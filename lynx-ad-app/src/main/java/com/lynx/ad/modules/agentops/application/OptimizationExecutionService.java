package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationAutomationStore.Event;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OptimizationExecutionService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final OptimizationStore policies;private final OptimizationAutomationStore journal;private final OptimizationExecutionGateway gateway;private final PlanInsightsService insights;
    public OptimizationExecutionService(OptimizationStore policies,OptimizationAutomationStore journal,OptimizationExecutionGateway gateway,PlanInsightsService insights){this.policies=policies;this.journal=journal;this.gateway=gateway;this.insights=insights;}
    @Scheduled(fixedDelayString="${lynx.ad.monitoring.execution-scan-ms:10000}")
    public void scan(){for(var event:journal.pending())if(!"MATERIAL_GENERATE".equals(event.kind()))try{process(event);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("execution check unavailable event={} type={}",event.id(),ex.getClass().getSimpleName());}}
    public void observe(AdPlan p,OptimizationStore.State state,JsonNode facts){
        if(!"AUTO_LIMITED".equals(state.policy().mode()) || state.holdReason()!=null)return;
        if(state.baseline()==null)journal.baseline(state,facts);
        else if(!state.baseline().equals(facts) && !journal.unresolved(state.tenant(),p.accountId().value()))hold(p,"检测到千川后台或人工变更，已暂停自动调控，请核查并重新授权");
    }
    public void hold(AdPlan p,String reason){var ids=gateway.related(p).stream().filter(id->policies.get(p.tenantId(),new PlanId(id)).map(s->"AUTO_LIMITED".equals(s.policy().mode())).orElse(false)).toList();journal.hold(p.tenantId().value(),ids,reason);}
    public String manualClaim(AdPlan p){hold(p,"用户正在手动操作计划，自动调控已暂停");String owner="manual:"+UUID.randomUUID();if(!journal.claimAccount(p.tenantId().value(),p.accountId().value(),owner))throw new BizException(1803,"账户正在提交或核查自动操作，请稍后重试手动操作");if(journal.unresolved(p.tenantId().value(),p.accountId().value())){journal.releaseAccount(p.tenantId().value(),p.accountId().value(),owner);throw new BizException(1803,"账户有自动操作结果待核查，请先到盯盘中心核查");}return owner;}
    public void release(AdPlan p,String owner){journal.releaseAccount(p.tenantId().value(),p.accountId().value(),owner);}
    public void process(Event event){
        String owner="execution:"+UUID.randomUUID();if(!journal.claimAccount(event.tenant(),event.accountId(),owner))return;
        boolean dispatched=false;AdPlan p=null;JsonNode intent=null;
        try{
            var current=journal.event(event.tenant(),event.id()).orElseThrow();
            if(!"READY".equals(current.status())){if(Set.of("EXECUTING","VERIFY_PENDING").contains(current.status())){dispatched=true;verify(current,false);}return;}
            p=gateway.plan(event.tenant(),event.planId());gateway.writable(p);
            var state=policies.get(p.tenantId(),p.id()).orElseThrow(()->new BizException(1802,"盯盘授权不存在"));
            if(journal.unresolved(event.tenant(),event.accountId()))throw new BizException(1803,"账户存在待核查的自动操作");
            var facts=gateway.facts(p);validate(state,event,facts,Instant.now(),journal.actionsToday(event.tenant(),event.planId()));
            if("ROI".equals(event.kind()) && !"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI".equals(facts.path("deepExternalAction").asText()))throw new BizException(1803,"平台优化目标不属于净成交ROI，不执行自动ROI调整");
            var products=new HashSet<String>();facts.path("productIds").forEach(n->products.add(n.asText()));var economics=policies.economics(p).stream().filter(e->products.contains(e.productId())).toList();state.policy().withEconomics(economics).validate(products);
            String cause=event.proposal().path("cause").asText();if(!Set.of("DAILY_LIMIT","NEXT_DAY","TRIAL_LOSS").contains(cause)){
                var factor=economics.stream().map(e->e.contributionMarginRate().multiply(BigDecimal.ONE.subtract(e.refundReserveRate()))).min(BigDecimal::compareTo).orElseThrow();
                var spend=decimal(event.report(),"spend");var computed=decimal(event.report(),"net_revenue").multiply(factor).subtract(spend);
                if(!"NEW_MATERIAL_TRIAL".equals(cause)&&spend.compareTo(state.policy().minSpend())<0 || computed.subtract(decimal(event.report(),"estimated_contribution_profit")).abs().compareTo(new BigDecimal("0.02"))>0)throw new BizException(1803,"最新利润资料或样本与执行依据不一致");
                if("ROI".equals(event.kind()) && "PROFITABLE_LOW_DELIVERY".equals(cause) && (factor.signum()<=0 || decimal(event.proposal(),"target").multiply(factor).compareTo(new BigDecimal("1.1"))<0))throw new BizException(1803,"调整后ROI低于商品保本保护线");
            }
            if(state.baseline()==null || !state.baseline().equals(facts)){hold(p,"平台状态与自动授权基准不同，请核查后恢复自动调控");throw new BizException(1803,"平台设置已变化");}
            BigDecimal daily=currentSpend(p);String kind=event.kind();
            if("PAUSE".equals(kind) && "DAILY_LIMIT".equals(event.proposal().path("cause").asText()) && daily.compareTo(state.policy().dailySpendLimit())<0)throw new BizException(1803,"最新消耗尚未达到每日限额");
            if(!"PAUSE".equals(kind) && daily.compareTo(state.policy().dailySpendLimit())>=0)throw new BizException(1803,"已达到每日限额，不执行扩量或调价");
            gateway.lock(p,event.id());
            OptimizationTacticsGateway.Prepared prepared=null;
            if(OptimizationTacticsGateway.KINDS.contains(kind)){
                gateway.assertPeerStopped(p);
                if(gateway.tactics()==null)throw new BizException(1803,"自动内容执行服务未就绪");
                prepared=gateway.tactics().prepare(p,state.policy(),event.proposal(),event.id());intent=gateway.tactics().intent(prepared);
                if(Set.of("BOOST","MATERIAL_FOLLOW").contains(kind)){
                    var amount=decimal(event.proposal(),"budget");if(amount.compareTo(decimal(facts,"budget"))>0)throw new BizException(1803,"自动追加预算不得超过原计划日预算");
                    var limit=journal.limit(event.tenant(),event.accountId());if(limit==null||gateway.accountBudget(p).add(gateway.activeTaskBudget(p)).add(amount).compareTo(limit)>0)throw new BizException(1803,"账户计划预算和追加任务预算超过授权上限");
                }
                // Preparation uses live reads; re-check manual edits and freshness before committing a write intent.
                if(!facts.equals(gateway.facts(p)))throw new BizException(1803,"执行准备期间平台绑定或设置发生变化");
                validate(state,event,facts,Instant.now(),journal.actionsToday(event.tenant(),event.planId()));
            }
            if("ENABLE".equals(kind))gateway.assertPeerStopped(p);
            boolean increase="ENABLE".equals(kind) || "BUDGET".equals(kind) && decimal(event.proposal(),"target").compareTo(decimal(facts,"budget"))>0 || "ROI".equals(kind) && decimal(event.proposal(),"target").compareTo(decimal(facts,"roi"))<0;
            if(increase){var limit=journal.limit(event.tenant(),event.accountId());if(limit==null)throw new BizException(1803,"请先设置广告账户总日预算上限");var sum=gateway.accountBudget(p);var addition="ENABLE".equals(kind)?decimal(facts,"budget"):"BUDGET".equals(kind)?decimal(event.proposal(),"target").subtract(decimal(facts,"budget")):BigDecimal.ZERO;if(sum.add(addition).compareTo(limit)>0)throw new BizException(1803,"账户总日预算会超过授权上限");}
            // The durable journal is committed before any platform mutation. A restart only reads back.
            if(!journal.dispatch(event,owner))throw new BizException(1803,"授权或执行租约已变化");
            dispatched=true;
            if(prepared!=null){journal.intent(event,intent);gateway.tactics().write(p,event.proposal(),prepared);}else gateway.write(p,event.proposal());var actual=gateway.facts(p);
            if(intent!=null){((com.fasterxml.jackson.databind.node.ObjectNode)actual).set("operation",intent);((com.fasterxml.jackson.databind.node.ObjectNode)actual).put("actionConfirmed",gateway.tactics().matches(p,event.proposal(),intent));}
            if(matches(event,actual)){gateway.sync(p,actual);journal.succeeded(event,actual);gateway.unlock(p,event.id());}
            else journal.finish(event,"VERIFY_PENDING","平台暂未返回目标值，继续只读核查",actual);
        }catch(RuntimeException ex){String reason=ex instanceof BizException?ex.getMessage():"平台通信或本地保存失败";
            if(dispatched)journal.finish(event,"VERIFY_PENDING",reason+"；不会重复提交",journal.event(event.tenant(),event.id()).map(Event::actual).orElse(null));
            else {journal.finish(event,"SKIPPED",reason,null);if(p!=null)gateway.unlock(p,event.id());}
        }finally{journal.releaseAccount(event.tenant(),event.accountId(),owner);}
    }
    private BigDecimal currentSpend(AdPlan p){var today=LocalDate.now(ZONE);var data=insights.data(p.tenantId(),p.id(),new PlanStatisticsRange(today,today),true);if(data.fetchedAt()==null || data.fetchedAt().plusSeconds(600).isBefore(Instant.now()) || !today.equals(LocalDate.now(ZONE)) || data.summary().get("cost")==null)throw new BizException(1803,"当日风险报表不完整或已过期");return data.summary().get("cost");}
    public static BigDecimal decimal(JsonNode node,String key){try{var field=node.get(key);if(field==null || !field.isTextual())throw new IllegalArgumentException();return new BigDecimal(field.asText());}catch(RuntimeException ex){throw new BizException(1802,"自动调控数值格式不正确");}}
    public static void validate(OptimizationStore.State s,Event e,JsonNode facts,Instant now,int count){
        var policy=s.policy();var a=policy.automation();
        if(!s.enabled() || !"AUTO_LIMITED".equals(policy.mode()) || s.holdReason()!=null || s.version()!=e.version())throw new BizException(1803,"自动执行授权已失效");
        if(!"PAUSE".equals(e.kind()) && (count>=a.maxActionsPerDay() || s.lastAction()!=null && s.lastAction().plusSeconds(a.cooldownMinutes()*60L).isAfter(now)))throw new BizException(1803,"已达每日动作次数或仍在冷却期");
        var observed=OffsetDateTime.parse(e.report().path("observed_at").asText()).toInstant();if(observed.isAfter(now.plusSeconds(30)) || observed.plusSeconds(600).isBefore(now) || !observed.atZone(ZONE).toLocalDate().equals(now.atZone(ZONE).toLocalDate()))throw new BizException(1803,"执行依据已过期，等待下一轮数据");
        if(!facts.path("qcId").asText().equals(e.report().path("plan_id").asText()) || !e.tenant().equals(e.report().path("store_id").asText()) || !e.accountId().equals(e.report().path("account_id").asText()))throw new BizException(1802,"执行依据计划或账户不匹配");
        String kind=e.kind(),cause=e.proposal().path("cause").asText(),status=facts.path("status").asText();
        if(!Set.of("BUDGET","ROI","PAUSE","ENABLE").contains(kind==null?"":kind)&&!OptimizationTacticsGateway.KINDS.contains(kind==null?"":kind))throw new BizException(1802,"不支持该自动动作");
        if(!kind.equals(e.proposal().path("kind").asText()))throw new BizException(1802,"自动动作记录不一致");
        if(!("ENABLE".equals(kind)?"PAUSED":"DELIVERING").equals(status))throw new BizException(1803,"平台启停状态不满足执行条件");
        if(OptimizationTacticsGateway.KINDS.contains(kind)){
            var t=policy.tactics();boolean allowed=switch(kind){case "BOOST"->t.boost();case "MATERIAL_FOLLOW"->t.materialFollow();case "MATERIAL_ADD"->t.materialAdd();case "MATERIAL_REMOVE"->t.materialRemove();default->t.productRemove();};
            String expectedCause=kind.endsWith("REMOVE")?"ENTITY_LOSS":kind.endsWith("ADD")?"NEW_MATERIAL_TRIAL":"PERSISTENT_PROFIT";
            if("MATERIAL_REMOVE".equals(kind)&&"TRIAL_LOSS".equals(cause))expectedCause="TRIAL_LOSS";
            if(("MATERIAL_ADD".equals(kind)||"TRIAL_LOSS".equals(cause))&&(!policy.creativeGeneration().enabled()||policy.creativeGeneration().authorizedBy()==null||e.proposal().path("generation_id").asText().isBlank()))throw new BizException(1802,"素材试投来源或生成授权缺失");
            if(!allowed||!expectedCause.equals(cause)||Set.of("BOOST","MATERIAL_FOLLOW").contains(kind)&&!"CUSTOM".equals(facts.path("bidType").asText()))throw new BizException(1802,"新增动作未授权或方向不符合规则");
            if(Set.of("BOOST","MATERIAL_FOLLOW").contains(kind) && (decimal(e.proposal(),"budget").compareTo(t.taskBudget())!=0 || decimal(e.proposal(),"duration").compareTo(t.taskHours())!=0 || t.taskBudget().compareTo(decimal(facts,"budget"))>0))throw new BizException(1802,"追加预算或时长超出授权");
            if("PRODUCT_REMOVE".equals(kind)&&facts.path("productIds").size()<=t.retainProducts())throw new BizException(1803,"不得移除最后的商品");
        }
        if(Set.of("PAUSE","ENABLE").contains(kind) && (!status.equals(e.proposal().path("expected").asText()) || !("PAUSE".equals(kind)?"PAUSED":"DELIVERING").equals(e.proposal().path("target").asText())))throw new BizException(1802,"自动启停目标不正确");
        if("PAUSE".equals(kind)){if(!a.pause() || !Set.of("DAILY_LIMIT","LOSS").contains(cause))throw new BizException(1802,"未授权自动暂停");}
        if("ENABLE".equals(kind)){if(!a.resumeNextDay() || !"NEXT_DAY".equals(cause) || !"DAILY_LIMIT".equals(s.autoPausedReason()) || s.autoPausedAt()==null || !s.autoPausedAt().atZone(ZONE).toLocalDate().isBefore(now.atZone(ZONE).toLocalDate()) || now.atZone(ZONE).getHour()<a.resumeHour())throw new BizException(1802,"不允许自动恢复人工暂停或亏损止损计划");}
        if(Set.of("BUDGET","ROI").contains(kind)){
            String field="BUDGET".equals(kind)?"budget":"roi";var expected=decimal(e.proposal(),"expected");var target=decimal(e.proposal(),"target");
            if(expected.compareTo(decimal(facts,field))!=0 || target.stripTrailingZeros().scale()>2 || target.signum()<=0 || target.compareTo(expected)==0 || target.subtract(expected).abs().multiply(new BigDecimal("100")).compareTo(expected.multiply(a.maxStepPercent()))>0)throw new BizException(1803,"当前值变化或调整幅度超出授权");
            if("BUDGET".equals(kind)){var floor="CUSTOM".equals(facts.path("bidType").asText())?new BigDecimal("300"):new BigDecimal("50");if(!a.budget() || target.compareTo(a.minBudget().max(floor))<0 || target.compareTo(a.maxBudget())>0 || !Set.of("PERSISTENT_LOSS","PERSISTENT_PROFIT").contains(cause) || "PERSISTENT_LOSS".equals(cause)!=(target.compareTo(expected)<0))throw new BizException(1802,"预算调整未授权或方向不符合规则");}
            else if(!a.roi() || !"CUSTOM".equals(facts.path("bidType").asText()) || target.compareTo(a.minRoi())<0 || target.compareTo(a.maxRoi())>0 || !Set.of("PERSISTENT_LOSS","PROFITABLE_LOW_DELIVERY").contains(cause) || "PERSISTENT_LOSS".equals(cause)!=(target.compareTo(expected)>0))throw new BizException(1802,"ROI调整未授权或方向不符合规则");
        }
        if(!Set.of("DAILY_LIMIT","NEXT_DAY","TRIAL_LOSS").contains(cause)){
            if(!"READY".equals(e.report().path("state").asText()) || e.report().path("window_count").asInt()!=policy.persistenceWindows())throw new BizException(1803,"成熟判断窗口不完整");
            var end=OffsetDateTime.parse(e.report().path("window_end").asText()).toInstant();var expectedEnd=observed.atZone(ZONE).minusMinutes(policy.conversionDelayMinutes()).withMinute(0).withSecond(0).withNano(0).toInstant();if(!end.equals(expectedEnd))throw new BizException(1803,"成熟窗口已过期");
            var profit=decimal(e.report(),"estimated_contribution_profit");if("NEW_MATERIAL_TRIAL".equals(cause)&&profit.compareTo(policy.maxLoss().negate())<=0)throw new BizException(1803,"计划已达到止损线，不发布新素材");if(!Set.of("ENTITY_LOSS","NEW_MATERIAL_TRIAL").contains(cause)&&("LOSS".equals(cause)?profit.compareTo(policy.maxLoss().negate())>0:"PERSISTENT_LOSS".equals(cause)?profit.signum()>=0:profit.signum()<=0))throw new BizException(1803,"利润证据不支持该操作");
            if(Set.of("PERSISTENT_PROFIT","PROFITABLE_LOW_DELIVERY","PROFITABLE_CANDIDATE").contains(cause) && (decimal(e.report(),"orders").compareTo(BigDecimal.valueOf(policy.minOrders()))<0 || decimal(e.report(),"spend").signum()<=0 || profit.compareTo(decimal(e.report(),"spend").multiply(new BigDecimal(".2")))<0))throw new BizException(1803,"成交样本或利润率不足，不扩量");
            if("ROI".equals(kind) && "PROFITABLE_LOW_DELIVERY".equals(cause) && decimal(e.proposal(),"target").compareTo(decimal(e.report(),"break_even_net_roi").multiply(new BigDecimal("1.1")))<0)throw new BizException(1803,"调整后ROI低于保本保护线");
        }
    }
    public void verify(Event event,boolean requested){
        var p=gateway.plan(event.tenant(),event.planId());JsonNode actual=null;
        try{actual=gateway.facts(p);if(OptimizationTacticsGateway.READBACK_KINDS.contains(event.kind())&&event.actual()!=null){var intent=event.actual().path("operation");((com.fasterxml.jackson.databind.node.ObjectNode)actual).set("operation",intent);((com.fasterxml.jackson.databind.node.ObjectNode)actual).put("actionConfirmed",gateway.tactics().matches(p,event.proposal(),intent));}if(matches(event,actual)){gateway.sync(p,actual);journal.succeeded(event,actual);gateway.unlock(p,event.id());return;}}catch(RuntimeException ex){if(requested)throw ex;}
        if(event.dispatchedAt()!=null && event.dispatchedAt().plusSeconds(180).isBefore(Instant.now())){journal.finish(event,"UNKNOWN","提交结果无法确认，已停止账户后续自动执行，请人工核查",actual==null?event.actual():actual);hold(p,"自动操作结果不明确，请到盯盘中心核查后重新授权");}
    }
    public static boolean matches(Event event,JsonNode actual){if(!event.report().path("plan_id").asText().equals(actual.path("qcId").asText()))return false;return switch(event.kind()){
        case "BUDGET"->decimal(actual,"budget").compareTo(decimal(event.proposal(),"target"))==0;
        case "ROI"->actual.hasNonNull("roi") && decimal(actual,"roi").compareTo(decimal(event.proposal(),"target"))==0;
        case "PAUSE","ENABLE"->event.proposal().path("target").asText().equals(actual.path("status").asText());default->OptimizationTacticsGateway.READBACK_KINDS.contains(event.kind())&&actual.path("actionConfirmed").asBoolean(false);};}
    public void recheck(String tenant,String id,boolean acknowledge){var event=journal.event(tenant,id).orElseThrow(()->new BizException(1405,"调控记录不存在"));if("MATERIAL_GENERATE".equals(event.kind()))throw new BizException(1802,"请在素材生成与试投记录中查看原生成任务");if(!Set.of("UNKNOWN","VERIFY_PENDING","EXECUTING").contains(event.status()))throw new BizException(1802,"仅核查结果不明确的操作");String owner="verify:"+UUID.randomUUID();if(!journal.claimAccount(tenant,event.accountId(),owner))throw new BizException(1803,"账户操作处理中，请稍后核查");try{if(acknowledge){if(event.dispatchedAt()==null || event.dispatchedAt().plusSeconds(180).isAfter(Instant.now()))throw new BizException(1803,"等待提交完成后再确认人工核查");var p=gateway.plan(tenant,event.planId());var facts=gateway.facts(p);journal.finish(event,"ACKNOWLEDGED","用户已核查并接受平台当前状态；不代表原操作成功",facts);gateway.unlock(p,id);hold(p,"已人工核查结果，需重新授权自动调控");}else verify(event,true);}finally{journal.releaseAccount(tenant,event.accountId(),owner);}}
    public void resume(String tenant,String id,long version){var p=gateway.plan(tenant,id);gateway.writable(p);String owner="resume:"+UUID.randomUUID();if(!journal.claimAccount(tenant,p.accountId().value(),owner))throw new BizException(1803,"账户操作处理中");try{if(journal.unresolved(tenant,p.accountId().value()))throw new BizException(1803,"请先核查账户中结果不明确的操作");var s=policies.get(p.tenantId(),p.id()).orElseThrow(()->new BizException(1802,"未配置盯盘"));if(!s.enabled() || !"AUTO_LIMITED".equals(s.policy().mode()))throw new BizException(1802,"请先开启并保存自动调控模式");var facts=gateway.facts(p);var products=new HashSet<String>();facts.path("productIds").forEach(n->products.add(n.asText()));s.policy().withEconomics(policies.economics(p).stream().filter(e->products.contains(e.productId())).toList()).validate(products);journal.resume(tenant,id,version,facts);}finally{release(p,owner);}}
}
