package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.service.PlanStatusPolicy;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OptimizationMonitoringService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(OptimizationMonitoringService.class);
    private final AdPlanRepository plans;
    private final OptimizationStore store;
    private final MonitoringEngineClient engine;
    private final PlanInsightsService insights;
    private final PlanManagementService management;
    private final PlanTransferStore transfers;
    private final LynxAdProperties properties;
    private OptimizationExecutionService execution;
    private OptimizationExecutionGateway gateway;
    private OptimizationAutomationStore journal;
    private MonitoringStrategy strategy;
    @org.springframework.beans.factory.annotation.Autowired public void strategy(MonitoringStrategy value){strategy=value;}
    private com.lynx.ad.modules.creative.application.MaterialCreativeContextService creativeContexts;
    @org.springframework.beans.factory.annotation.Autowired public void creativeContexts(com.lynx.ad.modules.creative.application.MaterialCreativeContextService value){creativeContexts=value;}
    @org.springframework.beans.factory.annotation.Autowired public void execution(OptimizationExecutionService execution,OptimizationExecutionGateway gateway,OptimizationAutomationStore journal){this.execution=execution;this.gateway=gateway;this.journal=journal;}
    public record View(OptimizationPolicy policy,long version,List<String> productIds,boolean available,String unavailableReason,
                       Instant lastCheck,Instant lastSuccess,String error,boolean stale,JsonNode result,String holdReason,Instant lastAction,String bidType,String planName,String accountId,String planStatus,String qcStatus,OptimizationRules pendingRules) {}
    public OptimizationMonitoringService(AdPlanRepository plans,OptimizationStore store,MonitoringEngineClient engine,
            PlanInsightsService insights,PlanManagementService management,PlanTransferStore transfers,LynxAdProperties properties) {
        this.plans=plans;this.store=store;this.engine=engine;this.insights=insights;this.management=management;this.transfers=transfers;this.properties=properties;
    }
    private AdPlan plan(TenantId tenant,PlanId id) {return plans.findById(tenant,id).filter(p->p.status()!=PlanStatus.DELETED).orElseThrow(()->new BizException(1405,"计划不存在"));}
    private String unavailable(AdPlan p) {
        if(p.qcPlanId().isEmpty())return "草稿尚未发布";
        if(properties.getMock().isQianchuan())return "真实盯盘需要连接千川正式数据源";
        if(!engine.ready())return "盯盘服务尚未就绪，请联系管理员完成服务配置";
        return null;
    }
    private String unavailable(AdPlan p,OptimizationPolicy policy) {
        if(policy.rules()==null)return unavailable(p);
        return rulesUnavailable(p);
    }
    private String rulesUnavailable(AdPlan p) {
        if(rulePlanEnded(p))return "计划已结束";
        if(p.qcPlanId().isEmpty())return "草稿尚未发布";
        return properties.getMock().isQianchuan()?"真实盯盘需要连接千川正式数据源":null;
    }
    static boolean rulePlanEnded(AdPlan plan) {
        return plan.status()==PlanStatus.FINISHED || plan.status()==PlanStatus.DELETED || PlanStatusPolicy.matches(plan.status().name(),plan.qcStatus(),"FINISHED");
    }
    private List<OptimizationPolicy.Economics> economics(AdPlan p,Set<String> ids) {return store.economics(p).stream().filter(e->ids.contains(e.productId())).toList();}
    public View view(TenantId tenant,PlanId id) {
        return view(tenant,id,true);
    }
    private View view(TenantId tenant,PlanId id,boolean fetchPlatformIds) {
        var p=plan(tenant,id);var state=store.get(tenant,id).orElse(null);var config=state==null?OptimizationPolicy.defaults():state.policy();
        var ids=p.productIds().stream().map(QcProductId::value).toList();
        if(state!=null && state.result()!=null && state.result().path("product_ids").isArray()) {
            var savedIds=new ArrayList<String>();state.result().path("product_ids").forEach(v->{if(v.isTextual() && !v.asText().isBlank())savedIds.add(v.asText());});
            if(!savedIds.isEmpty())ids=List.copyOf(savedIds);
        }
        // Synchronized detail metadata also covers imported/transfer plans with no local draft bindings.
        if(fetchPlatformIds && ids.isEmpty() && p.qcPlanId().isPresent() && unavailable(p)==null)ids=management.monitoringFacts(tenant,id).productIds();
        if(fetchPlatformIds && config.tactics().content()&&p.qcPlanId().isPresent()&&unavailable(p)==null)ids=management.monitoringFacts(tenant,id).productIds();
        var result=state==null?null:state.result();
        boolean stale=result==null || state.lastSuccess()==null || state.lastSuccess().plusSeconds(config.rules()==null?600:180).isBefore(Instant.now());
        if(result!=null)try {stale=stale || OffsetDateTime.parse(result.path("observed_at").asText()).toInstant().plusSeconds(config.rules()==null?600:180).isBefore(Instant.now());}catch(RuntimeException ex){stale=true;}
        if(!stale && state!=null && state.enabled() && !fetchPlatformIds)stale=store.deliveryChangedSinceReport(state);
        return new View(config.withEconomics(economics(p,Set.copyOf(ids))),state==null?0:state.version(),ids,unavailable(p,config)==null,unavailable(p,config),
            state==null?null:state.lastCheck(),state==null?null:state.lastSuccess(),state==null?null:state.error(),stale,result,state==null?null:state.holdReason(),state==null?null:state.lastAction(),p.setting().bidType().name(),p.name()==null?null:p.name().text(),p.accountId().value(),p.status().name(),p.qcStatus(),state==null?null:state.pendingRules());
    }
    public View save(TenantId tenant,PlanId id,OptimizationPolicy policy,long expectedVersion) {
        if(policy!=null && policy.rules()!=null)throw new BizException(1803,"请通过盯盘规则入口修改该计划");
        return save(tenant,id,policy,expectedVersion,null);
    }
    private View save(TenantId tenant,PlanId id,OptimizationPolicy policy,long expectedVersion,String operator) {
        var p=plan(tenant,id);if(policy==null)throw new BizException(1802,"请填写盯盘设置");
        String reason=unavailable(p,policy);
        if(reason!=null && (policy.enabled() || policy.rules()!=null && rulePlanEnded(p)))throw new BizException(1801,reason);
        var ids=p.productIds().stream().map(QcProductId::value).toList();
        if(policy.rules()==null && unavailable(p,policy)==null)ids=management.monitoringFacts(tenant,id).productIds();
        if(expectedVersion<0)throw new BizException(1802,"盯盘设置版本不正确");
        policy.validate(Set.copyOf(ids));
        if(gateway!=null && gateway.tactics()!=null)gateway.tactics().validateCandidates(p,policy);
        if("AUTO_LIMITED".equals(policy.mode()) && policy.automation().budget() && p.setting().bidType()==SmartBidType.CUSTOM && policy.automation().minBudget().compareTo(SmartBidType.CUSTOM.minimumDailyBudget())<0)throw new BizException(1802,"控成本自动预算下限不能低于 300 元");
        String owner="policy:"+UUID.randomUUID();
        if(journal!=null && !journal.claimAccount(tenant.value(),p.accountId().value(),owner))throw new BizException(1803,"账户操作处理中，请稍后保存授权");
        try{if(journal!=null && policy.enabled() && journal.unresolved(tenant.value(),p.accountId().value()))throw new BizException(1803,"请先核查账户中结果不明确的操作");store.save(p,policy,expectedVersion,operator);}finally{if(journal!=null)journal.releaseAccount(tenant.value(),p.accountId().value(),owner);}return policy.rules()==null?view(tenant,id):rulesView(tenant,id);
    }
    public View rulesView(TenantId tenant,PlanId id) {
        var value=view(tenant,id,false);var p=plan(tenant,id);
        String reason=rulesUnavailable(p);
        return new View(value.policy(),value.version(),value.productIds(),reason==null,reason,value.lastCheck(),value.lastSuccess(),value.error(),value.stale(),value.result(),value.holdReason(),value.lastAction(),value.bidType(),value.planName(),value.accountId(),value.planStatus(),value.qcStatus(),value.pendingRules());
    }
    public View run(TenantId tenant,PlanId id) {
        var p=plan(tenant,id);
        var state=store.get(tenant,id).filter(OptimizationStore.State::enabled).orElseThrow(()->new BizException(1802,"请先开启并保存自动盯盘"));
        if(unavailable(p,state.policy())!=null)throw new BizException(1801,unavailable(p,state.policy()));
        cycle(state);return view(tenant,id);
    }
    public void cycle(OptimizationStore.State state) {
        String owner=UUID.randomUUID().toString();if(!store.claim(state,owner,Instant.now()))return;
        String failure=null;
        AdPlan p=null;
        try {
            var tenant=new TenantId(state.tenant());var id=new PlanId(state.plan());p=plan(tenant,id);
            if(state.policy().rules()!=null) {
                if(rulePlanEnded(p)){store.suspend(p,state,OptimizationStore.TERMINAL_HOLD);return;}
                if(gateway!=null)gateway.writable(p);
            }
            if(unavailable(p,state.policy())!=null)throw new BizException(1801,unavailable(p,state.policy()));
            var pending=store.pending(state).orElse(null);
            // Merchant rules are evaluated locally and must re-read delivery status after a retry.
            if(pending!=null && (state.policy().rules()!=null || pending.version()!=state.version()
                    || pending.created().plusSeconds(600).isBefore(Instant.now()))) {store.discard(state,pending.id());pending=null;}
            if(pending==null) {
                var input=state.policy().rules()==null?collect(p,state):collectRules(p,state);
                if(state.policy().rules()!=null && Set.of("FINISHED","DELETED").contains(input.get("plan_status"))) {
                    store.suspend(p,state,OptimizationStore.TERMINAL_HOLD);
                    return;
                }
                pending=store.enqueue(state,owner,input);
            }
            var result=state.policy().rules()==null?engine.submit(state.tenant(),pending.payload()):OptimizationRulesEvaluator.evaluate(pending.payload(),state.result(),state.policy(),strategy==null?MonitoringStrategy.Parameters.shadow():strategy.parameters());
            if(creativeContexts!=null)creativeContexts.saveAssessments(state.tenant(),p.accountId().value(),result);
            store.complete(state,owner,pending,result);
        }catch(RuntimeException ex) {
            int code=ex instanceof BizException biz?biz.code():ex instanceof DomainException domain?domain.code():0;
            if(p!=null && state.policy().rules()!=null) {
                String hold=switch(code) {
                    case 403,1201,1205,1211 -> OptimizationStore.AUTHORIZATION_HOLD;
                    case 1102,1904 -> OptimizationStore.SERVICE_HOLD;
                    default -> null;
                };
                if(hold!=null){store.suspend(p,state,hold);return;}
            }
            if (ex instanceof BizException biz) {
                if (biz.code() == 1405) store.disableMissing(state,owner);
                failure = biz.code() == 1211 ? OptimizationCollectionError.AUTHORIZATION
                        : OptimizationCollectionError.publicMessage(Objects.toString(biz.getMessage(), ""));
                log.warn("monitor unavailable plan={} code={} reason={}",state.plan(),biz.code(),failure);
            } else {
                failure = OptimizationCollectionError.UNAVAILABLE;
                log.warn("monitor unavailable plan={} type={}",state.plan(),ex.getClass().getSimpleName());
            }
        }finally{store.release(state,owner,failure);}
    }
    Map<String,Object> collectRules(AdPlan p,OptimizationStore.State state) {
        var checkedAt=Instant.now();
        var facts=management.monitoringFacts(p.tenantId(),p.id(),false);
        if(!p.qcPlanId().orElseThrow().value().equals(facts.qcId()))throw new BizException(1802,"平台计划绑定不一致");
        if (execution != null) execution.observe(p,state);
        var today=LocalDate.now(ZONE);var current=insights.data(p.tenantId(),p.id(),new PlanStatisticsRange(today,today),true);
        var observed=Instant.now().atZone(ZONE);
        if(current.fetchedAt()==null || current.fetchedAt().isAfter(observed.toInstant().plusSeconds(30)) || current.fetchedAt().plusSeconds(120).isBefore(observed.toInstant()) || !today.equals(observed.toLocalDate()) || !today.equals(current.fetchedAt().atZone(ZONE).toLocalDate()))throw new BizException(1803,"等待当天最新报表");
        var data=new LinkedHashMap<String,Object>();data.put("request_id",UUID.randomUUID().toString());data.put("store_id",p.tenantId().value());data.put("account_id",p.accountId().value());data.put("plan_id",facts.qcId());data.put("product_ids",facts.productIds());
        data.put("observed_at",observed.toOffsetDateTime().toString());data.put("plan_status",facts.status());data.put("daily_spend_date",today.toString());
        data.put("plan_checked_at",checkedAt.toString());
        data.put("platform_status",facts.qcStatus());
        var previousReport=state.result();
        if(previousReport!=null && previousReport.hasNonNull("observed_at") && facts.qcId().equals(previousReport.path("plan_id").asText())) {
            var priorCheck=OffsetDateTime.parse(previousReport.path("plan_checked_at").asText(previousReport.path("observed_at").asText())).toInstant();
            var manual=store.deliveryChange(state.tenant(),state.plan(),priorCheck).orElse(null);
            if(manual!=null && (manual.at().isAfter(checkedAt) || "MANUAL_DISABLE".equals(manual.type()) && "DELIVERING".equals(facts.status())))
                throw new BizException(1803,"投放状态正在核对");
            boolean previouslyPaused="PAUSED".equals(previousReport.path("plan_status").asText()) || previousReport.path("delivery_paused").asBoolean();
            if("DELIVERING".equals(facts.status())) {
                if(previouslyPaused || manual!=null && "MANUAL_ENABLE".equals(manual.type()))
                    data.put("optimization_restarted_at",checkedAt.toString());
                else if(previousReport.hasNonNull("optimization_restarted_at"))
                    data.put("optimization_restarted_at",previousReport.path("optimization_restarted_at").asText());
            } else if(previouslyPaused || "PAUSED".equals(facts.status()) || manual!=null) {
                data.put("delivery_paused",true);
            } else if(previousReport.hasNonNull("optimization_restarted_at")) {
                data.put("optimization_restarted_at",previousReport.path("optimization_restarted_at").asText());
            }
        }
        data.put("daily_spend",decimal(current.summary().get("cost")));data.put("daily_net_revenue",decimal(current.summary().get("netGmv")));data.put("complete",current.summary().get("cost")!=null && current.summary().get("cost").signum()>=0);
        data.put("daily_budget",decimal(facts.budget()));data.put("bid_type",facts.bidType());data.put("metric_fields",current.metricFields());data.put("fetched_at",current.fetchedAt().toString());
        data.put("source_as_of",null); // A successful fetch is not the platform's conversion update time.
        var rows=new ArrayList<Map<String,Object>>(hourly(current,observed));
        try {
            var previous=insights.data(p.tenantId(),p.id(),new PlanStatisticsRange(today.minusDays(1),today.minusDays(1)),false);
            if(current.metricFields().equals(previous.metricFields()) && previous.fetchedAt()!=null && previous.fetchedAt().plusSeconds(600).isAfter(observed.toInstant()))rows.addAll(hourly(previous,observed));
        }catch(RuntimeException ignored){/* History loss cannot stop independent reliable spend protection. */}
        data.put("observations",rows);
        if(gateway!=null && gateway.tactics()!=null) {
            if ("DELIVERING".equals(facts.status())) {
                data.put("delivery_check", deliveryCheck(p, observed, current, facts, previousReport,
                    Objects.toString(data.get("optimization_restarted_at"), "")));
            }
            var context=gateway.tactics().merchantContext(p,observed);
            if(context!=null) {
                context.put("budget_constrained",OptimizationRulesEvaluator.planBudgetConstrained(facts.qcStatus()));
                data.put("diagnostic_context",context);
            }
        }
        data.put("execution_pending",journal!=null && !journal.rulePending(p.tenantId().value(),p.id().value()).isEmpty());
        return data;
    }

    private Map<String,String> deliveryCheck(AdPlan plan, ZonedDateTime observed, PlanInsightsService.Statistics current,
                                             PlanManagementService.MonitoringFacts facts, JsonNode previous, String restartedAt) {
        var today = observed.toLocalDate();
        if (hasDeliveryActivity(current.summary()) || current.trend().stream().anyMatch(point -> hasDeliveryActivity(point.metrics()))) {
            return Map.of("activity", "ACTIVE", "checked_at", observed.toOffsetDateTime().toString());
        }
        var priorProducts = new ArrayList<String>();
        if (previous != null) previous.path("product_ids").forEach(id -> priorProducts.add(id.asText()));
        if (previous != null && facts.qcId().equals(previous.path("plan_id").asText())
            && "DELIVERING".equals(previous.path("plan_status").asText())
            && today.toString().equals(previous.path("daily_spend_date").asText())
            && restartedAt.equals(previous.path("optimization_restarted_at").asText())
            && facts.productIds().equals(priorProducts)) {
            var check = previous.path("delivery_check");
            var checkedAt = check.path("checked_at").asText();
            if (!checkedAt.isEmpty()) {
                var at = OffsetDateTime.parse(checkedAt);
                if (!at.isAfter(observed.toOffsetDateTime()) && at.plusMinutes(10).isAfter(observed.toOffsetDateTime()))
                    return Map.of("activity", check.path("activity").asText(), "checked_at", checkedAt);
            }
        }
        var activity = gateway.tactics().productDeliveryActivity(plan, today, facts.productIds());
        return Map.of("activity", activity.name(), "checked_at", observed.toOffsetDateTime().toString());
    }

    private static boolean hasDeliveryActivity(Map<String, BigDecimal> metrics) {
        return List.of("cost", "shows", "clicks", "orders", "netGmv").stream().map(metrics::get)
            .anyMatch(value -> value != null && value.signum() > 0);
    }

    Map<String,Object> collect(AdPlan p,OptimizationStore.State state) {
        var facts=management.monitoringFacts(p.tenantId(),p.id());
        if (execution != null) execution.observe(p,state);
        if(!p.qcPlanId().orElseThrow().value().equals(facts.qcId()) || facts.productIds().isEmpty())throw new BizException(1802,"平台计划或商品绑定不完整");
        var today=LocalDate.now(ZONE);var reports=new ArrayList<PlanInsightsService.Statistics>();
        var previous=insights.data(p.tenantId(),p.id(),new PlanStatisticsRange(today.minusDays(1),today.minusDays(1)),true);
        var current=insights.data(p.tenantId(),p.id(),new PlanStatisticsRange(today,today),true);
        reports.add(previous);reports.add(current);var observed=Instant.now().atZone(ZONE);
        for(var report:reports)if(report.fetchedAt()==null || report.fetchedAt().plusSeconds(600).isBefore(observed.toInstant()))throw new BizException(1803,"平台报表缓存已过期，等待最新数据");
        // Crossing midnight changes the meaning of the cumulative daily risk counter. Recollect next cycle.
        if(!observed.toLocalDate().equals(today))throw new BizException(1803,"跨日采集，下一轮将重新读取数据");
        var rows=new ArrayList<Map<String,Object>>();
        for(var report:reports)rows.addAll(hourly(report,observed));
        var data=new LinkedHashMap<String,Object>();data.put("request_id",UUID.randomUUID().toString());data.put("store_id",p.tenantId().value());
        data.put("account_id",p.accountId().value());data.put("plan_id",facts.qcId());
        var group=transfers.group(p.tenantId(),p.id());data.put("logical_plan_id",group==null?p.id().value():group.root());
        data.put("platform","douyin");data.put("currency","CNY");data.put("timezone","Asia/Shanghai");data.put("policy_version",state.version());
        data.put("observed_at",observed.toOffsetDateTime().toString());data.put("plan_status",Set.of("DELIVERING","PAUSED").contains(facts.status())?facts.status():"OTHER");
        data.put("daily_budget",decimal(facts.budget()));data.put("roi_target",decimal(facts.roi()));data.put("bid_type",facts.bidType());
        data.put("product_ids",facts.productIds());data.put("policy",state.policy().enginePolicy());
        var context=new LinkedHashMap<String,Object>();context.put("auto_paused_reason",state.autoPausedReason());context.put("auto_paused_at",state.autoPausedAt()==null?null:state.autoPausedAt().atZone(ZONE).toOffsetDateTime().toString());context.put("last_action_at",state.lastAction()==null?null:state.lastAction().atZone(ZONE).toOffsetDateTime().toString());data.put("automation",context);
        data.put("economics",economics(p,Set.copyOf(facts.productIds())).stream().map(e->Map.of("product_id",e.productId(),"contribution_margin_rate",e.contributionMarginRate().toPlainString(),"refund_reserve_rate",e.refundReserveRate().toPlainString(),"verified",e.verified())).toList());
        data.put("daily_spend",decimal(current.summary().get("cost")));data.put("daily_spend_date",today.toString());data.put("observations",rows);
        if(state.policy().tactics().active()&&gateway!=null&&gateway.tactics()!=null)data.put("tactics_context",gateway.tactics().context(p,state.policy(),observed));
        return data;
    }
    public List<OptimizationTacticsGateway.CandidatePlan> candidates(TenantId tenant,PlanId id){var p=plan(tenant,id);return gateway==null||gateway.tactics()==null?List.of():gateway.tactics().candidates(p);}
    static String decimal(BigDecimal v) {return v==null?null:v.toPlainString();}
    static List<Map<String,Object>> hourly(PlanInsightsService.Statistics report,ZonedDateTime observed) {
        if(!"stat_time_hour".equals(report.timeDimension()))return List.of();
        var result=new ArrayList<Map<String,Object>>();
        for(var point:report.trend()) {
            ZonedDateTime start;
            try {start=LocalDateTime.parse(point.time().replace('T',' ').substring(0,19),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZONE);}
            catch(RuntimeException ex){throw new BizException(1802,"平台小时报表时间格式不正确");}
            if(start.getMinute()!=0 || start.getSecond()!=0)throw new BizException(1802,"平台未返回对齐的小时窗口");
            var end=start.plusHours(1);if(end.isAfter(observed))continue;
            var metrics=new LinkedHashMap<String,Object>();metrics.put("spend",decimal(point.metrics().get("cost")));metrics.put("orders",decimal(point.metrics().get("orders")));metrics.put("net_revenue",decimal(point.metrics().get("netGmv")));
            metrics.put("impressions",decimal(point.metrics().get("shows")));metrics.put("clicks",decimal(point.metrics().get("clicks")));metrics.put("refund_rate",decimal(point.metrics().get("refundRate")));
            var row=new LinkedHashMap<String,Object>();row.put("window_start",start.toOffsetDateTime().toString());row.put("window_end",end.toOffsetDateTime().toString());row.put("metric_mode","window");row.put("granularity","plan");row.put("complete",report.warning()==null || report.warning().isBlank());row.put("metrics",metrics);result.add(row);
        }
        return result;
    }
}
