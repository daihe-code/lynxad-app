package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationAutomationStore.Event;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationCreativeStore.Job;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Generation is a recoverable saga. Advertising writes always use the existing write/readback journal. */
@Service
public class OptimizationCreativeService {
    private final OptimizationStore policies;
    private final OptimizationAutomationStore journal;
    private final OptimizationCreativeStore jobs;
    private final OptimizationExecutionGateway gateway;
    private final MonitoringEngineClient monitoring;
    private final CreationEngineClient engine;
    private final CreationWorkflowService workflow;
    private final ObjectMapper json;
    private com.lynx.ad.modules.creative.application.MaterialCreativeContextService creativeContexts;
    @org.springframework.beans.factory.annotation.Autowired public void creativeContexts(com.lynx.ad.modules.creative.application.MaterialCreativeContextService value){creativeContexts=value;}
    public OptimizationCreativeService(OptimizationStore policies,OptimizationAutomationStore journal,OptimizationCreativeStore jobs,
            OptimizationExecutionGateway gateway,MonitoringEngineClient monitoring,CreationEngineClient engine,CreationWorkflowService workflow,ObjectMapper json){
        this.policies=policies;this.journal=journal;this.jobs=jobs;this.gateway=gateway;this.monitoring=monitoring;this.engine=engine;this.workflow=workflow;this.json=json;
    }
    @Scheduled(fixedDelayString="${lynx.ad.monitoring.creative-scan-ms:15000}")
    public void scan(){
        for(var event:journal.pending())if("READY".equals(event.status())&&"MATERIAL_GENERATE".equals(event.kind()))reserve(event);
        for(var job:jobs.due()){
            String owner="creative:"+UUID.randomUUID();if(!jobs.claim(job,owner))continue;
            try{advance(jobs.get(job.tenant(),job.id()).orElseThrow());}
            catch(RuntimeException ex){var latest=jobs.get(job.tenant(),job.id()).orElse(job);var body=(ObjectNode)latest.body().deepCopy();body.put("error",ex instanceof BizException?ex.getMessage():"生成或素材数据暂不可用，等待回查");jobs.update(latest,latest.status(),body);}
            finally{jobs.release(job,owner);}
        }
    }
    private void reserve(Event event){
        String owner="generation:"+UUID.randomUUID();if(!journal.claimAccount(event.tenant(),event.accountId(),owner))return;
        try{
            if(!"READY".equals(journal.event(event.tenant(),event.id()).orElseThrow().status()))return;
            var p=gateway.plan(event.tenant(),event.planId());gateway.writable(p);var state=active(p,event.version());var facts=gateway.facts(p);
            if(journal.unresolved(event.tenant(),event.accountId())||state.baseline()==null||!state.baseline().equals(facts))throw new BizException(1803,"平台已变化或有操作待核查，暂不生成");
            var trialAction=json.createObjectNode().put("kind","MATERIAL_ADD").put("cause","NEW_MATERIAL_TRIAL").put("generation_id",event.id()).put("product_id",event.proposal().path("product_id").asText());
            var trialEvent=new Event(event.id(),event.tenant(),event.planId(),event.accountId(),event.planName(),event.version(),"MATERIAL_ADD",event.title(),event.evidence(),trialAction,event.report(),event.status(),null,null,event.createdAt(),null,null);
            OptimizationExecutionService.validate(state,trialEvent,facts,Instant.now(),journal.actionsToday(event.tenant(),event.planId()));
            gateway.assertPeerStopped(p);var context=gateway.tactics().generationState(p,state.policy(),trialAction.path("product_id").asText());
            var strategy=event.proposal().path("strategy");if(strategy.path("count").asInt()!=1||!context.products.getFirst().qcProductId().equals(strategy.path("product_id").asText())||!state.policy().creativeGeneration().formats().contains(strategy.path("target").asText()))throw new BizException(1802,"生成策略超出授权");
            var product=context.products.getFirst();var productBody=json.createObjectNode().put("id",product.qcProductId()).put("name",product.name()).put("category",product.category());productBody.putArray("image_urls").add(product.imageUrl());
            // A range is a fact, never an invented exact selling price.
            if(product.priceInfo()!=null)productBody.set("price_info",json.valueToTree(product.priceInfo()));
            if(product.price()!=null&&(product.priceInfo()==null||product.priceInfo().minPrice().compareTo(product.priceInfo().maxPrice())==0))productBody.put("price",product.price());
            var request=json.createObjectNode().put("request_id",event.id()).put("store_id",event.tenant()).put("account_id",context.accountId).put("aweme_id",context.awemeId).put("plan_id",facts.path("qcId").asText());request.set("product",productBody);request.set("strategy",strategy.deepCopy());
            var body=json.createObjectNode();body.set("request",request);body.set("strategy",strategy.deepCopy());body.set("context",json.valueToTree(context));
            jobs.reserve(event,gateway.tactics().logicalRoot(p),state.policy(),body,owner);
        }catch(RuntimeException ex){journal.finish(event,"SKIPPED",ex instanceof BizException?ex.getMessage():"生成条件检查失败",null);}
        finally{journal.releaseAccount(event.tenant(),event.accountId(),owner);}
    }
    private OptimizationStore.State active(AdPlan p,long version){
        var s=policies.get(p.tenantId(),p.id()).orElseThrow(()->new BizException(1802,"生成授权不存在"));
        if(s.version()!=version||!s.enabled()||s.holdReason()!=null||!"AUTO_LIMITED".equals(s.policy().mode())||!s.policy().creativeGeneration().enabled()||s.policy().creativeGeneration().authorizedBy()==null)throw new BizException(1803,"生成授权已撤销或版本变化");return s;
    }
    public List<Job> history(String tenant,String plan){var p=gateway.plan(tenant,plan);return jobs.sharedHistory(tenant,gateway.tactics().logicalRoot(p));}
    public void approve(String tenant,String plan,String id,boolean approved){
        var job=jobs.get(tenant,id).orElseThrow(()->new BizException(1405,"生成记录不存在"));var current=gateway.plan(tenant,plan);if(!job.logicalPlanId().equals(gateway.tactics().logicalRoot(current)))throw new BizException(1405,"生成记录不属于该计划");
        if(approved)active(gateway.plan(tenant,job.planId()),job.version());jobs.approve(tenant,id,approved);
    }
    private void advance(Job job){
        var body=(ObjectNode)job.body().deepCopy();body.remove("error");
        // First reconcile an already submitted publication, even if a user has since revoked authorization.
        if(Set.of("PUBLISHING","REMOVING").contains(job.status())){reconcile(job,body);return;}
        AdPlan p;OptimizationStore.State state;
        try{p=gateway.plan(job.tenant(),job.planId());state=active(p,job.version());}
        catch(BizException ex){body.put("error",ex.getMessage());jobs.update(job,"CANCELLED",body);return;}
        if(Set.of("PLATFORM_REVIEW","TRIAL").contains(job.status())){evaluateTrial(p,state,job,body);return;}
        // Persisted request lookup precedes every possible retry. Unknown receipts never cause another generation.
        if(Set.of("QUEUED","GENERATING","UNKNOWN").contains(job.status())){
            var receipt=monitoring.creative(job.tenant(),"/"+CreationEngineClient.query(job.id()),null);
            if(receipt==null){
                gateway.tactics().generationState(p,state.policy(),job.productId());
                // Engine's durable request ID is reused with exactly the same saved payload, including after a restart.
                jobs.update(job,"GENERATING",body);job=jobs.get(job.tenant(),job.id()).orElseThrow();
                try{receipt=monitoring.creative(job.tenant(),"",body.path("request"));}catch(BizException ex){body.put("error",ex.getMessage());jobs.update(job,ex.code()==8002?"FAILED":"UNKNOWN",body);return;}
            }
            if(receipt==null||!job.id().equals(receipt.path("request_id").asText()))throw new BizException(8002,"引擎生成回执不匹配");
            String engineId=receipt.path("job_id").asText("");if(engineId.isBlank()){
                if(job.createdAt().plusSeconds(1800).isBefore(Instant.now())){body.put("error","生成上下文未完成，需核查原请求，禁止重复生成");jobs.update(job,"UNKNOWN",body);}return;
            }
            body.put("engine_job_id",engineId);var result=engine.get("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(engineId)+"?store_id="+CreationEngineClient.query(job.tenant()));
            if(!engineId.equals(result.path("job_id").asText()))throw new BizException(8002,"生成任务响应归属不匹配");
            for(var direction:result.path("direction_planning"))if("group-1".equals(direction.path("group_id").asText())&&job.productId().equals(direction.path("product_id").asText())&&body.path("strategy").path("target").asText().equals(direction.path("target").asText()))body.set("directionPlan",direction);
            if(!Set.of("succeeded","partial_failed","failed","cancelled").contains(result.path("status").asText())){if("UNCERTAIN".equals(receipt.path("status").asText())||job.createdAt().plusSeconds(7200).isBefore(Instant.now())){body.put("error","原生成任务尚未完成或投递不明确，请核查引擎任务 "+engineId+"；不会创建重复任务，可停止本批后续发布");jobs.update(job,"UNKNOWN",body);}else jobs.update(job,"GENERATING",body);return;}
            var material=parse(result,job.productId(),body.path("strategy").path("target").asText(),engineId);
            if(material==null){String reason=body.path("directionPlan").path("failure_reason").asText();body.put("error",reason.isBlank()?"未生成符合当前策略的完整素材，请查看引擎任务":"素材方向规划未通过，任务已停止："+reason);jobs.update(job,"FAILED",body);return;}
            body.set("material",json.valueToTree(material));jobs.update(job,state.policy().creativeGeneration().autoPublish()?"SYNCING":"REVIEW",body);
            journal.notice(job.tenant(),job.planId(),"OPTIMIZATION_EXECUTION","自动生成素材已完成",state.policy().creativeGeneration().autoPublish()?"正在同步所选账户，等待发布检查":"请到自动盯盘页面审核素材并确认发布");return;
        }
        if("REVIEW".equals(job.status()))return;
        if("SYNCING".equals(job.status())){
            if(!state.policy().creativeGeneration().autoPublish()&&!body.path("approved").asBoolean())throw new BizException(1802,"生成素材尚未确认发布");
            var context=gateway.tactics().generationState(p,state.policy(),job.productId());
            CreationState.Material material=material(body);workflow.importOptimizationMaterial(context,material);body.set("material",json.valueToTree(material));
            if("FAILED".equals(material.status)){body.put("error",material.error);jobs.update(job,"FAILED",body);return;}
            if(!"READY".equals(material.status)){jobs.update(job,"SYNCING",body);return;}
            if(creativeContexts!=null&&!body.path("approved").asBoolean()){
                creativeContexts.hydrate(context,material);body.set("material",json.valueToTree(material));
                if("FAILED".equals(material.creativeProfileStatus)){body.put("error","实际素材场景尚未确认，已转为人工审核，不自动发布");jobs.update(job,"REVIEW",body);return;}
                if(!"READY".equals(material.creativeProfileStatus)){body.put("error","素材已就绪，等待实际场景、风格与展示角度分析");jobs.update(job,"SYNCING",body);return;}
                if(!directionEvidenceReady(material.creativeProfile,body.path("strategy"))){body.put("error","实际素材方向分析不足以核查失败方向，已转为人工审核，不自动发布");jobs.update(job,"REVIEW",body);return;}
                if(directionConflict(material.creativeProfile,body.path("strategy"))){body.put("error","实际素材仍包含已确认亏损的场景、风格或展示角度，已转为人工审核，不自动发布");jobs.update(job,"REVIEW",body);return;}
            }
            var report=state.result();if(report==null||!"READY".equals(report.path("state").asText())){body.put("error","素材已同步，等待新鲜且成熟的计划报表");jobs.update(job,"SYNCING",body);return;}
            var action=json.createObjectNode().put("kind","MATERIAL_ADD").put("cause","NEW_MATERIAL_TRIAL").put("generation_id",job.id()).put("product_id",job.productId());
            if(!readyToEnqueue(p,state,job,action,report,body)){jobs.update(job,"SYNCING",body);return;}
            jobs.update(job,"SYNCING",body);job=jobs.get(job.tenant(),job.id()).orElseThrow();jobs.enqueue(job,action,report);
        }
    }
    private void reconcile(Job job,ObjectNode body){
        String field="REMOVING".equals(job.status())?"removal_event_id":"publication_event_id";var event=journal.event(job.tenant(),body.path(field).asText()).orElse(null);
        if(event==null){body.put("error","执行记录尚未确认，禁止重复提交");jobs.update(job,job.status(),body);return;}
        if("SUCCEEDED".equals(event.status())){
            if("REMOVING".equals(job.status()))jobs.update(job,"REMOVED",body);
            else{body.put("object_key",event.actual().path("operation").path("objectKey").asText());jobs.published(job,body);}return;
        }
        if(Set.of("SKIPPED","FAILED","ACKNOWLEDGED").contains(event.status())){body.put("error",event.error());jobs.update(job,"REMOVING".equals(job.status())?"LOSS_RETAINED":"CANCELLED",body);return;}
        if("UNKNOWN".equals(event.status()))body.put("error","平台提交结果不明确，请到盯盘中心核查原执行记录");jobs.update(job,job.status(),body);
    }
    public static boolean directionEvidenceReady(JsonNode profile,JsonNode strategy){
        var direction=strategy.path("scene_strategy");boolean constrained=false;
        for(var field:Map.of("scenes","avoid_scenes","visual_style","avoid_styles","creative_angle","avoid_angles").entrySet())if(!direction.path(field.getValue()).isEmpty()){
            constrained=true;if(profile==null)return false;var value=profile.path(field.getKey());if(value.isArray()?value.isEmpty():value.asText().isBlank())return false;
        }
        return !constrained||(profile!=null&&"VISUAL_ANALYSIS".equals(profile.path("source").asText())&&profile.path("confidence").asDouble()<Double.POSITIVE_INFINITY&&profile.path("confidence").asDouble()>=.6);
    }
    public static boolean directionConflict(JsonNode profile,JsonNode strategy){
        if(profile==null||!"VISUAL_ANALYSIS".equals(profile.path("source").asText()))return false;
        var direction=strategy.path("scene_strategy");
        for(var field:Map.of("scenes","avoid_scenes","visual_style","avoid_styles","creative_angle","avoid_angles").entrySet()){
            var actual=profile.path(field.getKey());var values=new ArrayList<String>();if(actual.isArray())actual.forEach(v->values.add(v.asText().strip()));else values.add(actual.asText().strip());
            for(var banned:direction.path(field.getValue())){String label=banned.asText().strip();if(!label.isBlank()&&values.stream().anyMatch(v->!v.isBlank()&&(v.contains(label)||label.contains(v))))return true;}
        }return false;
    }
    private CreationState.Material material(JsonNode body){try{return json.treeToValue(body.path("material"),CreationState.Material.class);}catch(Exception ex){throw new BizException(1802,"生成素材记录无效");}}
    private boolean readyToEnqueue(AdPlan p,OptimizationStore.State state,Job job,JsonNode action,JsonNode report,ObjectNode body){
        try{var facts=gateway.facts(p);var e=new Event(job.id(),job.tenant(),job.planId(),job.accountId(),p.name().text(),job.version(),action.path("kind").asText(),"生成素材试投","",action,report,"READY",null,null,job.createdAt(),null,null);OptimizationExecutionService.validate(state,e,facts,Instant.now(),journal.actionsToday(job.tenant(),job.planId()));return true;}
        catch(RuntimeException ex){body.put("error",ex instanceof BizException?ex.getMessage():"等待完整且新鲜的执行依据");return false;}
    }
    public static CreationState.Material parse(JsonNode result,String product,String target,String engineId){
        var found=new ArrayList<JsonNode>();for(var g:result.path("groups"))if("group-1".equals(g.path("group_id").asText()))for(var m:g.path("materials"))if("v1".equals(m.path("version").asText())&&"succeeded".equals(m.path("status").asText()))found.add(m);
        if(found.size()!=1||!target.equals(found.getFirst().path("material_type").asText()))return null;
        var item=found.getFirst();var m=new CreationState.Material();m.id=item.path("material_id").asText();if(m.id.isBlank())return null;
        m.engineMaterialId=m.id;m.engineJobId=engineId;m.groupId="group-1";m.productId=product;m.type=switch(target){case "video"->"VIDEO";case "image_text"->"CAROUSEL";case "product_card"->"IMAGE";default->"";};
        m.title=item.path("content").path("title").asText();m.text=item.path("content").path("text").asText();m.titles=new ArrayList<>(List.of(m.title));var urls=item.path("urls");
        if("VIDEO".equals(m.type)){m.sourceUrls.put("VIDEO",urls.path("video_url").asText());m.sourceUrls.put("COVER",urls.path("cover_url").asText());m.previewUrl=urls.path("video_url").asText();m.posterUrl=urls.path("cover_url").asText();}
        else{int i=0;for(var url:urls.path("image_urls")){m.sourceUrls.put("IMAGE_"+i++,url.asText());m.imageUrls.add(url.asText());}if(i==0||"IMAGE".equals(m.type)&&i!=1)return null;m.previewUrl=m.imageUrls.getFirst();}
        if("CAROUSEL".equals(m.type))m.sourceUrls.put("AUDIO",urls.path("voice_url").asText());
        return m.sourceUrls.values().stream().anyMatch(String::isBlank)?null:m;
    }
    public record TrialSummary(BigDecimal spend,BigDecimal revenue,BigDecimal orders,BigDecimal impressions,BigDecimal clicks,BigDecimal profit,int days){}
    public static TrialSummary evaluate(JsonNode trial,OptimizationPolicy.Economics profile,BigDecimal reservation){
        if(!trial.path("complete").asBoolean()||trial.path("days").isEmpty())return null;
        var sums=new HashMap<String,BigDecimal>();for(String key:List.of("spend","net_revenue","orders","impressions","clicks"))sums.put(key,BigDecimal.ZERO);
        try{for(var day:trial.path("days"))for(String key:sums.keySet()){var raw=day.path("metrics").path(key);if(!raw.isTextual())return null;var value=new BigDecimal(raw.asText());if(value.signum()<0)return null;sums.put(key,sums.get(key).add(value));}}catch(RuntimeException ex){return null;}
        var profit=sums.get("net_revenue").multiply(profile.contributionMarginRate()).multiply(BigDecimal.ONE.subtract(profile.refundReserveRate())).subtract(sums.get("spend")).subtract(reservation);
        return new TrialSummary(sums.get("spend"),sums.get("net_revenue"),sums.get("orders"),sums.get("impressions"),sums.get("clicks"),profit,trial.path("days").size());
    }
    private void evaluateTrial(AdPlan p,OptimizationStore.State state,Job job,ObjectNode body){
        var trial=gateway.tactics().trial(p,job,state.policy());body.set("trial",trial);
        if(!trial.path("bound").asBoolean()){body.put("error","素材已从原计划移除，停止本次试投");jobs.update(job,"CANCELLED",body);return;}
        if("REJECTED".equals(trial.path("audit").asText())){body.put("error","平台审核拒绝；素材仍保留在素材中心，可人工处理");jobs.update(job,"FAILED",body);return;}
        if(!"PASS".equals(trial.path("audit").asText())){if(job.publishedAt()!=null&&job.publishedAt().plusSeconds(14*86400L).isBefore(Instant.now())){body.put("error","验证期结束仍无法确认平台审核状态，请人工检查");jobs.update(job,"UNTESTED",body);}else jobs.update(job,"PLATFORM_REVIEW",body);return;}
        var profile=policies.economics(p).stream().filter(e->e.productId().equals(job.productId())&&e.verified()).findFirst().orElse(null);
        var summary=profile==null?null:evaluate(trial,profile,job.reservedCost());if(summary!=null)body.set("trial_summary",json.valueToTree(summary));
        if(summary!=null&&summary.spend().subtract(summary.revenue().multiply(profile.contributionMarginRate()).multiply(BigDecimal.ONE.subtract(profile.refundReserveRate()))).compareTo(state.policy().creativeGeneration().trialLossLimit())>=0){
            if(!state.policy().tactics().materialRemove()||trial.path("retained").asBoolean()){body.put("error","试投达到亏损上限；未授权删除或须保留最后素材，请人工处理");jobs.update(job,"LOSS_RETAINED",body);}
            else{var report=state.result();if(report==null)return;var action=json.createObjectNode().put("kind","MATERIAL_REMOVE").put("cause","TRIAL_LOSS").put("generation_id",job.id()).put("product_id",job.productId()).put("entity_id",trial.path("entity_id").asText()).put("material_type",body.path("material").path("type").asText());if(!readyToEnqueue(p,state,job,action,report,body)){jobs.update(job,"TRIAL",body);return;}jobs.update(job,job.status(),body);jobs.enqueue(jobs.get(job.tenant(),job.id()).orElseThrow(),action,report);}return;
        }
        var limits=state.policy().creativeGeneration();
        if(summary!=null&&summary.days()>=state.policy().tactics().entityDays()&&summary.impressions().compareTo(BigDecimal.valueOf(limits.minImpressions()))>=0&&summary.clicks().compareTo(BigDecimal.valueOf(limits.minClicks()))>=0&&summary.orders().compareTo(BigDecimal.valueOf(state.policy().minOrders()))>=0&&summary.profit().signum()>0){jobs.update(job,"QUALIFIED",body);journal.notice(job.tenant(),job.planId(),"OPTIMIZATION_EXECUTION","生成素材试投已达标","已达到样本与成熟利润条件（扣除预留生成费用）；平台自主分配流量，不表示因果胜出");return;}
        if(job.publishedAt()!=null&&job.publishedAt().plusSeconds(14*86400L).isBefore(Instant.now())){body.put("error","验证期结束仍未达到样本或利润条件，保留素材供人工判断");jobs.update(job,"UNTESTED",body);return;}
        jobs.update(job,"TRIAL",body);
    }
}
