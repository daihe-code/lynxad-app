package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationAutomationStore.Event;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OptimizationDecisionService {
    public record Preview(String eventId,long version,String hash,JsonNode facts,JsonNode action,JsonNode report,BigDecimal addition,BigDecimal committed,Instant expiresAt){}
    private final OptimizationStore policies;private final OptimizationAutomationStore journal;private final OptimizationExecutionGateway gateway;
    private final OptimizationConfirmationStore confirmations;private final OptimizationExecutionService execution;
    public OptimizationDecisionService(OptimizationStore policies,OptimizationAutomationStore journal,OptimizationExecutionGateway gateway,OptimizationConfirmationStore confirmations,OptimizationExecutionService execution){this.policies=policies;this.journal=journal;this.gateway=gateway;this.confirmations=confirmations;this.execution=execution;}
    Event event(String tenant,String plan,String id){return journal.event(tenant,id).filter(e->e.planId().equals(plan)).orElseThrow(()->new BizException(1405,"盯盘建议不存在"));}
    public Preview preview(String tenant,String plan,String id){
        var event=event(tenant,plan,id);var p=gateway.plan(tenant,plan);var state=policies.get(p.tenantId(),p.id()).orElseThrow();
        if(!event.status().equals("RECOMMENDED")||event.proposal()==null)throw new BizException(1803,"当前建议没有可确认执行的操作");
        var facts=OptimizationExecutionService.actionFacts(gateway,p,event);validate(state,event,facts,Instant.now());
        if(policies.deliveryChangedSinceReport(state))throw new BizException(1803,"投放已变化，请等待最新判断");
        String day=LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();var addition=addition(event.proposal());
        var used=confirmations.committed(tenant,plan,day);
        return new Preview(id,state.version(),hash(state,event,facts),facts,event.proposal(),state.result(),addition,used,Instant.now().plusSeconds(90));
    }
    public Event confirm(String tenant,String plan,String id,String actor,long version,String expectedHash){
        var original=event(tenant,plan,id);if(confirmations.find(tenant,id).isPresent())return original; // An HTTP retry never queues another action.
        var p=gateway.plan(tenant,plan);gateway.writable(p);String owner="confirm:"+UUID.randomUUID();if(!journal.claimAccount(tenant,p.accountId().value(),owner))throw new BizException(1803,"账户操作处理中，请稍后确认");
        try{
            if(journal.unresolved(tenant,p.accountId().value()))throw new BizException(1803,"请先核实账户中已提交操作");
            var preview=preview(tenant,plan,id);if(preview.version()!=version||!preview.hash().equals(expectedHash))throw new BizException(1804,"方案或平台事实已变化，请重新确认");
            var state=policies.get(p.tenantId(),p.id()).orElseThrow();
            confirmations.approve(original,actor,preview.facts(),preview.action(),preview.report(),preview.addition(),state.policy().rules().dailyAddition(),owner);
        }finally{journal.releaseAccount(tenant,p.accountId().value(),owner);}
        execution.process(event(tenant,plan,id));return event(tenant,plan,id);
    }
    public static BigDecimal addition(JsonNode action){return action.path("kind").asText().equals("BUDGET")?OptimizationExecutionService.decimal(action,"target").subtract(OptimizationExecutionService.decimal(action,"expected")).max(BigDecimal.ZERO):BigDecimal.ZERO;}
    public static void validate(OptimizationStore.State s,Event event,JsonNode facts,Instant now){
        if(s.policy().rules()==null||!s.enabled()||s.holdReason()!=null||s.version()!=event.version()||s.result()==null)throw new BizException(1803,"当前规则或建议已失效");
        var report=s.result();var at=OffsetDateTime.parse(report.path("observed_at").asText()).toInstant();
        if(at.plusSeconds(120).isBefore(now)||at.isAfter(now.plusSeconds(30))||!at.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())||!report.path("complete").asBoolean()||report.path("execution_pending").asBoolean())throw new BizException(1803,"执行依据已过期或有操作待核实");
        if(!event.tenant().equals(report.path("store_id").asText())||!event.accountId().equals(report.path("account_id").asText())||!facts.path("qcId").equals(report.path("plan_id"))||!facts.path("status").asText().equals("DELIVERING")||!"DELIVERING".equals(report.path("plan_status").asText("DELIVERING")))throw new BizException(1803,"平台对象或投放状态已变化");
        var action=event.proposal();String kind=action.path("kind").asText();
        if(!Set.of("BUDGET","PAUSE").contains(kind)||!kind.equals(event.kind()))throw new BizException(1802,"该操作须进入原有流程独立确认");
        boolean current=false;for(var item:report.path("recommendations"))if(action.equals(item.path("action")))current=true;
        if(!current)throw new BizException(1803,"最新判断已不支持原方案");
        if(kind.equals("PAUSE")){if(!action.path("expected").asText().equals("DELIVERING")||!action.path("target").asText().equals("PAUSED"))throw new BizException(1802,"暂停方案不正确");return;}
        if(!report.path("strategy_approved").asBoolean()||!report.path("business_ready").asBoolean()||!report.path("diagnostic_context").path("ledger_verified").asBoolean()||!report.path("diagnostic_context").path("budget_constrained").asBoolean())throw new BizException(1803,"策略、预算瓶颈及当日新增投入尚未核实");
        var expected=OptimizationExecutionService.decimal(action,"expected");var target=OptimizationExecutionService.decimal(action,"target");
        if(expected.compareTo(OptimizationExecutionService.decimal(facts,"budget"))!=0||target.stripTrailingZeros().scale()>2||target.compareTo(new BigDecimal("999999999.99"))>0||target.signum()<=0)throw new BizException(1803,"预算值已变化或不合法");
        var delta=addition(action);var rules=s.policy().rules();
        if(delta.signum()<=0||delta.compareTo(rules.maxAddition())>0||delta.compareTo(OptimizationExecutionService.decimal(report.path("diagnostic_context"),"headroom"))>0)throw new BizException(1803,"新增预算超过本计划风险空间");
    }
    private static String hash(OptimizationStore.State state,Event event,JsonNode facts){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((state.version()+"|"+event.id()+"|"+facts+"|"+event.proposal()+"|"+state.result().path("observed_at").asText()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
