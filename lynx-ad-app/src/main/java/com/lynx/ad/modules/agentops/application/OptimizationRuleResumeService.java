package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.application.PlanInsightsService;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OptimizationRuleResumeService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    public enum Outcome { BELOW_LIMIT, LIMIT_PAUSE, LIMIT_REMINDER, PLAN_NOT_DELIVERING }
    public record Preview(long version,LocalDate day,String planStatus,String qcStatus,BigDecimal spend,Instant fetchedAt,
                          BigDecimal committed,OptimizationRules rules,Outcome outcome){}
    public record Confirmation(long version,LocalDate day,String planStatus,Outcome outcome){}
    public record Result(String pauseStatus){}
    private record Evidence(Preview preview,JsonNode facts,Instant checkedAt){}

    private final OptimizationStore store;
    private final OptimizationAutomationStore journal;
    private final OptimizationExecutionGateway gateway;
    private final PlanInsightsService insights;
    private final OptimizationConfirmationStore confirmations;
    private final OptimizationExecutionService execution;
    private final ObjectMapper json;

    public OptimizationRuleResumeService(OptimizationStore store,OptimizationAutomationStore journal,
            OptimizationExecutionGateway gateway,PlanInsightsService insights,OptimizationConfirmationStore confirmations,
            OptimizationExecutionService execution,ObjectMapper json){
        this.store=store;this.journal=journal;this.gateway=gateway;this.insights=insights;
        this.confirmations=confirmations;this.execution=execution;this.json=json;
    }

    public Preview preview(TenantId tenant,PlanId id){
        var plan=gateway.plan(tenant.value(),id.value());
        return inspect(plan,pausedRules(tenant,id)).preview();
    }

    public Result resume(TenantId tenant,PlanId id,Confirmation confirmation){
        var plan=gateway.plan(tenant.value(),id.value());
        String owner="resume-rules:"+UUID.randomUUID();
        String account=plan.accountId().value();
        if(!journal.claimAccount(tenant.value(),account,owner))throw new BizException(1803,"账户操作处理中，请稍后重试");
        OptimizationAutomationStore.Event pause=null;
        try{
            var state=pausedRules(tenant,id);
            if(confirmation.version()!=state.version())throw changed();
            if(journal.unresolved(tenant.value(),account))throw new BizException(1803,"请先核实账户中已提交的操作");
            plan=gateway.plan(tenant.value(),id.value());
            if(!account.equals(plan.accountId().value()))throw changed();
            var evidence=inspect(plan,state);
            var current=evidence.preview();
            if(!current.day().equals(confirmation.day()) || !current.planStatus().equals(confirmation.planStatus())
                || current.outcome()!=confirmation.outcome())throw changed();
            boolean autoPause=current.outcome()==Outcome.LIMIT_PAUSE;
            if(autoPause)gateway.writable(plan);
            store.resumeRules(plan,state,report(plan,state,evidence),evidence.facts());
            if(autoPause)pause=journal.ruleActions(tenant.value(),id.value(),state.version()+1).stream()
                .filter(event->"PAUSE".equals(event.kind()) && "READY".equals(event.status())).findFirst().orElse(null);
        }finally{journal.releaseAccount(tenant.value(),account,owner);}
        if(pause==null)return new Result(null);
        execution.process(pause);
        return new Result(journal.event(tenant.value(),pause.id()).map(OptimizationAutomationStore.Event::status).orElse(null));
    }

    private OptimizationStore.State pausedRules(TenantId tenant,PlanId id){
        return store.get(tenant,id).filter(state->!state.enabled() && state.policy().rules()!=null)
            .orElseThrow(()->new BizException(1804,"盯盘规则状态已变化，请刷新详情"));
    }

    private Evidence inspect(AdPlan plan,OptimizationStore.State state){
        if(!gateway.ready())throw new BizException(1801,"真实盯盘需要连接千川正式数据源");
        if(OptimizationMonitoringService.rulePlanEnded(plan))throw new BizException(1801,"计划已结束");
        var checkedAt=Instant.now();
        var platform=gateway.readPlanFacts(plan);
        var facts=platform.facts();
        String status=facts.path("status").asText();
        if("FINISHED".equals(status) || "DELETED".equals(status))throw new BizException(1801,"计划已结束");
        if(status.isBlank() || "UNKNOWN".equals(status))throw new BizException(1803,"暂时无法核实广告计划状态，请重试");
        var day=LocalDate.now(ZONE);
        var data=insights.data(plan.tenantId(),plan.id(),new PlanStatisticsRange(day,day),true);
        var spend=data.summary().get("cost");
        var now=Instant.now();
        var fetched=data.fetchedAt();
        if(spend==null || spend.signum()<0 || fetched==null || fetched.plusSeconds(120).isBefore(now)
            || fetched.isAfter(now.plusSeconds(30)) || !day.equals(now.atZone(ZONE).toLocalDate())
            || !day.equals(fetched.atZone(ZONE).toLocalDate()))
            throw new BizException(1803,"暂时无法核实当天消耗，请重试");
        var rules=state.policy().rules();
        Outcome outcome=Outcome.BELOW_LIMIT;
        if(!"DELIVERING".equals(status))outcome=Outcome.PLAN_NOT_DELIVERING;
        else if(spend.compareTo(rules.spendProtection())>=0)
            outcome=OptimizationRules.AUTO_PAUSE.equals(rules.protectionAction())?Outcome.LIMIT_PAUSE:Outcome.LIMIT_REMINDER;
        var committed=confirmations.committed(state.tenant(),state.plan(),day.toString())
            .add(journal.extraBudgetToday(state.tenant(),state.plan()));
        return new Evidence(new Preview(state.version(),day,status,platform.qcStatus(),spend,fetched,committed,rules,outcome),facts,checkedAt);
    }

    private JsonNode report(AdPlan plan,OptimizationStore.State state,Evidence evidence){
        var preview=evidence.preview();
        var observed=Instant.now().atZone(ZONE);
        if(!preview.day().equals(observed.toLocalDate()))throw changed();
        var data=json.createObjectNode()
            .put("store_id",state.tenant()).put("account_id",plan.accountId().value())
            .put("plan_id",evidence.facts().path("qcId").asText())
            .put("plan_status",preview.planStatus()).put("observed_at",observed.toOffsetDateTime().toString())
            .put("plan_checked_at",evidence.checkedAt().toString())
            .put("daily_spend_date",preview.day().toString()).put("daily_spend",preview.spend().toPlainString())
            .put("fetched_at",preview.fetchedAt().toString()).put("complete",true);
        if(preview.outcome()==Outcome.BELOW_LIMIT || preview.outcome()==Outcome.LIMIT_REMINDER)
            data.put("optimization_restarted_at",evidence.checkedAt().toString());
        data.set("product_ids",evidence.facts().path("productIds"));
        return OptimizationRulesEvaluator.evaluate(data,null,state.policy());
    }

    private BizException changed(){return new BizException(1804,"规则、计划状态或当天消耗已变化，请重新核对后确认");}
}
