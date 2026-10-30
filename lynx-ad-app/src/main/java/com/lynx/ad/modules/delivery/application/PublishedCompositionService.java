package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Staged edits reuse generation/import workers, but never create a second advertising plan. */
@Service
public class PublishedCompositionService {
    private final CreationStore store;
    private final AdPlanRepository plans;
    private final PlanTransferStore transfers;
    private final CreationQianchuanGateway gateway;
    private final AdManagementPort ads;
    private final CreationPayloadBuilder payloads;
    private final AdPlanCreativeBindingStore bindings;
    private final PlanRemoteStore remote;
    private final BillingTrialService billing;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public PublishedCompositionService(CreationStore store,AdPlanRepository plans,PlanTransferStore transfers,
            CreationQianchuanGateway gateway,AdManagementPort ads,CreationPayloadBuilder payloads,
            AdPlanCreativeBindingStore bindings,PlanRemoteStore remote,BillingTrialService billing,
            ObjectMapper json,PlatformTransactionManager manager) {
        this.store=store;this.plans=plans;this.transfers=transfers;this.gateway=gateway;this.ads=ads;
        this.payloads=payloads;this.bindings=bindings;this.remote=remote;this.billing=billing;this.json=json;tx=new TransactionTemplate(manager);
    }
    private AdPlan access(TenantId tenant,String id) {
        var p=plans.findById(tenant,new PlanId(id)).orElseThrow(()->new BizException(1403,"投放计划不存在"));
        if(p.qcPlanId().isEmpty()||!Set.of(PlanStatus.DELIVERING,PlanStatus.PAUSED).contains(p.status()))throw new BizException(1403,"当前计划状态不支持调整商品与素材");
        return p;
    }
    private AdPlan member(TenantId tenant,String id,CreationState s) {
        var p=access(tenant,id);
        if(!p.accountId().value().equals(s.accountId)||!p.awemeId().value().equals(s.awemeId))throw new BizException(1404,"原计划的账户绑定已变化");
        return p;
    }
    private JsonNode detail(Credential c,String id) {
        var full=ads.detail(c,id);
        if(!id.equals(full.path("ad_id").asText()))throw new BizException(1404,"千川返回的计划不匹配");
        return full;
    }
    private static void idle(CreationState s) {
        if(com.lynx.ad.modules.delivery.domain.service.CreationProductSelection.locked(s)
                ||Set.of("QUEUED","RUNNING").contains(s.analysisStatus))throw new BizException(1523,"素材任务或保存正在处理，请完成并核查后再调整商品");
    }
    public CreationState begin(TenantId tenant,String target) {
        billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
        var p=access(tenant,target);transfers.assertIdle(tenant,p.id());
        var group=transfers.group(tenant,p.id());String root=group==null?target:group.root();
        return tx.execute(status->{
            var result=new CreationState[1];
            store.mutate(tenant.value(),root,null,source->{
                if(source.publishedEditDraftId!=null){result[0]=store.get(tenant.value(),source.publishedEditDraftId);return;}
                idle(source);
                if(!"CREATED".equals(source.publishStatus))throw new BizException(1403,"当前计划缺少已确认的投放素材快照，请先同步计划");
                member(tenant,target,source);
                var work=json.convertValue(source,CreationState.class);work.planId=UUID.randomUUID().toString();work.revision=1;
                if(com.lynx.ad.common.security.TenantContext.tenantId().isPresent())work.userId=com.lynx.ad.common.security.TenantContext.userId().value();
                work.publishedEditDraftId=null;work.publishedEdit=new CreationState.PublishedEdit();var edit=work.publishedEdit;
                edit.sourcePlanId=root;edit.targetPlanId=target;edit.planStatus=p.status().name();edit.original=json.valueToTree(source);
                var c=gateway.credential(tenant,source.accountId,source.awemeId);
                var ids=group==null?List.of(target):new LinkedHashSet<>(Arrays.asList(group.conservative(),group.custom()));
                for(String id:ids)if(id!=null){var plan=member(tenant,id,source);var m=new CreationState.ContentMember();m.planId=id;m.qcPlanId=plan.qcPlanId().orElseThrow().value();m.before=QianchuanCreativeBindings.groups(detail(c,m.qcPlanId));
                    if(!productIds(m.before).equals(new HashSet<>(source.products.stream().map(CreationState.Product::qcProductId).toList())))throw new BizException(1404,"原计划商品与千川绑定不一致，请先同步核对");edit.members.add(m);}
                if(edit.members.isEmpty())throw new BizException(1403,"原计划关联缺失，请先同步核对");
                for(var m:work.materials)if(m.enginePlanId==null)m.enginePlanId=work.enginePlanId;
                work.name=p.name().text();work.smartBidType=p.setting().bidType().name();work.budget=p.setting().dailyBudget().amount();work.roi2Goal=p.setting().roi2Goal().map(RoiGoal::value).orElse(null);
                work.publishStatus="NONE";work.publishKey=null;work.publishError=null;work.publishRecovery=null;work.qcPlanId=null;work.materialEdit=null;
                work.step=1;work.reachedStep=4;work.selectionChange=null;work.confirmedVersion=null;
                source.publishedEditDraftId=work.planId;source.revision++;edit.sourceRevision=source.revision;
                store.insert(work);result[0]=work;
            });
            return result[0];
        });
    }
    public CreationState save(TenantId tenant,String workId,int revision) {
        billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
        var current=store.get(tenant.value(),workId);var edit=required(current);
        member(tenant,edit.targetPlanId,current);
        if("SUCCEEDED".equals(edit.status)||current.publishedEditPending())return current;
        if(!"DRAFT".equals(edit.status))throw new BizException(1404,"原计划内容已变化，请返回详情核对；本次生成的素材仍在素材中心保留");
        if(current.revision!=revision)throw new BizException(1404,"修改内容已更新，请刷新后核对");
        idle(current);
        var original=json.convertValue(edit.original,CreationState.class);
        var ids=current.products.stream().map(CreationState.Product::qcProductId).toList();
        if(ids.isEmpty()||ids.size()>10||new HashSet<>(ids).size()!=ids.size())throw new BizException(1302,"请选择 1–10 个不同商品");
        var retained=new HashSet<>(original.products.stream().map(CreationState.Product::qcProductId).toList());
        var added=ids.stream().filter(id->!retained.contains(id)).toList();
        // Catalog gray reasons include "already in a full-domain plan": creation eligibility
        // applies to new products, while retained bindings are checked against live ad detail below.
        if(!added.isEmpty())gateway.selected(tenant,current.accountId,current.awemeId,added);
        // Validate title reviews and platform asset synchronization before any write.
        var wanted=payloads.creatives(current);var old=payloads.creatives(original);
        var c=gateway.credential(tenant,current.accountId,current.awemeId);
        tx.executeWithoutResult(status->store.mutate(tenant.value(),edit.sourcePlanId,edit.sourceRevision,source->{
            if(!workId.equals(source.publishedEditDraftId))throw new BizException(1404,"原计划的修改版本已变化");
            idle(source);var g=transfers.lockForProductChange(member(tenant,edit.targetPlanId,current),workId);
            if(!g.root().equals(edit.sourcePlanId)||!new HashSet<>(Arrays.asList(g.conservative(),g.custom()).stream().filter(Objects::nonNull).toList()).equals(new HashSet<>(edit.members.stream().map(m->m.planId).toList())))throw new BizException(1404,"关联投放计划已变化，请重新核对");
            store.mutate(tenant.value(),workId,revision,s->{
                var e=required(s);e.operations.clear();
                for(var m:e.members){var p=member(tenant,m.planId,s);if(!m.qcPlanId.equals(p.qcPlanId().orElseThrow().value())||!PublishedMaterialService.same(m.before,QianchuanCreativeBindings.groups(detail(c,m.qcPlanId))))throw new BizException(1404,"千川商品或素材已变化，本次未提交；请返回详情核对");
                    var delta=merge(m.before,old,wanted);m.after=delta.finalGroups();prepareOperations(s,c,m,delta);}
                e.status="SAVING";e.message="正在将商品与素材修改保存到原投放计划";s.changed();
            });
        }));
        return process(store.get(tenant.value(),workId));
    }
    public CreationState verify(TenantId tenant,String workId){var s=store.get(tenant.value(),workId);required(s);member(tenant,s.publishedEdit.targetPlanId,s);return process(s);}
    private static CreationState.PublishedEdit required(CreationState s){if(s.publishedEdit==null)throw new BizException(1403,"当前记录不属于已投放计划的修改内容");return s.publishedEdit;}
    private void update(CreationState s,java.util.function.Consumer<CreationState> change){store.mutate(s.tenantId,s.planId,null,v->{if(v.publishedEditPending()){change.accept(v);v.changed();}});}
    public CreationState process(CreationState snapshot) {
        var s=store.get(snapshot.tenantId,snapshot.planId);if(!s.publishedEditPending()||s.publishedEdit.nextCheckAt>System.currentTimeMillis())return s;
        try {
            var tenant=new TenantId(s.tenantId);var e=required(s);var g=transfers.group(tenant,new PlanId(e.sourcePlanId));
            if(g==null||!s.planId.equals(g.requestKey()))throw new BizException(1404,"原计划保存锁已变化，请核查");
            var c=gateway.credential(tenant,s.accountId,s.awemeId);
            for(int i=0;i<e.operations.size();i++){
                final int n=i;var op=store.get(s.tenantId,s.planId).publishedEdit.operations.get(i);if("DONE".equals(op.phase))continue;
                var p=member(tenant,op.planId,s);if(!op.qcPlanId.equals(p.qcPlanId().orElseThrow().value()))throw new BizException(1404,"原计划平台ID已变化");
                var now=QianchuanCreativeBindings.groups(detail(c,op.qcPlanId));
                if(PublishedMaterialService.same(now,op.after)){update(s,v->v.publishedEdit.operations.get(n).phase="DONE");continue;}
                if(!"READY".equals(op.phase))throw new BizException(1406,"上次保存结果待千川确认，不会重复提交");
                if(!PublishedMaterialService.same(now,op.before))throw new BizException(1404,"千川内容已变化，已暂停后续修改，请核查");
                var claimed=new java.util.concurrent.atomic.AtomicBoolean();update(s,v->{var target=v.publishedEdit.operations.get(n);if("READY".equals(target.phase)){target.phase="SENDING";claimed.set(true);}});
                if(!claimed.get())return store.get(s.tenantId,s.planId);
                switch(op.action){
                    case "UPDATE"->ads.automaticContent(c,op.qcPlanId,"MATERIAL_ADD",json.createObjectNode().set("creatives",op.after));
                    case "DELETE_MATERIAL"->ads.deleteMaterials(c,op.qcPlanId,op.materialIds);
                    case "DELETE_PRODUCT"->ads.deleteProduct(c,op.qcPlanId,op.productId);
                    default->throw new IllegalStateException("Unknown content operation");
                }
                if(!PublishedMaterialService.same(QianchuanCreativeBindings.groups(detail(c,op.qcPlanId)),op.after))throw new BizException(1406,"千川尚未确认当前修改，不会重复提交");
                update(s,v->v.publishedEdit.operations.get(n).phase="DONE");
            }
            var confirmed=new LinkedHashMap<String,JsonNode>();
            for(var m:e.members){var full=detail(c,m.qcPlanId);if(!PublishedMaterialService.same(QianchuanCreativeBindings.groups(full),m.after))throw new BizException(1406,"原计划最终内容待核查");confirmed.put(m.planId,full);}
            finish(s,confirmed);
        }catch(RuntimeException error){update(s,v->{v.publishedEdit.status="UNCERTAIN";v.publishedEdit.nextCheckAt=System.currentTimeMillis()+30000;v.publishedEdit.message="保存结果待核查，暂不重复提交。"+(error instanceof BizException?error.getMessage():"平台暂未确认，请稍后核查原计划。");});}
        return store.get(s.tenantId,s.planId);
    }
    private void finish(CreationState s,Map<String,JsonNode> confirmed){
        var tenant=new TenantId(s.tenantId);var e=s.publishedEdit;
        tx.executeWithoutResult(status->store.mutate(s.tenantId,e.sourcePlanId,e.sourceRevision,source->{
            var work=store.get(s.tenantId,s.planId);if(!work.publishedEditPending()||work.publishedEdit.operations.stream().anyMatch(o->!"DONE".equals(o.phase)))return;
            source.products=work.products;source.materials=work.materials;source.titlePools=work.titlePools;source.removedMaterialIds=work.removedMaterialIds;source.hideInAweme=work.hideInAweme;
            source.enginePlanId=work.enginePlanId;source.engineJobId=work.engineJobId;source.generationGroupProducts=work.generationGroupProducts;source.generationSelection=work.generationSelection;source.generationPendingProductIds=work.generationPendingProductIds;
            source.generationStatus=work.generationStatus;source.generationCounts=work.generationCounts;source.generationIssues=work.generationIssues;source.dismissedGenerationIssues=new ArrayList<>(work.dismissedGenerationIssues);source.engineTerminalStatus=work.engineTerminalStatus;source.selectionChange=work.selectionChange;
            source.regeneration=work.regeneration;source.generationRetry=work.generationRetry;source.pendingAdditionId=work.pendingAdditionId;source.pendingRegenerationId=work.pendingRegenerationId;
            source.generationAdditions=work.generationAdditions;source.previousEngineJobId=work.previousEngineJobId;source.engineTerminalJobId=work.engineTerminalJobId;source.groups=work.groups;
            source.generationMessage=work.generationMessage;source.generationStage=work.generationStage;source.progress=work.progress;source.feedback=work.feedback;source.regenerationRetryAt=work.regenerationRetryAt;source.resetGenerationReconciliation();
            source.basicAnalysis=work.basicAnalysis;source.analysis=work.analysis;source.analysisStatus=work.analysisStatus;source.analysisConfirmation=work.analysisConfirmation;
            source.verifiedPlatformCreatives=work.publishedEdit.members.getFirst().after;source.publishedEditDraftId=null;source.recommendations=null;source.mediaChanged();
            for(var id:confirmed.keySet()){
                var p=plans.findByIdForUpdate(tenant,new PlanId(id)).orElseThrow();
                bindings.upsert(p.id(),work.products.stream().map(v->new PlanCreativeBinding(null,new QcProductId(v.qcProductId()),null,null,List.of())).toList());remote.verifiedContent(tenant,id,confirmed.get(id),work.products);
            }
            if(!confirmed.containsKey(e.sourcePlanId))bindings.upsert(new PlanId(e.sourcePlanId),work.products.stream().map(v->new PlanCreativeBinding(null,new QcProductId(v.qcProductId()),null,null,List.of())).toList());
            store.mutate(s.tenantId,s.planId,null,v->{v.publishedEdit.status="SUCCEEDED";v.publishedEdit.message="商品与素材修改已保存到原投放计划";v.revision++;});
            transfers.finishProductChange(tenant,e.sourcePlanId,s.planId);
        }));
    }
    public void discard(TenantId tenant,String workId){
        var s=store.get(tenant.value(),workId);var e=required(s);idle(s);
        if(!"DRAFT".equals(e.status))throw new BizException(1403,"保存结果待核查或已完成，不能丢弃本次修改");
        tx.executeWithoutResult(status->store.mutate(tenant.value(),e.sourcePlanId,null,source->{
            store.mutate(tenant.value(),workId,s.revision,actual->{idle(actual);if(!"DRAFT".equals(required(actual).status))throw new BizException(1404,"保存状态已变化，请核查");store.delete(tenant.value(),workId);});
            if(workId.equals(source.publishedEditDraftId)){source.publishedEditDraftId=null;source.revision++;}
        }));
    }
    record Delta(JsonNode additions,JsonNode finalGroups,Map<String,Set<String>> removals,List<String> removedProducts){}
    /** Keep unknown platform visuals and untouched product groups; change only confirmed local bindings. */
    static Delta merge(JsonNode before,JsonNode original,JsonNode wanted){
        var added=(ArrayNode)before.deepCopy();var removed=new LinkedHashMap<String,Set<String>>();var desiredIds=productIds(wanted);var oldIds=productIds(original);
        if(!productIds(before).equals(oldIds))throw new BizException(1404,"原计划商品绑定已变化");
        for(var next:wanted){String id=next.path("product_id").asText();if(!oldIds.contains(id))continue;
            var saved=find(original,id);var live=find(added,id);var oldKeys=PublishedMaterialService.objects(saved);var newKeys=PublishedMaterialService.objects(next);
            if(!PublishedMaterialService.objects(live).containsAll(oldKeys))throw new BizException(1404,"原素材的平台绑定已变化");
            var gone=new HashSet<>(oldKeys);gone.removeAll(newKeys);if(!gone.isEmpty())removed.put(id,gone);
            for(String field:List.of("video_material","image_material","carousel_material"))for(var visual:next.path(field)){
                var wrapper=new ObjectMapper().createObjectNode();wrapper.putArray(field).add(visual);var keys=PublishedMaterialService.objects(wrapper);
                if(Collections.disjoint(PublishedMaterialService.objects(live),keys))live.withArray(field).add(visual.deepCopy());
                else if(oldKeys.containsAll(keys)){
                    var list=(ArrayNode)live.withArray(field);for(int i=0;i<list.size();i++){var wrapped=new ObjectMapper().createObjectNode();wrapped.putArray(field).add(list.get(i));if(PublishedMaterialService.objects(wrapped).equals(keys)){list.set(i,visual.deepCopy());break;}}
                }
                else if(!PublishedMaterialService.objects(live).containsAll(keys))throw new BizException(1404,"素材图片组绑定重叠，请先核查原素材");
            }
            if(!PublishedMaterialService.same(saved.path("title_material"),next.path("title_material"))){
                var known=new HashSet<String>();saved.path("title_material").forEach(t->known.add(t.path("title").asText()));
                var titles=new ObjectMapper().createArrayNode();var text=new HashSet<String>();
                for(var t:next.path("title_material"))if(text.add(t.path("title").asText()))titles.add(t.deepCopy());
                for(var t:live.path("title_material"))if(!known.contains(t.path("title").asText())&&text.add(t.path("title").asText()))titles.add(t.deepCopy());
                if(titles.size()>30)throw new BizException(1503,"保留千川已有标题后超过30条，请先核对原计划标题");live.set("title_material",titles);
            }
            if(!gone.isEmpty())live.set("creative_card",next.path("creative_card").deepCopy());
            live.set("hide_in_aweme",next.path("hide_in_aweme").deepCopy());
        }
        var finalGroups=(JsonNode)added.deepCopy();for(var removal:removed.entrySet())finalGroups=PublishedMaterialService.without(finalGroups,removal.getKey(),removal.getValue());
        var removedProducts=new ArrayList<String>();for(String id:oldIds)if(!desiredIds.contains(id)){removedProducts.add(id);finalGroups=withoutProduct(finalGroups,id);}
        for(var next:wanted)if(!oldIds.contains(next.path("product_id").asText()))((ArrayNode)finalGroups).add(next.deepCopy());
        if(finalGroups.isEmpty()||finalGroups.size()>10)throw new BizException(1503,"每个投放计划须保留 1–10 款商品");
        return new Delta(added,finalGroups,removed,removedProducts);
    }
    private void prepareOperations(CreationState s,Credential c,CreationState.ContentMember m,Delta delta){
        JsonNode cursor=m.before;
        if(!PublishedMaterialService.same(cursor,delta.additions())){operation(s,m,"UPDATE",null,List.of(),cursor,delta.additions());cursor=delta.additions();}
        var pending=new ArrayList<>(delta.removedProducts());var oldIds=productIds(m.before);
        // Keep one live product throughout replacements, and respect the ten-product platform limit.
        for(var next:delta.finalGroups())if(!oldIds.contains(next.path("product_id").asText())){
            if(cursor.size()>=10){String id=pending.removeFirst();var after=withoutProduct(cursor,id);operation(s,m,"DELETE_PRODUCT",id,List.of(),cursor,after);cursor=after;}
            var after=(ArrayNode)cursor.deepCopy();after.add(next.deepCopy());operation(s,m,"UPDATE",null,List.of(),cursor,after);cursor=after;
        }
        for(var removal:delta.removals().entrySet()){
            var ids=materialIds(c,m.qcPlanId,removal.getKey(),removal.getValue(),cursor);var after=PublishedMaterialService.without(cursor,removal.getKey(),removal.getValue());
            operation(s,m,"DELETE_MATERIAL",removal.getKey(),ids,cursor,after);cursor=after;
        }
        for(String id:pending){var after=withoutProduct(cursor,id);operation(s,m,"DELETE_PRODUCT",id,List.of(),cursor,after);cursor=after;}
        if(!PublishedMaterialService.same(cursor,delta.finalGroups()))throw new IllegalStateException("Content operation chain mismatch");
    }
    private List<String> materialIds(Credential c,String qcId,String product,Set<String> keys,JsonNode groups){
        for(var group:groups)if(!product.equals(group.path("product_id").asText())&&!Collections.disjoint(PublishedMaterialService.objects(group),keys))throw new BizException(1404,"待移除素材同时绑定其他商品，请先核查");
        var matched=new LinkedHashMap<String,String>();var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for(String type:List.of("VIDEO","IMAGE","CAROUSEL"))if(keys.stream().anyMatch(k->k.startsWith(type+":")))for(var row:ads.materialReport(c,qcId,today,today,type)){
            if(row.hasNonNull("ad_id")&&!qcId.equals(row.path("ad_id").asText()))throw new BizException(1404,"平台素材计划归属不一致");if(row.path("is_delete").asBoolean())continue;
            var products=new HashSet<String>();row.path("product_info").forEach(p->products.add(p.path("product_id").asText()));row.path("product_id_list").forEach(p->products.add(p.asText()));if(!products.equals(Set.of(product)))continue;
            var visual=row.path("material_info").path(type.toLowerCase(Locale.ROOT)+"_material");String key=type+":"+visual.path(switch(type){case "VIDEO"->visual.hasNonNull("video_id")?"video_id":"aweme_item_id";case "IMAGE"->"image_id";default->visual.hasNonNull("carousel_id")?"carousel_id":"aweme_carousel_id";}).asText();
            if(keys.contains(key)){String id=visual.path("material_id").asText();LiveAdManagementAdapter.numericId(id);var prior=matched.putIfAbsent(key,id);if(prior!=null&&!prior.equals(id))throw new BizException(1404,"平台素材绑定重复");}
        }
        if(!matched.keySet().containsAll(keys))throw new BizException(1406,"千川尚未返回待移除素材的完整绑定，本次未提交，请稍后核查");return new ArrayList<>(new LinkedHashSet<>(matched.values()));
    }
    private static void operation(CreationState s,CreationState.ContentMember m,String action,String product,List<String> ids,JsonNode before,JsonNode after){var op=new CreationState.ContentOperation();op.planId=m.planId;op.qcPlanId=m.qcPlanId;op.action=action;op.productId=product;op.materialIds=ids;op.before=before.deepCopy();op.after=after.deepCopy();s.publishedEdit.operations.add(op);}
    private static ObjectNode find(JsonNode groups,String id){for(var g:groups)if(id.equals(g.path("product_id").asText()))return (ObjectNode)g;throw new BizException(1404,"商品绑定缺失，请刷新核对");}
    private static Set<String> productIds(JsonNode groups){var result=new LinkedHashSet<String>();groups.forEach(g->result.add(g.path("product_id").asText()));return result;}
    private static JsonNode withoutProduct(JsonNode groups,String id){var result=new ObjectMapper().createArrayNode();for(var g:groups)if(!id.equals(g.path("product_id").asText()))result.add(g.deepCopy());return result;}
}
