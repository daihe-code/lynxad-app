package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.application.OptimizationPolicy;
import com.lynx.ad.modules.delivery.application.PlanManagementService;
import com.lynx.ad.modules.delivery.application.CreationPayloadBuilder;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Existing content is read from the platform; additions require local generated-asset provenance. */
@Component
public class OptimizationTacticsGateway {
    public static final Set<String> KINDS=Set.of("BOOST","MATERIAL_FOLLOW","MATERIAL_ADD","MATERIAL_REMOVE","PRODUCT_REMOVE");
    // Already-dispatched legacy actions may only be reconciled through readback.
    public static final Set<String> READBACK_KINDS=Set.of("BOOST","MATERIAL_FOLLOW","MATERIAL_ADD","MATERIAL_REMOVE","PRODUCT_ADD","PRODUCT_REMOVE");
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final AdPlanRepository plans;private final AdManagementPort ads;private final QianchuanCredentialFacade credentials;
    private final OptimizationStore policies;private final OptimizationAutomationStore journal;private final PlanTransferStore transfers;
    private final CreationQianchuanGateway catalog;private final PlanManagementService management;private final PlanRemoteStore remote;
    private final ObjectMapper json;
    private record MerchantCache(Instant fetched,ObjectNode body){}
    private final Map<String,MerchantCache> merchantCache=new java.util.concurrent.ConcurrentHashMap<>();
    public enum DeliveryActivity { ZERO, ACTIVE, UNKNOWN }

    public DeliveryActivity productDeliveryActivity(AdPlan plan, LocalDate day, List<String> productIds) {
        if (productIds.isEmpty()) return DeliveryActivity.UNKNOWN;
        try {
            var missing = new HashSet<>(productIds);
            boolean active = false;
            for (var row : ads.products(credential(plan), plan.qcPlanId().orElseThrow().value(), day, day)) {
                if (row.path("is_del").asBoolean()) continue;
                var nestedId = row.path("product_info").path("product_id").asText(null);
                var id = row.path("product_id").asText(nestedId);
                if (nestedId != null && !nestedId.equals(id)) return DeliveryActivity.UNKNOWN;
                if (!productIds.contains(id)) continue;
                if (!missing.remove(id)) return DeliveryActivity.UNKNOWN;
                var stats = row.path("stats_info");
                var impressions = QianchuanPlanMapping.number(stats, "product_show_count_for_roi2");
                var spend = QianchuanPlanMapping.number(stats, "stat_cost_for_roi2");
                if (impressions == null || impressions.signum() < 0 || spend == null || spend.signum() < 0)
                    return DeliveryActivity.UNKNOWN;
                active |= impressions.signum() > 0 || spend.signum() > 0;
            }
            if (missing.isEmpty()) return active ? DeliveryActivity.ACTIVE : DeliveryActivity.ZERO;
        } catch (RuntimeException ignored) {
            // Supplemental product data can fail independently of spend protection.
        }
        return DeliveryActivity.UNKNOWN;
    }

