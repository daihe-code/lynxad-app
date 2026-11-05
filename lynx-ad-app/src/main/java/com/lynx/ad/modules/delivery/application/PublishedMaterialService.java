package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Published material changes retain the old visual until every physical plan confirms the change. */
@Service
public class PublishedMaterialService {
    private final CreationStore store;
    private final AdPlanRepository plans;
    private final PlanTransferStore transfers;
    private final CreationQianchuanGateway gateway;
    private final AdManagementPort ads;
    private final CreationPayloadBuilder payloads;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private PlanRemoteStore remote;
    @org.springframework.beans.factory.annotation.Autowired public void remoteMetadata(PlanRemoteStore value){remote=value;}
    public PublishedMaterialService(CreationStore store,AdPlanRepository plans,PlanTransferStore transfers,
            CreationQianchuanGateway gateway,AdManagementPort ads,CreationPayloadBuilder payloads,ObjectMapper json,PlatformTransactionManager manager) {
        this.store=store;this.plans=plans;this.transfers=transfers;this.gateway=gateway;this.ads=ads;this.payloads=payloads;this.json=json;tx=new TransactionTemplate(manager);
    }
    public static boolean pending(CreationState s) {return s.materialEditPending();}
    public void requireEditor(TenantId tenant,String requested,CreationState s) {
        var p=member(tenant,requested,s);
        if(!Set.of(PlanStatus.DELIVERING,PlanStatus.PAUSED).contains(p.status()))throw new BizException(1403,"当前计划状态不支持修改素材");
        if(!"CREATED".equals(s.publishStatus)||pending(s))throw new BizException(1523,"素材修改正在处理，请先核查结果");
        if(Set.of("QUEUED","SUBMITTING","REVISING","RUNNING","IMPORTING","UNCERTAIN").contains(s.generationStatus))throw new BizException(1523,"素材任务正在处理，请等待完成");
    }
    private AdPlan member(TenantId tenant,String id,CreationState s) {
        var p=plans.findById(tenant,new PlanId(id)).orElseThrow(()->new BizException(1403,"计划不存在"));
        if(p.qcPlanId().isEmpty()||p.status()==PlanStatus.DELETED||!p.accountId().value().equals(s.accountId)||!p.awemeId().value().equals(s.awemeId))throw new BizException(1403,"素材与计划的授权绑定不一致");
        return p;
    }
    /** Called under CreationStore's transaction. The group claim also serializes transfers and product removal. */
    public void reserve(TenantId tenant,String requested,CreationState s,CreationState.Material source,String action) {
        requireEditor(tenant,requested,s);
        var p=member(tenant,requested,s);var edit=new CreationState.MaterialEdit();
        edit.key=UUID.randomUUID().toString();edit.action=action;edit.sourceId=source.id;edit.status="regenerate".equals(action)?"GENERATING":"SYNCING";
        edit.message="regenerate".equals(action)?"正在重新生成，原投放素材保持可用":"正在同步删除所选素材";
        var group=transfers.lockForProductChange(p,edit.key);
        if(!group.root().equals(s.planId))throw new BizException(1404,"共享素材归属已变化，请刷新");
        var ids=new LinkedHashSet<String>();if(group.conservative()!=null)ids.add(group.conservative());if(group.custom()!=null)ids.add(group.custom());
        var c=gateway.credential(tenant,s.accountId,s.awemeId);var old=payloads.optimizationMaterial(s,source);var keys=objects(old);
        if(keys.isEmpty())throw new BizException(1503,"无法确认原素材的平台绑定");
        for(String id:ids) {
            var plan=member(tenant,id,s);var m=new CreationState.MaterialEditMember();m.planId=id;m.qcPlanId=plan.qcPlanId().orElseThrow().value();
            m.before=detail(c,m.qcPlanId);var product=product(m.before,source.productId);
            if(!objects(product).containsAll(keys))throw new BizException(1404,"原素材的平台绑定已变化，请刷新后核对");
            for(var other:m.before)if(!source.productId.equals(other.path("product_id").asText())&&!Collections.disjoint(objects(other),keys))throw new BizException(1403,"该素材同时绑定多个商品，请先在千川核查绑定范围");
            if("delete".equals(action)&&objects(product).size()<=keys.size())throw new BizException(1503,"请至少保留该商品的一条投放素材");
            var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));var matched=new LinkedHashMap<String,String>();
            for(var row:ads.materialReport(c,m.qcPlanId,today,today,source.type)) {
                if(row.hasNonNull("ad_id")&&!m.qcPlanId.equals(row.path("ad_id").asText()))throw new BizException(1404,"平台素材的计划归属不一致");
                if(row.path("is_delete").asBoolean())continue;
                var products=new HashSet<String>();for(var info:row.path("product_info"))products.add(info.path("product_id").asText());for(var productId:row.path("product_id_list"))products.add(productId.asText());
                if(!products.equals(Set.of(source.productId)))continue;
                var v=row.path("material_info").path(source.type.toLowerCase(Locale.ROOT)+"_material");
                String key=object(source.type,v),material=v.path("material_id").asText();
                if(keys.contains(key)) {LiveAdManagementAdapter.numericId(material);String existing=matched.putIfAbsent(key,material);if(existing!=null&&!existing.equals(material))throw new BizException(1404,"平台素材绑定重复，请核查");}
            }
            if(!matched.keySet().containsAll(keys))throw new BizException(1406,"平台尚未返回原素材绑定，请刷新素材后再操作");
            m.removeIds.addAll(new LinkedHashSet<>(matched.values()));edit.members.add(m);
        }
        if(edit.members.isEmpty())throw new BizException(1403,"当前没有可修改的投放计划");
        s.materialEdit=edit;
    }
    private JsonNode detail(com.lynx.ad.modules.identity.domain.model.Credential c,String id) {
        var d=ads.detail(c,id);if(!id.equals(d.path("ad_id").asText()))throw new BizException(1404,"平台返回的计划不匹配");
        return QianchuanCreativeBindings.groups(d);
    }
    private static ObjectNode product(JsonNode groups,String id) {
        for(var g:groups)if(id.equals(g.path("product_id").asText()))return (ObjectNode)g;
        throw new BizException(1404,"投放商品绑定已变化，请刷新后核对");
    }
    static Set<String> objects(JsonNode g) {
        var keys=new HashSet<String>();for(var v:g.path("video_material"))keys.add(object("VIDEO",v));
        for(var v:g.path("image_material"))for(var id:v.path("image_ids"))keys.add("IMAGE:"+id.asText());
        for(var v:g.path("carousel_material"))keys.add(object("CAROUSEL",v));return keys;
    }
    private static String object(String type,JsonNode v) {
        return type+":"+switch(type){case "VIDEO"->v.path(v.hasNonNull("video_id")?"video_id":"aweme_item_id").asText();case "IMAGE"->v.path("image_id").asText();default->v.path(v.hasNonNull("carousel_id")?"carousel_id":"aweme_carousel_id").asText();};
    }
    static JsonNode without(JsonNode groups,String product,Set<String> keys) {
        JsonNode copy=groups.deepCopy();var g=product(copy,product);
        for(String field:List.of("video_material","carousel_material"))if(g.has(field)) {
            var kept=new ObjectMapper().createArrayNode();for(var v:g.path(field))if(!keys.contains(object(field.startsWith("video")?"VIDEO":"CAROUSEL",v)))kept.add(v);g.set(field,kept);
        }
        if(g.has("image_material")){var kept=new ObjectMapper().createArrayNode();for(var v:g.path("image_material")){var item=(ObjectNode)v.deepCopy();var images=item.putArray("image_ids");for(var id:v.path("image_ids"))if(!keys.contains("IMAGE:"+id.asText()))images.add(id);if(!images.isEmpty())kept.add(item);}g.set("image_material",kept);}
        return copy;
    }
    static JsonNode with(JsonNode groups,JsonNode single) {
        JsonNode copy=groups.deepCopy();var g=product(copy,single.path("product_id").asText());
        if(!Collections.disjoint(objects(g),objects(single)))throw new BizException(1404,"新素材已存在，需核查平台状态");
        for(String field:List.of("video_material","image_material","carousel_material"))for(var v:single.path(field))g.withArray(field).add(v.deepCopy());
        return copy;
    }
    private void update(CreationState s,java.util.function.Consumer<CreationState> change) {
        store.mutate(s.tenantId,s.planId,null,current->{if(pending(current)&&current.materialEdit.key.equals(s.materialEdit.key)){change.accept(current);current.changed();}});
    }
    private boolean claim(CreationState s,int member,String expected,String next) {
        var claimed=new java.util.concurrent.atomic.AtomicBoolean();
        update(s,current->{var m=current.materialEdit.members.get(member);if(expected.equals(m.phase)){m.phase=next;claimed.set(true);}});return claimed.get();
    }
    public static boolean same(JsonNode a,JsonNode b){return normalized(a).equals(normalized(b));}
    private static JsonNode normalized(JsonNode n) {
        if(n.isIntegralNumber())return com.fasterxml.jackson.databind.node.BigIntegerNode.valueOf(n.bigIntegerValue());
        if(n.isNumber())return com.fasterxml.jackson.databind.node.DecimalNode.valueOf(n.decimalValue().stripTrailingZeros());
        if(n.isArray()){var rows=new ArrayList<JsonNode>();n.forEach(v->rows.add(normalized(v)));rows.sort(Comparator.comparing(JsonNode::toString));var result=new ObjectMapper().createArrayNode();rows.forEach(result::add);return result;}
        if(n.isObject()){var result=new ObjectMapper().createObjectNode();n.fields().forEachRemaining(e->{if(!e.getValue().isArray()||!e.getValue().isEmpty())result.set(e.getKey(),normalized(e.getValue()));});return result;}
        return n;
    }
    public CreationState process(CreationState input) {
        var s=store.get(input.tenantId,input.planId);if(!pending(s))return s;
        if(s.materialEdit.nextCheckAt>System.currentTimeMillis())return s;
        try {
            var edit=s.materialEdit;var source=s.materials.stream().filter(m->edit.sourceId.equals(m.id)).findFirst().orElseThrow(()->new BizException(1404,"原素材记录缺失，请核查"));
            if("GENERATING".equals(edit.status)) {
                if(!Set.of("SUCCEEDED","FAILED").contains(s.generationStatus))return s;
                var replacement=s.materials.stream().filter(m->(edit.sourceId.equals(m.replacesId)||edit.sourceId.equals(m.reviewSourceId))&&"READY".equals(m.status)).findFirst();
                if(replacement.isEmpty()) {finish(s,false,"重新生成未完成，原投放素材已保留");return store.get(s.tenantId,s.planId);}
                if(replacement.get().pendingVersion()) {
                    tx.executeWithoutResult(status->update(s,current->{current.materialEdit.status="SUCCEEDED";current.materialEdit.message="新版本已保存，确认处理方式前原投放素材保持不变";transfers.finishProductChange(new TenantId(s.tenantId),s.planId,current.materialEdit.key);}));
                    return store.get(s.tenantId,s.planId);
                }
                var single=payloads.optimizationMaterial(s,replacement.get());
                update(s,current->{for(var m:current.materialEdit.members)m.added=with(m.before,single);current.materialEdit.status="SYNCING";current.materialEdit.message="新素材已就绪，正在替换计划中的原素材";});
                return store.get(s.tenantId,s.planId);
            }
            var tenant=new TenantId(s.tenantId);var c=gateway.credential(tenant,s.accountId,s.awemeId);
            var group=transfers.group(tenant,new PlanId(s.planId));
            if(group==null||!edit.key.equals(group.requestKey()))throw new BizException(1404,"素材操作锁已变化，请核查");
            var oldKeys=objects(payloads.optimizationMaterial(s,source));
            for(int index=0;index<edit.members.size();index++) {
                final int n=index;var m=edit.members.get(n);if("DONE".equals(m.phase))continue;
                var p=member(tenant,m.planId,s);if(!m.qcPlanId.equals(p.qcPlanId().orElseThrow().value()))throw new BizException(1404,"平台计划绑定已变化");
                var current=detail(c,m.qcPlanId);boolean addition=m.added!=null&&!m.added.isNull();var beforeRemoval=addition?m.added:m.before;var expected="keep".equals(edit.action)?beforeRemoval:without(beforeRemoval,source.productId,oldKeys);
                if(same(current,expected)){update(s,state->state.materialEdit.members.get(n).phase="DONE");continue;}
                if("READY".equals(m.phase)&&addition) {
                    if(!same(current,m.before))throw new BizException(1404,"平台素材已被修改，请核查后处理");
                    if(!claim(s,n,"READY","ADDING"))return store.get(s.tenantId,s.planId);
                    ads.automaticContent(c,m.qcPlanId,"MATERIAL_ADD",json.createObjectNode().set("creatives",m.added));
                    current=detail(c,m.qcPlanId);
                    if(!same(current,m.added))throw new BizException(1406,"新素材投放绑定待确认，原素材已保留");
                    update(s,state->state.materialEdit.members.get(n).phase="ADDED");m.phase="ADDED";
                } else if("ADDING".equals(m.phase)) {
                    if(!same(current,m.added))throw new BizException(1406,"新素材提交结果待确认，不会重复提交");
                    update(s,state->state.materialEdit.members.get(n).phase="ADDED");m.phase="ADDED";
                }
                if("keep".equals(edit.action)&&same(current,expected)){update(s,state->state.materialEdit.members.get(n).phase="DONE");continue;}
                if("REMOVING".equals(m.phase))throw new BizException(1406,"删除结果待平台确认，不会重复提交");
                if(!same(current,beforeRemoval))throw new BizException(1404,"平台素材绑定已变化，请核查后处理");
                if(!claim(s,n,addition?"ADDED":"READY","REMOVING"))return store.get(s.tenantId,s.planId);
                // One persisted claim per member: a timeout is reconciled by reads, never blind replay.
                ads.deleteMaterials(c,m.qcPlanId,m.removeIds);
                if(!same(detail(c,m.qcPlanId),expected))throw new BizException(1406,"素材删除结果待平台确认，不会重复提交");
                update(s,state->state.materialEdit.members.get(n).phase="DONE");
            }
            var confirmed=new LinkedHashMap<String,JsonNode>();
            for(var m:edit.members) {
                var full=ads.detail(c,m.qcPlanId);
                var expected=m.added!=null&&!m.added.isNull()?m.added:m.before;if(!"keep".equals(edit.action))expected=without(expected,source.productId,oldKeys);
                if(!m.qcPlanId.equals(full.path("ad_id").asText())||!same(QianchuanCreativeBindings.groups(full),expected))throw new BizException(1406,"平台素材最终结果待核查");
                confirmed.put(m.planId,full);
            }
            finish(s,true,"素材修改已同步千川",confirmed);
        }catch(RuntimeException unavailable) {
            var current=store.get(s.tenantId,s.planId);
            if(unavailable instanceof BizException error&&error.code()==1404&&current.materialEdit.members.stream().allMatch(m->"READY".equals(m.phase)))finish(current,false,"平台素材绑定已变化，本次未修改投放素材，请刷新后重试");
            else update(s,state->{state.materialEdit.nextCheckAt=System.currentTimeMillis()+30000;state.materialEdit.message="素材修改结果待核查，已有素材保留；正在读取平台结果，请勿重复操作";});
        }
        return store.get(s.tenantId,s.planId);
    }
    private void finish(CreationState s,boolean success,String message) {
        finish(s,success,message,Map.of());
    }
    private void finish(CreationState s,boolean success,String message,Map<String,JsonNode> confirmed) {
        tx.executeWithoutResult(status->update(s,current->{
            if(success&&current.materialEdit.members.stream().anyMatch(m->!"DONE".equals(m.phase)))return;
            if(!success&&current.materialEdit.members.stream().anyMatch(m->!"READY".equals(m.phase)))return;
            if(success){
                if(!"keep".equals(current.materialEdit.action)){current.materials.removeIf(m->current.materialEdit.sourceId.equals(m.id));current.removedMaterialIds.add(current.materialEdit.sourceId);}
                if(current.materialEdit.versionId!=null)current.materials.stream().filter(m->current.materialEdit.versionId.equals(m.id)).forEach(m->m.versionDecision="keep".equals(current.materialEdit.action)?"KEEP_BOTH":"REPLACE");
                if(!confirmed.isEmpty()) {
                    var latest=confirmed.values().iterator().next();
                    current.verifiedPlatformCreatives=QianchuanCreativeBindings.groups(latest);
                    current.verifiedPlatformChannels=latest.path("product_channel_info").isArray()?latest.path("product_channel_info").deepCopy():null;
                }
                current.mediaChanged();
            } else {
                current.materials.stream().filter(m->Objects.equals(current.materialEdit.versionId,m.id)).forEach(m->m.versionDecision="PENDING");
                current.materials.removeIf(m->{if(current.materialEdit.sourceId.equals(m.replacesId)){current.removedMaterialIds.add(m.id);return true;}return false;});
            }
            current.materialEdit.status=success?"SUCCEEDED":"FAILED";current.materialEdit.message=message;
            if(success&&remote!=null)confirmed.forEach((id,detail)->remote.verifiedContent(new TenantId(s.tenantId),id,detail,current.products));
            transfers.finishProductChange(new TenantId(s.tenantId),s.planId,s.materialEdit.key);
        }));
    }
}
