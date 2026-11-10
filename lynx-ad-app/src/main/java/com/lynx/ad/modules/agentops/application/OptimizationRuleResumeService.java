package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.exception.DomainException;
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
    private static final int CONFIRM_IMMEDIATE_PAUSE = 1815;
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

    public Preview configurationPreview(TenantId tenant,PlanId id,OptimizationRules rules,long version) {
        if(rules==null)throw new BizException(1802,"请填写盯盘规则");
        rules.validate();
        var plan=gateway.plan(tenant.value(),id.value());
        gateway.requireRuleOperator(plan);
        requireVersion(store.get(tenant,id).orElse(null),version);
        return inspect(plan,rules,version).preview();
    }

    public void configure(TenantId tenant,PlanId id,OptimizationRules rules,boolean enabled,long version,
                          Confirmation confirmation,String operator) {
        if(rules==null)throw new BizException(1802,"请填写盯盘规则");
        rules.validate();
        var plan=gateway.plan(tenant.value(),id.value());
        if(OptimizationMonitoringService.rulePlanEnded(plan))throw new BizException(1801,"计划已结束");
        gateway.requireRuleOperator(plan);
        var state=store.get(tenant,id).orElse(null);
        requireVersion(state,version);
        if (state!=null && OptimizationStore.TERMINAL_HOLD.equals(state.holdReason()))throw new BizException(1801,"计划已结束");
        if (!enabled) {
            if(state==null || state.policy().rules()==null)throw changed();
            store.pauseOrEditPausedRules(plan,state,rules,operator);
            return;
        }
        var existing=state==null?null:state.policy().rules();
        if (existing!=null && !state.enabled())throw new BizException(1803,"请先核对当天消耗，再确认恢复盯盘优化");
        if (existing!=null && OptimizationRules.AUTO_PAUSE.equals(existing.protectionAction())
                && OptimizationRules.CONFIRM.equals(rules.protectionAction()) && state.holdReason()==null) {
            // Revoke before any network read or account lease. The dispatch journal also checks the version under lock.
            store.deferRules(plan,state,rules,OptimizationStore.REVISION_HOLD,operator);
            return;
        }
        if (existing!=null && OptimizationStore.lifecycleHold(state.holdReason())) {
            store.deferRules(plan,state,rules,state.holdReason(),operator);
            return;
        }
        String account=plan.accountId().value();
        String owner="configure-rules:"+UUID.randomUUID();
        if(!journal.claimAccount(tenant.value(),account,owner))throw new BizException(1803,"账户操作处理中，请稍后重试");
        try {
            Evidence evidence;
            try {
                evidence=inspect(plan,rules,version);
                if(journal.unresolved(tenant.value(),account))throw new BizException(1803,"请先核实账户中已提交的操作");
            } catch (BizException | DomainException failure) {
                int code=failure instanceof BizException biz?biz.code():((DomainException)failure).code();
                if(existing==null || code==1801)throw failure;
                String hold=switch(code) {
                    case 403,1201,1205,1211 -> OptimizationStore.AUTHORIZATION_HOLD;
                    case 1102,1904 -> OptimizationStore.SERVICE_HOLD;
                    default -> rules.restricts(existing)?OptimizationStore.REVISION_HOLD:null;
                };
                store.deferRules(plan,state,rules,hold,operator);
                return;
            }
            var current=evidence.preview();
            if (current.outcome()==Outcome.LIMIT_PAUSE && confirmation==null)
                throw new BizException(CONFIRM_IMMEDIATE_PAUSE,"当天消耗已达到保护金额，请确认是否暂停广告计划");
            if (confirmation!=null) requireConfirmation(current,confirmation);
            store.applyCheckedRules(plan,rules,version,report(plan,rules,evidence,false),evidence.facts(),operator);
        } finally {
            journal.releaseAccount(tenant.value(),account,owner);
        }
    }

    private void requireVersion(OptimizationStore.State state,long version) {
        if((state==null?0:state.version())!=version)throw changed();
    }

    public Preview preview(TenantId tenant,PlanId id){
        var plan=gateway.plan(tenant.value(),id.value());
        gateway.requireRuleOperator(plan);
        var state=recoverableRules(tenant,id);
        return inspect(plan,savedRules(state),state.version()).preview();
    }

    public Result resume(TenantId tenant,PlanId id,Confirmation confirmation,String operator){
        var plan=gateway.plan(tenant.value(),id.value());
        gateway.requireRuleOperator(plan);
        String owner="resume-rules:"+UUID.randomUUID();
        String account=plan.accountId().value();
        if(!journal.claimAccount(tenant.value(),account,owner))throw new BizException(1803,"账户操作处理中，请稍后重试");
        OptimizationAutomationStore.Event pause=null;
        try{
            var state=recoverableRules(tenant,id);
            if(confirmation==null || confirmation.version()!=state.version())throw changed();
            if(journal.unresolved(tenant.value(),account))throw new BizException(1803,"请先核实账户中已提交的操作");
            plan=gateway.plan(tenant.value(),id.value());
            if(!account.equals(plan.accountId().value()))throw changed();
            var rules=savedRules(state);
            var evidence=inspect(plan,rules,state.version());
            var current=evidence.preview();
            requireConfirmation(current,confirmation);
            boolean autoPause=current.outcome()==Outcome.LIMIT_PAUSE;
            store.applyCheckedRules(plan,rules,state.version(),report(plan,rules,evidence,true),evidence.facts(),operator);
            if(autoPause)pause=journal.ruleActions(tenant.value(),id.value(),state.version()+1).stream()
                .filter(event->"PAUSE".equals(event.kind()) && "READY".equals(event.status())).findFirst().orElse(null);
        }finally{journal.releaseAccount(tenant.value(),account,owner);}
        if(pause==null)return new Result(null);
        execution.process(pause);
        return new Result(journal.event(tenant.value(),pause.id()).map(OptimizationAutomationStore.Event::status).orElse(null));
    }

    private OptimizationStore.State recoverableRules(TenantId tenant,PlanId id){
        return store.get(tenant,id).filter(state -> state.policy().rules()!=null
                && (!state.enabled() || state.pendingRules()!=null || OptimizationStore.lifecycleHold(state.holdReason())))
            .orElseThrow(()->new BizException(1804,"盯盘规则状态已变化，请刷新详情"));
    }

    private OptimizationRules savedRules(OptimizationStore.State state) {
        return state.pendingRules()==null?state.policy().rules():state.pendingRules();
    }

    private void requireConfirmation(Preview current,Confirmation confirmation) {
        if(current.version()!=confirmation.version() || !current.day().equals(confirmation.day())
            || !current.planStatus().equals(confirmation.planStatus()) || current.outcome()!=confirmation.outcome())throw changed();
    }

    private Evidence inspect(AdPlan plan,OptimizationRules rules,long version){
        if(!gateway.ready())throw new BizException(1801,"真实盯盘需要连接千川正式数据源");
        if(OptimizationMonitoringService.rulePlanEnded(plan))throw new BizException(1801,"计划已结束");
        gateway.writable(plan); // Verify service entitlement even when protection only sends reminders.
        var checkedAt=Instant.now();
        var platform=gateway.readPlanFacts(plan,false);
        var facts=platform.facts();
        String status=facts.path("status").asText();
        if(!plan.qcPlanId().orElseThrow().value().equals(facts.path("qcId").asText()))throw new BizException(1803,"平台计划绑定不一致");
        if("FINISHED".equals(status) || "DELETED".equals(status)
            || com.lynx.ad.modules.delivery.domain.service.PlanStatusPolicy.matches(status,platform.qcStatus(),"FINISHED"))throw new BizException(1801,"计划已结束");
        if(!java.util.Set.of("DELIVERING","PAUSED","PENDING_AUDIT").contains(status)
            && !OptimizationRulesEvaluator.planBudgetConstrained(platform.qcStatus()))throw new BizException(1803,"暂时无法核实广告计划状态，请重试");
        var day=LocalDate.now(ZONE);
        var data=insights.data(plan.tenantId(),plan.id(),new PlanStatisticsRange(day,day),true);
        var spend=data.summary().get("cost");
        var now=Instant.now();
        var fetched=data.fetchedAt();
        if(spend==null || spend.signum()<0 || fetched==null || fetched.plusSeconds(120).isBefore(now)
            || fetched.isAfter(now.plusSeconds(30)) || !day.equals(now.atZone(ZONE).toLocalDate())
            || !day.equals(fetched.atZone(ZONE).toLocalDate()))
            throw new BizException(1803,"暂时无法核实当天消耗，请重试");
        Outcome outcome=Outcome.BELOW_LIMIT;
        if(!"DELIVERING".equals(status))outcome=Outcome.PLAN_NOT_DELIVERING;
        else if(spend.compareTo(rules.spendProtection())>=0)
            outcome=OptimizationRules.AUTO_PAUSE.equals(rules.protectionAction())?Outcome.LIMIT_PAUSE:Outcome.LIMIT_REMINDER;
        var committed=confirmations.committed(plan.tenantId().value(),plan.id().value(),day.toString())
            .add(journal.extraBudgetToday(plan.tenantId().value(),plan.id().value()));
        return new Evidence(new Preview(version,day,status,platform.qcStatus(),spend,fetched,committed,rules,outcome),facts,checkedAt);
    }

    private JsonNode report(AdPlan plan,OptimizationRules rules,Evidence evidence,boolean resume){
        var preview=evidence.preview();
        var observed=Instant.now().atZone(ZONE);
        if(!preview.day().equals(observed.toLocalDate()))throw changed();
        var data=json.createObjectNode()
            .put("store_id",plan.tenantId().value()).put("account_id",plan.accountId().value())
            .put("plan_id",evidence.facts().path("qcId").asText())
            .put("plan_status",preview.planStatus()).put("observed_at",observed.toOffsetDateTime().toString())
            .put("plan_checked_at",evidence.checkedAt().toString())
            .put("daily_spend_date",preview.day().toString()).put("daily_spend",preview.spend().toPlainString())
            .put("fetched_at",preview.fetchedAt().toString()).put("complete",true)
            .put("daily_committed_addition",preview.committed());
        if(resume)data.put("resume_outcome",preview.outcome().name());
        if(resume && (preview.outcome()==Outcome.BELOW_LIMIT || preview.outcome()==Outcome.LIMIT_REMINDER))
            data.put("optimization_restarted_at",evidence.checkedAt().toString());
        data.put("platform_status",preview.qcStatus());
        data.set("product_ids",evidence.facts().path("productIds"));
        return OptimizationRulesEvaluator.evaluate(data,null,rules.policy(true));
    }

    private BizException changed(){return new BizException(1804,"规则、计划状态或当天消耗已变化，请重新核对后确认");}
}