    /** Slow object reports are independently cached; no write permission or generation is enabled by this read. */
    public ObjectNode merchantContext(AdPlan p,ZonedDateTime now){
        String key=p.tenantId().value()+":"+p.accountId().value()+":"+p.qcPlanId().orElseThrow().value()+":"+now.toLocalDate();
        var cached=merchantCache.get(key);if(cached!=null&&cached.fetched().plusSeconds(600).isAfter(now.toInstant()))return cached.body().deepCopy();
        var n=json.createObjectNode().put("complete",false).put("fetched_at",now.toOffsetDateTime().toString()).put("ledger_verified",false).put("budget_constrained",false);
        var issues=n.putArray("issues");
        try{
            var current=groups(detail(p));var products=n.putArray("products");var ids=new ArrayList<String>();current.forEach(g->ids.add(g.path("product_id").asText()));
            var selected=catalog.restorable(p.tenantId(),p.accountId().value(),p.awemeId().value(),ids);
            boolean supply=selected.size()==ids.size();for(var product:selected){boolean eligible=product.unavailableReasons().isEmpty()&&product.stock()!=null&&product.stock()>0;supply&=eligible;var v=products.addObject().put("id",product.qcProductId()).put("name",product.name()).put("eligible",eligible);if(product.stock()!=null)v.put("stock",product.stock());v.set("reasons",json.valueToTree(product.unavailableReasons()));}
            var defaults=OptimizationPolicy.defaults();var old=defaults.tactics();var readPolicy=new OptimizationPolicy(false,"OBSERVE",defaults.dailySpendLimit(),defaults.maxLoss(),defaults.minSpend(),defaults.minOrders(),3,60,List.of(),defaults.automation(),new OptimizationPolicy.Tactics(false,false,false,false,false,false,old.taskBudget(),old.taskHours(),old.extraBudgetPerDay(),4,1,1,List.of()));
            n.set("entities",entityRows(p,null,readPolicy,now,current));
            var tasks=n.putArray("tasks");boolean known=true;for(var task:ads.controls(credential(p),p.qcPlanId().orElseThrow().value())){
                tasks.add(task.deepCopy());String status=task.path("task_status").asText();
                if(!Set.of("FROZEN","OFFLINE_TIME","OFFLINE_BUDGET","DELETED").contains(status))known=false;
            }
            n.put("task_risk_known",known).put("supply_ready",supply).put("complete",supply&&known);
            if(!known)issues.add("独立任务尚有未花承诺风险，暂缓新增投入");if(!supply)issues.add("部分商品的库存或可投性未确认");
            // External operations and independent tasks are not fully covered by the local ledger.
            issues.add("当日外部新增投入仍待核实");
        }catch(RuntimeException e){issues.add("商品、计划内素材或独立任务数据暂不可用");}
        if(merchantCache.size()>256)merchantCache.clear();merchantCache.put(key,new MerchantCache(now.toInstant(),n));return n.deepCopy();
    }
    private CreationStore creations;
    private OptimizationCreativeStore creativeStore;
    private CreationPayloadBuilder payloads;
    private com.lynx.ad.modules.creative.application.MaterialCreativeContextService creativeContexts;
    @org.springframework.beans.factory.annotation.Autowired public void creativeContexts(com.lynx.ad.modules.creative.application.MaterialCreativeContextService value){creativeContexts=value;}
    @org.springframework.beans.factory.annotation.Autowired public void creations(CreationStore value){creations=value;}
    @org.springframework.beans.factory.annotation.Autowired public void creative(OptimizationCreativeStore store,CreationPayloadBuilder builder){creativeStore=store;payloads=builder;}
    public record CandidatePlan(String id,String name,String qcId){}
    public record Prepared(List<MemberWrite> writes,String objectKey,String productId,String taskName,String scene){}
    public record MemberWrite(AdPlan plan,JsonNode payload,JsonNode expectedMembership){}
    public OptimizationTacticsGateway(AdPlanRepository plans,AdManagementPort ads,QianchuanCredentialFacade credentials,OptimizationStore policies,OptimizationAutomationStore journal,PlanTransferStore transfers,CreationQianchuanGateway catalog,PlanManagementService management,PlanRemoteStore remote,ObjectMapper json){this.plans=plans;this.ads=ads;this.credentials=credentials;this.policies=policies;this.journal=journal;this.transfers=transfers;this.catalog=catalog;this.management=management;this.remote=remote;this.json=json;}
    private Credential credential(AdPlan p){return credentials.getUsableCredential(p.tenantId(),p.accountId());}
    private JsonNode detail(AdPlan p){var d=ads.detail(credential(p),p.qcPlanId().orElseThrow().value());if(!p.qcPlanId().orElseThrow().value().equals(d.path("ad_id").asText()))throw new BizException(1803,"内容计划ID不匹配");groups(d);return d;}
    private AdPlan candidate(AdPlan p,String id){return plans.findById(p.tenantId(),new PlanId(id)).filter(v->v.qcPlanId().isPresent() && v.status()!=PlanStatus.DELETED && v.accountId().equals(p.accountId()) && v.awemeId().equals(p.awemeId()) && !members(p).stream().anyMatch(m->m.id().equals(v.id()))).orElseThrow(()->new BizException(1802,"候选计划须属于当前商家、广告账户和抖音号，且不能是转投关联计划"));}
    public List<CandidatePlan> candidates(AdPlan p){return List.of();}
    public void validateCandidates(AdPlan p,OptimizationPolicy policy){if(policy.tactics().materialAdd())for(String id:policy.tactics().candidatePlanIds())candidate(p,id);if((policy.tactics().boost()||policy.tactics().materialFollow()) && p.setting().bidType()!=SmartBidType.CUSTOM)throw new BizException(1802,"一键起量和素材追投仅授权控成本计划");}
    private List<AdPlan> members(AdPlan p){var g=transfers.group(p.tenantId(),p.id());if(g==null)return List.of(p);var ids=java.util.stream.Stream.of(g.root(),g.conservative(),g.custom()).filter(Objects::nonNull).distinct().toList();return ids.stream().map(id->plans.findById(p.tenantId(),new PlanId(id)).orElseThrow(()->new BizException(1803,"转投关联计划缺失"))).filter(v->v.qcPlanId().isPresent()&&v.status()!=PlanStatus.DELETED).sorted(Comparator.comparing(v->v.id().value())).toList();}
    public JsonNode bindingFacts(AdPlan p){var policy=policies.get(p.tenantId(),p.id()).map(OptimizationStore.State::policy).orElse(OptimizationPolicy.defaults());if(!policy.tactics().active())return null;var result=json.createObjectNode();for(var member:members(p))result.set(member.id().value(),groups(detail(member)));return result;}
    public JsonNode merchantBindings(AdPlan p){return groups(detail(p));}
    static ArrayNode groups(JsonNode detail){return QianchuanCreativeBindings.groups(detail);}
    private static ObjectNode select(JsonNode n,String... keys){var result=new ObjectMapper().createObjectNode();for(String key:keys)if(n.hasNonNull(key))result.set(key,n.get(key).deepCopy());return result;}
    private static JsonNode group(JsonNode groups,String product){for(var g:groups)if(product.equals(g.path("product_id").asText()))return g;throw new BizException(1803,"商品不在平台当前绑定中");}
    static Set<String> objects(JsonNode g){var result=new HashSet<String>();for(var v:g.path("video_material"))result.add("VIDEO:"+v.path(v.hasNonNull("video_id")?"video_id":"aweme_item_id").asText());for(var v:g.path("image_material"))for(var id:v.path("image_ids"))result.add("IMAGE:"+id.asText());for(var v:g.path("carousel_material"))result.add("CAROUSEL:"+v.path(v.hasNonNull("carousel_id")?"carousel_id":"aweme_carousel_id").asText());return result;}
    private List<LocalDate> dates(OptimizationPolicy policy,ZonedDateTime observed){var end=observed.minusMinutes(policy.conversionDelayMinutes()).toLocalDate();var days=new ArrayList<LocalDate>();for(int i=policy.tactics().entityDays();i>0;i--)days.add(end.minusDays(i));return days;}
    public ObjectNode context(AdPlan p,OptimizationPolicy policy,ZonedDateTime observed){
        var result=json.createObjectNode();var entities=result.putArray("entities");var candidates=result.putArray("candidates");var counts=result.putObject("material_counts");var issues=result.putArray("issues");result.put("active_tasks",true);result.put("extra_budget_today",journal.extraBudgetToday(p.tenantId().value(),p.id().value()).toPlainString());
        try{var d=detail(p);var current=groups(d);for(var g:current)counts.put(g.path("product_id").asText(),objects(g).size());entities.addAll(entityRows(p,null,policy,observed,current));
            if(policy.tactics().boost()||policy.tactics().materialFollow())result.put("active_tasks",hasActiveTasks(p));
        }catch(RuntimeException ex){issues.add("当前素材、商品或调控任务数据不完整，本轮不进行相关动作");}
        if(policy.creativeGeneration().enabled())try{result.set("creative_context",generationContext(p));}catch(RuntimeException ex){result.putObject("creative_context").put("unavailable",true);issues.add("商品参考图、库存或生成状态暂不可用，本轮不生成");}
        if(creativeStore!=null)for(var job:creativeStore.sharedHistory(p.tenantId().value(),logicalRoot(p)))for(var row:entities)if("MATERIAL".equals(row.path("kind").asText())&&!job.body().path("object_key").asText().isBlank()&&job.productId().equals(row.path("product_id").asText())&&job.body().path("object_key").asText().equals(row.path("object_key").asText())){var features=(ObjectNode)row.path("creative_features");features.put("creative_angle",job.body().path("strategy").path("hypothesis").asText()).put("visual_style",job.body().path("strategy").path("visual_style").asText()).put("strategy_id",job.body().path("strategy").path("strategy_id").asText()).put("trial_status",job.status());}
        if(creativeContexts!=null){
            if(creations!=null)try{var state=creations.get(p.tenantId().value(),logicalRoot(p));for(var material:state.materials)creativeContexts.record(state,material);}catch(RuntimeException ex){issues.add("部分素材场景档案待补充，未知场景不作为判断依据");}
            for(var row:entities)creativeContexts.recordPlatform(p,row);
            var creative=result.path("creative_context").isObject()?(ObjectNode)result.path("creative_context"):result.putObject("creative_context");
            creativeContexts.attach(p.tenantId().value(),p.accountId().value(),entities,creative);
        }
        // Object references stay in the App; engine receives only scoped candidate/entity IDs.
        for(var list:List.of(entities,candidates))for(var row:list)((ObjectNode)row).remove("object_key");return result;
    }
    private ArrayNode entityRows(AdPlan p,String source,OptimizationPolicy policy,ZonedDateTime observed,JsonNode current){
        var rows=new LinkedHashMap<String,ObjectNode>();var profiles=new HashMap<String,OptimizationPolicy.Economics>();policies.economics(p).forEach(e->profiles.put(e.productId(),e));var c=credential(p);var days=dates(policy,observed);String qc=p.qcPlanId().orElseThrow().value();
        Set<String> follow=new HashSet<>();if(source==null && policy.tactics().materialFollow())management.materials(p.tenantId(),p.id()).stream().filter(PlanManagementService.Material::eligible).forEach(m->follow.add(m.id()));
        for(var date:days){
            var seenProducts=new HashSet<String>();for(var row:ads.monitoringProducts(c,qc,date,date)){String id=row.path("product_id").asText(row.path("product_info").path("product_id").asText());if(!seenProducts.add(id))throw new BizException(1803,"商品报表重复");JsonNode g;try{g=group(current,id);}catch(BizException ex){continue;}boolean eligible="PASS".equals(row.path("audit_status").asText());var node=rows.computeIfAbsent("P:"+id,k->entity("PRODUCT",id,id,null,source,profiles.get(id)));node.put("eligible",node.path("eligible").asBoolean(true)&&eligible);node.withArray("days").add(day(date,row.path("stats_info")));}
            for(String type:List.of("VIDEO","IMAGE","CAROUSEL")){var seen=new HashSet<String>();for(var row:ads.materialReport(c,qc,date,date,type)){
                if(row.hasNonNull("ad_id") && !qc.equals(row.path("ad_id").asText()))throw new BizException(1803,"素材报表计划不匹配");var sub=row.path("material_info").path(type.toLowerCase(Locale.ROOT)+"_material");String id=sub.path("material_id").asText();LiveAdManagementAdapter.numericId(id);if(!seen.add(id))throw new BizException(1803,"素材报表重复");var productIds=new HashSet<String>();for(var info:row.path("product_info"))productIds.add(info.path("product_id").asText());for(var info:row.path("product_id_list"))productIds.add(info.asText());if(productIds.size()!=1)continue;String product=productIds.iterator().next();JsonNode g;try{g=group(current,product);}catch(BizException ex){continue;}String object=objectKey(c,type,sub);if(object==null||!objects(g).contains(object))continue;
                boolean eligible="PASS".equals(row.path("audit_status").asText())&&!row.path("is_delete").asBoolean();var node=rows.computeIfAbsent(type+":"+id,k->entity("MATERIAL",id,product,type,source,profiles.get(product)));node.put("eligible",node.path("eligible").asBoolean(true)&&eligible);node.put("follow_eligible",follow.contains(id));node.put("object_key",object);node.putObject("creative_features").put("title",sub.path("title").asText(row.path("material_info").path("title").asText("")));node.withArray("days").add(day(date,row.path("stats_info")));
            }}
        }var result=json.createArrayNode();rows.values().forEach(result::add);return result;
    }
    private String objectKey(Credential c,String type,JsonNode sub){String key=switch(type){case "VIDEO"->sub.path(sub.hasNonNull("video_id")?"video_id":"aweme_item_id").asText();case "IMAGE"->sub.path("image_id").asText();default->sub.path("carousel_id").asText();};if(key.isBlank()&&"CAROUSEL".equals(type)){for(var meta:ads.materialMetadata(c,type,List.of(sub.path("material_id").asText())))if(sub.path("material_id").asText().equals(meta.path("material_id").asText()))key=meta.path("carousel_id").asText(meta.path("id").asText());}return key.isBlank()?null:type+":"+key;}
    private ObjectNode entity(String kind,String id,String product,String type,String source,OptimizationPolicy.Economics profile){var n=json.createObjectNode();n.put("kind",kind).put("id",id).put("product_id",product).put("eligible",true).put("follow_eligible",false);if(type!=null)n.put("material_type",type);if(source!=null)n.put("source_plan_id",source);if(profile!=null)n.set("economics",json.valueToTree(Map.of("product_id",profile.productId(),"contribution_margin_rate",profile.contributionMarginRate().toPlainString(),"refund_reserve_rate",profile.refundReserveRate().toPlainString(),"verified",profile.verified())));n.putArray("days");return n;}
    private ObjectNode day(LocalDate date,JsonNode raw){var n=json.createObjectNode().put("date",date.toString());var m=n.putObject("metrics");for(var pair:List.of(new String[]{"spend","stat_cost_for_roi2"},new String[]{"orders","total_pay_order_count_for_roi2"},new String[]{"net_revenue","total_order_settle_amount_for_roi2_1h"},new String[]{"impressions","product_show_count_for_roi2"},new String[]{"clicks","product_click_count_for_roi2"})){var v=QianchuanPlanMapping.number(raw,pair[1]);if(v==null)m.putNull(pair[0]);else m.put(pair[0],v.toPlainString());}return n;}
    private boolean hasActiveTasks(AdPlan p){for(var row:ads.controls(credential(p),p.qcPlanId().orElseThrow().value())){String status=row.path("task_status").asText();if(!Set.of("FROZEN","OFFLINE_TIME","OFFLINE_BUDGET","DELETED").contains(status))return true;}return false;}
    public String logicalRoot(AdPlan p){var g=transfers.group(p.tenantId(),p.id());return g==null?p.id().value():g.root();}
    public boolean creativeBusy(AdPlan p){
        if(creations==null)return false;
        try{var s=creations.get(p.tenantId().value(),logicalRoot(p));return Set.of("QUEUED","RUNNING").contains(s.analysisStatus)||Set.of("QUEUED","SUBMITTING","RUNNING","IMPORTING","REVISING","UNCERTAIN").contains(s.generationStatus)||Set.of("QUEUED","SENDING","UNCERTAIN").contains(s.publishStatus);}
        catch(BizException e){if(e.code()==1403)return false;throw e;}
    }
    private ObjectNode generationContext(AdPlan p){
        var result=creativeStore==null?json.createObjectNode():(ObjectNode)creativeStore.context(p.tenantId().value(),logicalRoot(p));
        result.put("busy",creativeBusy(p));var products=result.putObject("products");var ids=new ArrayList<String>();groups(detail(p)).forEach(g->ids.add(g.path("product_id").asText()));
        for(var product:catalog.selected(p.tenantId(),p.accountId().value(),p.awemeId().value(),ids))products.putObject(product.qcProductId()).put("stock_ready",product.stock()!=null&&product.stock()>0).put("reference_ready",product.imageUrl()!=null&&!product.imageUrl().isBlank());
        return result;
    }
    public CreationState generationState(AdPlan p,OptimizationPolicy policy,String product){
        group(groups(detail(p)),product);if(creativeBusy(p))throw new BizException(1803,"共享素材正在修改，暂不生成或发布");
        var selected=catalog.selected(p.tenantId(),p.accountId().value(),p.awemeId().value(),List.of(product));
        if(selected.size()!=1||selected.getFirst().stock()==null||selected.getFirst().stock()<=0||selected.getFirst().imageUrl()==null||selected.getFirst().imageUrl().isBlank())throw new BizException(1803,"商品库存或真实参考图未确认");
        var s=new CreationState();s.planId=p.id().value();s.tenantId=p.tenantId().value();s.userId=policy.creativeGeneration().authorizedBy();s.accountId=p.accountId().value();s.awemeId=p.awemeId().value();s.name=p.name().text();s.products=new ArrayList<>(selected);s.smartBidType=p.setting().bidType().name();return s;
    }
    private Prepared prepareGenerated(AdPlan p,OptimizationPolicy policy,JsonNode action){
        if(creativeStore==null||payloads==null||!policy.creativeGeneration().enabled()||!"NEW_MATERIAL_TRIAL".equals(action.path("cause").asText()))throw new BizException(1802,"仅支持已授权生成素材的独立试投");
        var state=policies.get(p.tenantId(),p.id()).orElseThrow();var job=creativeStore.publication(p.tenantId().value(),action.path("generation_id").asText(),p.id().value(),state.version());
        if(!policy.creativeGeneration().autoPublish()&&!job.body().path("approved").asBoolean())throw new BizException(1802,"用户尚未确认发布生成素材");
        if(!job.productId().equals(action.path("product_id").asText()))throw new BizException(1802,"生成商品归属不匹配");
        var fresh=generationState(p,policy,job.productId());CreationState.Material material;
        try{material=json.treeToValue(job.body().path("material"),CreationState.Material.class);}catch(Exception ex){throw new BizException(1802,"生成素材来源无效");}
        if(!job.body().path("engine_job_id").asText().equals(material.engineJobId)||!job.productId().equals(material.productId)||!Set.of("VIDEO","IMAGE","CAROUSEL").contains(material.type))throw new BizException(1802,"生成素材来源不匹配");
        if(creativeContexts!=null&&!job.body().path("approved").asBoolean()){
            creativeContexts.hydrate(fresh,material);
            if(!"READY".equals(material.creativeProfileStatus)||!com.lynx.ad.modules.agentops.application.OptimizationCreativeService.directionEvidenceReady(material.creativeProfile,job.body().path("strategy"))||com.lynx.ad.modules.agentops.application.OptimizationCreativeService.directionConflict(material.creativeProfile,job.body().path("strategy")))throw new BizException(1803,"生成素材场景未确认或重复失败方向，暂停自动发布");
        }
        var single=payloads.optimizationMaterial(fresh,material);var object=objects(single).stream().findFirst().orElseThrow(()->new BizException(1802,"生成素材对象缺失"));
        var writes=new ArrayList<MemberWrite>();for(var member:members(p)){var current=groups(detail(member));mergeGenerated((ObjectNode)group(current,job.productId()),single);var payload=json.createObjectNode().set("creatives",current);writes.add(new MemberWrite(member,payload,membership(current)));}
        return new Prepared(List.copyOf(writes),object,job.productId(),null,null);
    }
    static void mergeGenerated(ObjectNode current,JsonNode single){
        if(!current.path("product_id").asText().equals(single.path("product_id").asText())||objects(single).size()!=1||!Collections.disjoint(objects(current),objects(single))||objects(current).size()>=100)throw new BizException(1803,"商品、素材绑定变化或数量达到上限");
        for(String key:List.of("video_material","image_material","carousel_material"))for(var item:single.path(key))current.withArray(key).add(item.deepCopy());
        var titles=current.withArray("title_material");boolean card=false;Set<String> copy=new HashSet<>();for(var t:titles){copy.add(t.path("title").asText());card|="COMMODITY_CARD".equals(t.path("title_type").asText());}
        for(var t:single.path("title_material")){if(!copy.add(t.path("title").asText()))continue;var added=(ObjectNode)t.deepCopy();if(card&&"COMMODITY_CARD".equals(added.path("title_type").asText()))added.put("title_type","CUSTOM");titles.add(added);}
        if(titles.size()>30)throw new BizException(1803,"商品标题已达到30条上限");
    }
    /** Zero or missing traffic is never fabricated from another plan's metrics. Skip publication day's partial attribution. */
    public ObjectNode trial(AdPlan p,OptimizationCreativeStore.Job job,OptimizationPolicy policy){
        var n=json.createObjectNode().put("observed_at",Instant.now().toString());String object=job.body().path("object_key").asText();String type=job.body().path("material").path("type").asText();
        var current=group(groups(detail(p)),job.productId());n.put("bound",objects(current).contains(object));n.put("retained",objects(current).size()<=policy.tactics().retainMaterials());
        var c=credential(p);String qc=p.qcPlanId().orElseThrow().value();LocalDate today=LocalDate.now(ZONE);
        for(var row:ads.materialReport(c,qc,today,today,type)){var sub=row.path("material_info").path(type.toLowerCase(Locale.ROOT)+"_material");if(object.equals(objectKey(c,type,sub))){n.put("audit",row.path("audit_status").asText());n.put("entity_id",sub.path("material_id").asText());break;}}
        var days=n.putArray("days");if(job.publishedAt()==null)return n;
        LocalDate first=job.publishedAt().atZone(ZONE).toLocalDate().plusDays(1),end=ZonedDateTime.now(ZONE).minusMinutes(policy.conversionDelayMinutes()).toLocalDate().minusDays(1);
        for(var date=first;!date.isAfter(end)&&days.size()<14;date=date.plusDays(1)){
            JsonNode found=null;for(var row:ads.materialReport(c,qc,date,date,type)){var sub=row.path("material_info").path(type.toLowerCase(Locale.ROOT)+"_material");if(object.equals(objectKey(c,type,sub))){if(found!=null)throw new BizException(1803,"试投报表重复");found=row;}}
            if(found==null){n.put("complete",false);return n;}days.add(day(date,found.path("stats_info")));
        }
        n.put("complete",!days.isEmpty());return n;
    }
    private Prepared prepareTrialRemoval(AdPlan p,OptimizationPolicy policy,JsonNode action){
        if(creativeStore==null||!policy.tactics().materialRemove())throw new BizException(1802,"未授权自动删除素材");
        var job=creativeStore.get(p.tenantId().value(),action.path("generation_id").asText()).orElseThrow(()->new BizException(1802,"试投记录不存在"));
        var state=policies.get(p.tenantId(),p.id()).orElseThrow();if(!p.id().value().equals(job.planId())||state.version()!=job.version()||!"REMOVING".equals(job.status()))throw new BizException(1803,"试投归属或授权已变化");
        if(!job.productId().equals(action.path("product_id").asText())||!job.body().path("material").path("type").asText().equals(action.path("material_type").asText()))throw new BizException(1802,"试投素材或商品范围不匹配");
        var evaluation=trial(p,job,policy);var profile=policies.economics(p).stream().filter(e->e.productId().equals(job.productId())&&e.verified()).findFirst().orElseThrow();
        var summary=com.lynx.ad.modules.agentops.application.OptimizationCreativeService.evaluate(evaluation,profile,job.reservedCost());
        if(summary==null||summary.spend().subtract(summary.revenue().multiply(profile.contributionMarginRate()).multiply(BigDecimal.ONE.subtract(profile.refundReserveRate()))).compareTo(policy.creativeGeneration().trialLossLimit())<0)throw new BizException(1803,"最新成熟试投亏损未达到上限");
        String object=job.body().path("object_key").asText();var writes=new ArrayList<MemberWrite>();for(var member:members(p)){var current=groups(detail(member));var g=group(current,job.productId());if(!objects(g).contains(object)||approvedObjects(member,g).stream().filter(k->!object.equals(k)).count()<policy.tactics().retainMaterials())throw new BizException(1803,"必须保留最后的已审核可投素材");var payload=json.createObjectNode().put("materialId",materialId(member,action.path("material_type").asText(),job.productId(),object));var expected=membership(current);var kept=json.createArrayNode();for(var key:expected.path(job.productId()))if(!object.equals(key.asText()))kept.add(key);expected.set(job.productId(),kept);writes.add(new MemberWrite(member,payload,expected));}
        return new Prepared(List.copyOf(writes),object,job.productId(),null,null);
    }
    public Prepared prepare(AdPlan p,OptimizationPolicy policy,JsonNode action,String eventId){
        if("MATERIAL_ADD".equals(action.path("kind").asText()))return prepareGenerated(p,policy,action);
        if("TRIAL_LOSS".equals(action.path("cause").asText()))return prepareTrialRemoval(p,policy,action);
        validateCandidates(p,policy);String kind=action.path("kind").asText();var ctx=context(p,policy,ZonedDateTime.now(ZONE));validateAction(policy,action,ctx,ZonedDateTime.now(ZONE));
        if(creations!=null&&policy.tactics().content()){var g=transfers.group(p.tenantId(),p.id());try{var state=creations.get(p.tenantId().value(),g==null?p.id().value():g.root());if(Set.of("QUEUED","RUNNING").contains(state.analysisStatus)||Set.of("QUEUED","SUBMITTING","RUNNING","IMPORTING","REVISING","UNCERTAIN").contains(state.generationStatus)||Set.of("QUEUED","SENDING","UNCERTAIN").contains(state.publishStatus))throw new BizException(1803,"共享商品素材正在分析、生成或发布，暂不自动增删");}catch(BizException ex){if(ex.code()!=1403)throw ex;}}
        String product=action.path("product_id").asText(),object=null;String scene="BOOST".equals(kind)?"SMART_BOOST":"MATERIAL_ADD_BUDGET";String taskName="灵投自动_"+eventId.replace("-","");
        if(Set.of("BOOST","MATERIAL_FOLLOW").contains(kind)){var ids=new ArrayList<String>();for(var g:groups(detail(p)))if(!"MATERIAL_FOLLOW".equals(kind)||product.equals(g.path("product_id").asText()))ids.add(g.path("product_id").asText());for(var item:catalog.selected(p.tenantId(),p.accountId().value(),p.awemeId().value(),ids))if(item.stock()==null||item.stock()<=0)throw new BizException(1803,"投放商品库存不明确或不足，暂不追加预算");return new Prepared(List.of(),null,product,taskName,scene);}
        var source=kind.endsWith("ADD")?candidate(p,action.path("source_plan_id").asText()):p;var sourceGroups=groups(detail(source));var sourceGroup=group(sourceGroups,product);JsonNode single=null;
        if(kind.startsWith("MATERIAL")){object=findObject(source,action,sourceGroup);single=singleCreative(sourceGroup,object);}
        if(kind.endsWith("ADD")){var selected=catalog.selected(p.tenantId(),p.accountId().value(),p.awemeId().value(),List.of(product));if(selected.getFirst().stock()==null || selected.getFirst().stock()<=0)throw new BizException(1803,"候选商品库存不明确或不足，暂不新增");}
        var writes=new ArrayList<MemberWrite>();for(var member:members(p)){
            var d=detail(member);var current=groups(d);var payload=json.createObjectNode();
            if("PRODUCT_REMOVE".equals(kind)){group(current,product);if(current.size()<=policy.tactics().retainProducts())throw new BizException(1803,"关联计划已达到保留商品数量");payload.put("productId",product);}
            else if("MATERIAL_REMOVE".equals(kind)){var g=group(current,product);String removed=object;if(!objects(g).contains(object)||approvedObjects(member,g).stream().filter(k->!removed.equals(k)).count()<policy.tactics().retainMaterials())throw new BizException(1803,"关联计划已审核可投素材数量不足，暂不删除");payload.put("materialId",materialId(member,action.path("material_type").asText(),product,object));}
            else {var g=(ObjectNode)group(current,product);if(objects(g).contains(object))throw new BizException(1803,"素材已存在于关联计划");if(objects(g).size()>=100)throw new BizException(1803,"已达到自动素材容量上限");for(String field:List.of("video_material","image_material","carousel_material"))for(var item:single.path(field))g.withArray(field).add(item.deepCopy());payload.set("creatives",current);}
            var expected=membership(current);if("PRODUCT_REMOVE".equals(kind))expected.remove(product);else if("MATERIAL_REMOVE".equals(kind)){var kept=json.createArrayNode();for(var key:expected.path(product))if(!object.equals(key.asText()))kept.add(key);expected.set(product,kept);}writes.add(new MemberWrite(member,payload,expected));
        }return new Prepared(List.copyOf(writes),object,product,taskName,scene);
    }
    private String findObject(AdPlan source,JsonNode action,JsonNode g){String type=action.path("material_type").asText();var today=LocalDate.now(ZONE);for(var row:ads.materialReport(credential(source),source.qcPlanId().orElseThrow().value(),today,today,type)){var sub=row.path("material_info").path(type.toLowerCase(Locale.ROOT)+"_material");if(action.path("entity_id").asText().equals(sub.path("material_id").asText()) && "PASS".equals(row.path("audit_status").asText())&&!row.path("is_delete").asBoolean()){var key=objectKey(credential(source),type,sub);if(key!=null&&objects(g).contains(key))return key;}}throw new BizException(1803,"素材已改变、审核不明确或不再绑定");}
    private String materialId(AdPlan member,String type,String product,String object){var today=LocalDate.now(ZONE);for(var row:ads.materialReport(credential(member),member.qcPlanId().orElseThrow().value(),today,today,type)){var sub=row.path("material_info").path(type.toLowerCase(Locale.ROOT)+"_material");if(object.equals(objectKey(credential(member),type,sub)))return sub.path("material_id").asText();}throw new BizException(1803,"关联计划素材ID未确认");}
    private Set<String> approvedObjects(AdPlan p,JsonNode group){var known=objects(group);var result=new HashSet<String>();var today=LocalDate.now(ZONE);var c=credential(p);for(String type:List.of("VIDEO","IMAGE","CAROUSEL"))for(var row:ads.materialReport(c,p.qcPlanId().orElseThrow().value(),today,today,type))if("PASS".equals(row.path("audit_status").asText())&&!row.path("is_delete").asBoolean()){var key=objectKey(c,type,row.path("material_info").path(type.toLowerCase(Locale.ROOT)+"_material"));if(known.contains(key))result.add(key);}return result;}
    static ObjectNode singleCreative(JsonNode g,String object){var result=select(g,"product_id");for(var v:g.path("video_material"))if(objects(new ObjectMapper().createObjectNode().set("video_material",new ObjectMapper().createArrayNode().add(v))).contains(object))result.putArray("video_material").add(v);for(var v:g.path("image_material"))for(var id:v.path("image_ids"))if(object.equals("IMAGE:"+id.asText()))result.putArray("image_material").add(select(v,"image_mode").set("image_ids",new ObjectMapper().createArrayNode().add(id)));for(var v:g.path("carousel_material"))if(object.equals("CAROUSEL:"+v.path(v.hasNonNull("carousel_id")?"carousel_id":"aweme_carousel_id").asText()))result.putArray("carousel_material").add(v);return result;}
    public void write(AdPlan p,JsonNode action,Prepared prepared){String kind=action.path("kind").asText();if(!KINDS.contains(kind))throw new BizException(1802,"不支持该自动动作");if(Set.of("BOOST","MATERIAL_FOLLOW").contains(kind))ads.automaticControl(credential(p),p.qcPlanId().orElseThrow().value(),prepared.scene(),prepared.taskName(),new BigDecimal(action.path("budget").asText()),new BigDecimal(action.path("duration").asText()),"MATERIAL_FOLLOW".equals(kind)?List.of(action.path("entity_id").asText()):List.of());else for(var write:prepared.writes())ads.automaticContent(credential(write.plan()),write.plan().qcPlanId().orElseThrow().value(),kind,write.payload());}
    private ObjectNode membership(JsonNode groups){var n=json.createObjectNode();for(var g:groups){var keys=n.putArray(g.path("product_id").asText());objects(g).stream().sorted().forEach(keys::add);}return n;}
    public ObjectNode intent(Prepared p){var n=json.createObjectNode();if(p.objectKey()!=null)n.put("objectKey",p.objectKey());n.put("productId",p.productId()).put("taskName",p.taskName()).put("scene",p.scene());var members=n.putArray("members");var expected=n.putObject("expectedBindings");p.writes().forEach(w->{members.add(w.plan().id().value());expected.set(w.plan().id().value(),w.expectedMembership());});return n;}
    public boolean matches(AdPlan p,JsonNode action,JsonNode intent){if(intent==null)return false;String kind=action.path("kind").asText();if(Set.of("BOOST","MATERIAL_FOLLOW").contains(kind)){
        var tasks=ads.controls(credential(p),p.qcPlanId().orElseThrow().value(),intent.path("scene").asText()).stream().filter(task->intent.path("taskName").asText().equals(task.path("name").asText())).toList();
        if(tasks.size()!=1)return false;var task=tasks.getFirst();
        if(!task.hasNonNull("id") || !Set.of("PROCESSING","DISABLE","OFFLINE_TIME","OFFLINE_BUDGET","OFFLINE_BALANCE","ROI2_DISABLE","LIVE_ROOM_OFF").contains(task.path("task_status").asText()))return false;
        try{LiveAdManagementAdapter.numericId(task.path("id").asText());if(new BigDecimal(task.path("budget").asText("-1")).compareTo(new BigDecimal(action.path("budget").asText()))!=0 || new BigDecimal(task.path("duration").asText("-1")).compareTo(new BigDecimal(action.path("duration").asText()))!=0)return false;}catch(RuntimeException ex){return false;}
        if("MATERIAL_FOLLOW".equals(kind)){var materials=task.path("material_list");if(!materials.isArray() || materials.size()!=1 || !action.path("entity_id").asText().equals(materials.get(0).path("material_id").asText()))return false;}
        return true;
    }
        var actual=members(p);var expected=new HashSet<String>();intent.path("members").forEach(n->expected.add(n.asText()));if(!expected.equals(actual.stream().map(v->v.id().value()).collect(java.util.stream.Collectors.toSet())))return false;
        for(var member:actual){var groups=groups(detail(member));if(!membership(groups).equals(intent.path("expectedBindings").path(member.id().value())))return false;JsonNode g=null;for(var row:groups)if(intent.path("productId").asText().equals(row.path("product_id").asText()))g=row;if(kind.startsWith("PRODUCT")){if(kind.endsWith("ADD")!=(g!=null))return false;}else if(g==null||kind.endsWith("ADD")!=objects(g).contains(intent.path("objectKey").asText()))return false;}return true;
    }
    public void sync(AdPlan p){
        var latest=detail(p);var bindings=groups(latest);var ids=new ArrayList<String>();bindings.forEach(g->ids.add(g.path("product_id").asText()));
        var snapshots=catalog.products(p.tenantId(),p.accountId().value(),p.awemeId().value(),1,100,null,"ALL",false,ids).items();
        if(!new HashSet<>(ids).equals(snapshots.stream().map(CreationState.Product::qcProductId).collect(java.util.stream.Collectors.toSet())))throw new BizException(1803,"平台商品显示信息尚未完整，继续只读核查");
        for(var member:members(p))remote.verifiedContent(p.tenantId(),member.id().value(),detail(member),snapshots);
        if(creations!=null){var group=transfers.group(p.tenantId(),p.id());String root=group==null?p.id().value():group.root();try{creations.get(p.tenantId().value(),root);}catch(BizException ex){if(ex.code()==1403)return;throw ex;}
            creations.mutate(p.tenantId().value(),root,null,state->{state.verifiedPlatformCreatives=bindings;state.verifiedPlatformChannels=latest.path("product_channel_info").isArray()?latest.path("product_channel_info").deepCopy():null;state.products=new ArrayList<>(snapshots);state.materials.removeIf(v->!state.products.stream().anyMatch(product->product.productId().equals(v.productId)||product.qcProductId().equals(v.productId)));state.recommendations=null;state.mediaChanged();});
        }
    }
    public void confirmedRemoval(AdPlan p,JsonNode intent){
        if(creativeContexts==null||intent.path("objectKey").asText().isBlank())return;
        String product=intent.path("productId").asText(),key=intent.path("objectKey").asText();var scopes=new HashSet<String>();scopes.add(logicalRoot(p));
        for(var member:intent.path("members")){scopes.add(member.asText());for(var bound:intent.path("expectedBindings").path(member.asText()).path(product))if(key.equals(bound.asText()))return;}
        creativeContexts.confirmedRemoval(p.tenantId().value(),p.accountId().value(),scopes,product,key);
    }
    public static void validateAction(OptimizationPolicy policy,JsonNode action,JsonNode context,ZonedDateTime now){
        var t=policy.tactics();String kind=action.path("kind").asText();boolean allowed=switch(kind){case "BOOST"->t.boost();case "MATERIAL_FOLLOW"->t.materialFollow();case "MATERIAL_ADD"->t.materialAdd();case "MATERIAL_REMOVE"->t.materialRemove();case "PRODUCT_REMOVE"->t.productRemove();default->false;};if(!allowed)throw new BizException(1802,"未授权该自动动作");
        if(Set.of("BOOST","MATERIAL_FOLLOW").contains(kind)){var b=new BigDecimal(action.path("budget").asText());var h=new BigDecimal(action.path("duration").asText());if(b.compareTo(t.taskBudget())!=0 || h.compareTo(t.taskHours())!=0 || context.path("active_tasks").asBoolean(true)||!context.hasNonNull("extra_budget_today")||new BigDecimal(context.path("extra_budget_today").asText()).add(b).compareTo(t.extraBudgetPerDay())>0)throw new BizException(1803,"追加任务预算超限、已有调控任务或任务授权已变化");if("BOOST".equals(kind))return;}
        boolean candidate=kind.endsWith("ADD");JsonNode entity=null;for(var row:context.path(candidate?"candidates":"entities"))if(action.path("entity_id").asText().equals(row.path("id").asText())&&action.path("product_id").asText().equals(row.path("product_id").asText()) && (kind.startsWith("PRODUCT")?"PRODUCT":"MATERIAL").equals(row.path("kind").asText()) && (!kind.startsWith("MATERIAL")||action.path("material_type").equals(row.path("material_type"))) && (!candidate||action.path("source_plan_id").equals(row.path("source_plan_id")))){entity=row;break;}
        if(entity==null || !entity.path("eligible").asBoolean() || !entity.path("economics").path("verified").asBoolean() || !entity.path("product_id").equals(entity.path("economics").path("product_id")) || candidate && !t.candidatePlanIds().contains(action.path("source_plan_id").asText()) || "MATERIAL_FOLLOW".equals(kind)&&!entity.path("follow_eligible").asBoolean())throw new BizException(1803,"素材或商品候选资格、归属与利润资料未确认");
        var factor=new BigDecimal(entity.path("economics").path("contribution_margin_rate").asText()).multiply(BigDecimal.ONE.subtract(new BigDecimal(entity.path("economics").path("refund_reserve_rate").asText())));var days=entity.path("days");if(days.size()!=t.entityDays())throw new BizException(1803,"素材或商品成熟日数据不足");var end=now.minusMinutes(policy.conversionDelayMinutes()).toLocalDate();BigDecimal spend=BigDecimal.ZERO,orders=BigDecimal.ZERO,profit=BigDecimal.ZERO;boolean allPositive=true,allNegative=true;for(int i=0;i<days.size();i++){var day=days.get(i);if(!end.minusDays(t.entityDays()-i).toString().equals(day.path("date").asText()))throw new BizException(1803,"素材或商品报表已过期");var m=day.path("metrics");for(String field:List.of("spend","orders","net_revenue"))if(!m.hasNonNull(field))throw new BizException(1803,"素材或商品净成交数据缺失");var cost=new BigDecimal(m.path("spend").asText());var value=new BigDecimal(m.path("net_revenue").asText()).multiply(factor).subtract(cost);spend=spend.add(cost);orders=orders.add(new BigDecimal(m.path("orders").asText()));profit=profit.add(value);allPositive&=value.signum()>0;allNegative&=value.signum()<0;}
        if(spend.compareTo(policy.minSpend())<0 || (kind.endsWith("REMOVE")?(!allNegative||profit.compareTo(policy.maxLoss().negate())>0):(!allPositive||orders.compareTo(BigDecimal.valueOf(policy.minOrders()))<0||profit.compareTo(spend.multiply(new BigDecimal(".2")))<0)))throw new BizException(1803,"最新单素材或商品利润与样本不支持该自动动作");
        if("MATERIAL_REMOVE".equals(kind)&&context.path("material_counts").path(action.path("product_id").asText()).asInt()<=t.retainMaterials())throw new BizException(1803,"不得移除商品最后的素材");
    }
}
