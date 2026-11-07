package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationReviewStore.Review;
import com.lynx.ad.modules.delivery.application.PlanInsightsService;
import com.lynx.ad.modules.delivery.domain.model.*;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OptimizationReviewService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final OptimizationReviewStore reviews;private final OptimizationStore policies;private final OptimizationAutomationStore journal;
    private final OptimizationExecutionGateway gateway;private final PlanInsightsService insights;private final MonitoringStrategy strategy;private final ObjectMapper json;
    public OptimizationReviewService(OptimizationReviewStore reviews,OptimizationStore policies,OptimizationAutomationStore journal,OptimizationExecutionGateway gateway,PlanInsightsService insights,MonitoringStrategy strategy,ObjectMapper json){this.reviews=reviews;this.policies=policies;this.journal=journal;this.gateway=gateway;this.insights=insights;this.strategy=strategy;this.json=json;}
    private com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore creations;
    @org.springframework.beans.factory.annotation.Autowired public void creations(com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore value){creations=value;}
    /** A staged content save is tracked only after resolving its server-owned target plan. */
    public void linkContent(String tenant,String workId,String reviewId){
        if(reviewId==null||!reviewId.matches("[a-fA-F0-9-]{36}")||creations==null)return;
        var r=reviews.find(tenant,reviewId).filter(v->v.status().equals("TRACKING")&&v.workflow().equals("materials")).orElse(null);if(r==null)return;
        var work=creations.get(tenant,workId);
        if(work.publishedEdit==null||!r.planId().equals(work.publishedEdit.targetPlanId))return;
        var p=gateway.plan(tenant,r.planId());if(beforeMerchantWrite(p,reviewId))reviews.linkContent(r,workId);
    }
    public Review start(String tenant,String plan,String eventId,String workflow){
        if(!Set.of("materials","budget","plan","data").contains(workflow))throw new BizException(1802,"请选择对应的整改流程");
        var p=gateway.plan(tenant,plan);var s=policies.get(p.tenantId(),p.id()).orElseThrow(()->new BizException(1802,"未配置盯盘规则"));
        var event=journal.event(tenant,eventId).filter(e->e.planId().equals(plan)&&e.version()==s.version()&&e.status().equals("RECOMMENDED")).orElseThrow(()->new BizException(1803,"建议已变化，请刷新后核对"));
        if(!s.enabled()||s.policy().rules()==null||s.holdReason()!=null)throw new BizException(1803,"当前规则尚未生效");
        String owner="review:"+UUID.randomUUID();if(!journal.claimAccount(tenant,p.accountId().value(),owner))throw new BizException(1803,"账户操作处理中");
        try{
            if(journal.unresolved(tenant,p.accountId().value()))throw new BizException(1803,"请先核实已提交操作");
            if(event.proposal()!=null)throw new BizException(1802,"该建议须通过操作确认执行");
            var facts=gateway.reviewFacts(p);var params=strategy.parameters();
            if(!params.approved())throw new BizException(1803,"经营诊断策略尚未完成校准");
            boolean current=false;for(var item:s.result().path("recommendations"))if(item.path("title").asText().equals(event.title())&&workflow.equals(item.path("workflow").asText()))current=true;
            if(!current||!s.result().path("complete").asBoolean()||!"DELIVERING".equals(facts.path("status").asText())
                ||OffsetDateTime.parse(s.result().path("observed_at").asText()).toInstant().plusSeconds(120).isBefore(Instant.now())
                ||policies.deliveryChangedSinceReport(s))throw new BizException(1803,"建议或数据已变化，请刷新后核对");
            var end=Instant.now().atZone(ZONE).minusMinutes(params.maturityMinutes()).withMinute(0).withSecond(0).withNano(0);
            return reviews.start(event,workflow,facts,window(p,end.minusHours(params.reviewHours()),end,params),params);
        }finally{journal.releaseAccount(tenant,p.accountId().value(),owner);}
    }
    /** Called only after a successful existing merchant write, never just after navigation or polling. */
    public boolean beforeMerchantWrite(AdPlan p,String reviewId){
        var r=reviews.find(p.tenantId().value(),reviewId).filter(v->v.planId().equals(p.id().value())&&v.status().equals("TRACKING")).orElse(null);
        return r!=null&&r.createdAt().plusSeconds(1800).isAfter(Instant.now())&&r.before().equals(gateway.reviewFacts(p));
    }
    public void merchantWriteCompleted(AdPlan p,String reviewId){
        var review=reviews.find(p.tenantId().value(),reviewId).filter(r->r.planId().equals(p.id().value())&&r.status().equals("TRACKING")).orElse(null);if(review==null)return;
        applyWorkflow(review,gateway.reviewFacts(p));
    }
    private void applyWorkflow(Review review,JsonNode actual){
        boolean changed=switch(review.workflow()){
            case "materials"->!review.before().path("bindings").equals(actual.path("bindings"));
            case "budget"->!review.before().path("budget").equals(actual.path("budget"));
            default->!review.before().equals(actual);
        };
        if(!changed)return;
        if(reviews.applied(review,actual))reviews.verifiedWorkflow(review,actual);
    }
    public void actionApplied(OptimizationAutomationStore.Event event,JsonNode facts){
        if(!event.report().path("rule_version").asText().equals("merchant-rules-v2"))return;
        var p=gateway.plan(event.tenant(),event.planId());var params=strategy.parameters();
        var at=event.dispatchedAt()==null?event.createdAt():event.dispatchedAt();
        var end=at.atZone(ZONE).minusMinutes(params.maturityMinutes()).withMinute(0).withSecond(0).withNano(0);
        var actual=gateway.reviewFacts(p);
        // The post-write readback cannot stand in for a pre-write snapshot. Legacy missing snapshots stay unknown.
        var before=event.report().get("pre_action_facts");
        var r=reviews.start(event,event.kind().equals("BUDGET")?"budget":"plan",before,window(p,end.minusHours(params.reviewHours()),end,params),params);
        reviews.applied(r,actual,event.finishedAt()==null?Instant.now():event.finishedAt());
    }
    @Scheduled(fixedDelayString="${lynx.ad.monitoring.review-scan-ms:60000}")
    public void scan(){for(var event:journal.unreviewed())try{actionApplied(event,event.actual());}catch(RuntimeException ignored){}for(var r:reviews.due())try{evaluate(r);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("review deferred id={}",r.id());}}
    void evaluate(Review r){String owner="review:"+UUID.randomUUID();if(!reviews.claim(r,owner))return;var result=json.createObjectNode();String status="WAITING_DATA";
        try{
            if(r.status().equals("TRACKING")){
                if(r.contentWorkId()!=null&&creations!=null){
                    var work=creations.get(r.tenant(),r.contentWorkId());
                    if(work.publishedEdit!=null&&r.planId().equals(work.publishedEdit.targetPlanId)&&"SUCCEEDED".equals(work.publishedEdit.status)){
                        var actual=gateway.reviewFacts(gateway.plan(r.tenant(),r.planId()));
                        var member=work.publishedEdit.members.stream().filter(m->r.planId().equals(m.planId)).findFirst().orElse(null);
                        if(member!=null&&member.after!=null&&com.lynx.ad.modules.delivery.application.PublishedMaterialService.same(member.after,actual.path("bindings")))applyWorkflow(r,actual);
                        if(reviews.find(r.tenant(),r.id()).map(v->!v.status().equals("TRACKING")).orElse(false))return;
                    }
                }
                status=r.createdAt().plusSeconds(1800).isBefore(Instant.now())?"NOT_APPLIED":"TRACKING";result.put("reason",status.equals("TRACKING")?"等待原流程中已确认的实际修改":"尚未取得已确认修改，本次未计完成");
            }
            else if(r.actionAt().plusSeconds(r.strategy().maxReviewHours()*3600L).isBefore(Instant.now())){status="INSUFFICIENT";result.put("reason","观察期限内未取得可比成熟样本，无法判定改善");}
            else {
                var p=gateway.plan(r.tenant(),r.planId());var facts=gateway.reviewFacts(p);
                if(!r.strategy().approved()||!"DELIVERING".equals(r.after().path("status").asText())){status="INCOMPARABLE";result.put("reason","当前操作未形成可比较的持续投放窗口，未计ROI改善");}
                else if(!r.after().equals(facts)){status="INCOMPARABLE";result.put("reason","观察期内计划状态、设置或素材再次变化，不能单独比较本次调整");}
                else {
                    var start=r.actionAt().atZone(ZONE).withMinute(0).withSecond(0).withNano(0).plusHours(1);var end=start.plusHours(r.strategy().reviewHours());
                    if(end.plusMinutes(r.strategy().maturityMinutes()).toInstant().isAfter(Instant.now()))result.put("reason","等待调整后的完整小时与成交回补");
                    else{
                        var after=window(p,start,end,r.strategy());var original=r.baseline();
                        // Refetch the exact baseline to include late refunds/revisions, preserving its initial snapshot.
                        var baseEnd=ZonedDateTime.parse(original.path("window_end").asText());var baseStart=ZonedDateTime.parse(original.path("window_start").asText());
                        var baseline=window(p,baseStart,baseEnd,r.strategy());result=OptimizationEffectComparison.compare(baseline,after,r.strategy());result.set("initial_baseline",original);status=result.path("status").asText();
                    }
                }
            }
        }catch(RuntimeException ex){result.put("reason","复评数据暂不可用，继续核对");}
        result.put("checked_at",Instant.now().toString());reviews.finish(r,owner,status,result);
    }
    ObjectNode window(AdPlan p,ZonedDateTime start,ZonedDateTime end,MonitoringStrategy.Parameters params){
        var n=json.createObjectNode().put("window_start",start.toOffsetDateTime().toString()).put("window_end",end.toOffsetDateTime().toString()).put("complete",true);var rows=n.putArray("rows");
        JsonNode fields=null;var observed=Instant.now().atZone(ZONE);
        for(var day=start.toLocalDate();!day.isAfter(end.minusSeconds(1).toLocalDate());day=day.plusDays(1)){
            var data=insights.data(p.tenantId(),p.id(),new PlanStatisticsRange(day,day),true);var current=json.valueToTree(data.metricFields());
            if(fields!=null&&!fields.equals(current)||data.warning()!=null&&!data.warning().isBlank()||data.fetchedAt()==null||data.fetchedAt().plusSeconds(120).isBefore(Instant.now()))n.put("complete",false);fields=current;
            for(var row:OptimizationMonitoringService.hourly(data,observed)){var at=OffsetDateTime.parse(row.get("window_start").toString()).toInstant();if(!at.isBefore(start.toInstant())&&at.isBefore(end.toInstant()))rows.add(json.valueToTree(row));}
        }
        n.set("metric_fields",fields==null?json.createObjectNode():fields);n.put("fetched_at",Instant.now().toString());return n;
    }
}
