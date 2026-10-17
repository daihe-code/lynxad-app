package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OptimizationMonitoringService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
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
    private com.lynx.ad.modules.creative.application.MaterialCreativeContextService creativeContexts;
    @org.springframework.beans.factory.annotation.Autowired public void creativeContexts(com.lynx.ad.modules.creative.application.MaterialCreativeContextService value){creativeContexts=value;}
    @org.springframework.beans.factory.annotation.Autowired public void execution(OptimizationExecutionService execution,OptimizationExecutionGateway gateway,OptimizationAutomationStore journal){this.execution=execution;this.gateway=gateway;this.journal=journal;}
    public record View(OptimizationPolicy policy,long version,List<String> productIds,boolean available,String unavailableReason,
                       Instant lastCheck,Instant lastSuccess,String error,boolean stale,JsonNode result,String holdReason,Instant lastAction,String bidType) {}
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
    private List<OptimizationPolicy.Economics> economics(AdPlan p,Set<String> ids) {return store.economics(p).stream().filter(e->ids.contains(e.productId())).toList();}
    public View view(TenantId tenant,PlanId id) {
        var p=plan(tenant,id);var state=store.get(tenant,id).orElse(null);var config=state==null?OptimizationPolicy.defaults():state.policy();
        var ids=p.productIds().stream().map(QcProductId::value).toList();
        if(state!=null && state.result()!=null && state.result().path("product_ids").isArray()) {
            var savedIds=new ArrayList<String>();state.result().path("product_ids").forEach(v->{if(v.isTextual() && !v.asText().isBlank())savedIds.add(v.asText());});
            if(!savedIds.isEmpty())ids=List.copyOf(savedIds);
        }
        // Synchronized detail metadata also covers imported/transfer plans with no local draft bindings.
        if(ids.isEmpty() && p.qcPlanId().isPresent() && unavailable(p)==null)ids=management.monitoringFacts(tenant,id).productIds();
        if(config.tactics().content()&&p.qcPlanId().isPresent()&&unavailable(p)==null)ids=management.monitoringFacts(tenant,id).productIds();
        var result=state==null?null:state.result();
        boolean stale=result==null || state.lastSuccess()==null || state.lastSuccess().plusSeconds(600).isBefore(Instant.now());
        if(result!=null)try {stale=stale || Instant.parse(result.path("observed_at").asText()).plusSeconds(600).isBefore(Instant.now());}catch(RuntimeException ex){stale=true;}
        return new View(config.withEconomics(economics(p,Set.copyOf(ids))),state==null?0:state.version(),ids,unavailable(p)==null,unavailable(p),
            state==null?null:state.lastCheck(),state==null?null:state.lastSuccess(),state==null?null:state.error(),stale,result,state==null?null:state.holdReason(),state==null?null:state.lastAction(),p.setting().bidType().name());
    }
    public View save(TenantId tenant,PlanId id,OptimizationPolicy policy,long expectedVersion) {
        var p=plan(tenant,id);if(policy==null)throw new BizException(1802,"请填写盯盘设置");
        if(policy.enabled() && unavailable(p)!=null)throw new BizException(1801,unavailable(p));
        var ids=p.productIds().stream().map(QcProductId::value).toList();
        if(unavailable(p)==null)ids=management.monitoringFacts(tenant,id).productIds();
        if(expectedVersion<0)throw new BizException(1802,"盯盘设置版本不正确");
        policy.validate(Set.copyOf(ids));
        if(gateway!=null && gateway.tactics()!=null)gateway.tactics().validateCandidates(p,policy);
        if("AUTO_LIMITED".equals(policy.mode()) && policy.automation().budget() && p.setting().bidType()==SmartBidType.CUSTOM && policy.automation().minBudget().compareTo(SmartBidType.CUSTOM.minimumDailyBudget())<0)throw new BizException(1802,"控成本自动预算下限不能低于 300 元");
        String owner="policy:"+UUID.randomUUID();
        if(journal!=null && !journal.claimAccount(tenant.value(),p.accountId().value(),owner))throw new BizException(1803,"账户操作处理中，请稍后保存授权");
        try{if(journal!=null && journal.unresolved(tenant.value(),p.accountId().value()))throw new BizException(1803,"请先核查账户中结果不明确的操作");store.save(p,policy,expectedVersion);}finally{if(journal!=null)journal.releaseAccount(tenant.value(),p.accountId().value(),owner);}return view(tenant,id);
    }
    public View run(TenantId tenant,PlanId id) {
        var p=plan(tenant,id);if(unavailable(p)!=null)throw new BizException(1801,unavailable(p));
        var state=store.get(tenant,id).filter(OptimizationStore.State::enabled).orElseThrow(()->new BizException(1802,"请先开启并保存自动盯盘"));
        cycle(state);return view(tenant,id);
    }
    public void cycle(OptimizationStore.State state) {
        String owner=UUID.randomUUID().toString();if(!store.claim(state,owner,Instant.now()))return;
        String failure=null;
        try {
            var tenant=new TenantId(state.tenant());var id=new PlanId(state.plan());var p=plan(tenant,id);
            if(unavailable(p)!=null)throw new BizException(1801,unavailable(p));
            var pending=store.pending(state).orElse(null);
            // Old revisions must not overwrite a changed policy, or starve fresh observations after an outage.
            if(pending!=null && (pending.version()!=state.version() || pending.created().plusSeconds(600).isBefore(Instant.now()))) {store.discard(state,pending.id());pending=null;}
            if(pending==null)pending=store.enqueue(state,owner,collect(p,state));
            var result=engine.submit(state.tenant(),pending.payload());
            if(creativeContexts!=null)creativeContexts.saveAssessments(state.tenant(),p.accountId().value(),result);
            store.complete(state,owner,pending,result);
        }catch(RuntimeException ex) {
            if(ex instanceof BizException biz && biz.code()==1405)store.disableMissing(state,owner);
            failure=ex instanceof BizException?ex.getMessage():"盯盘采集失败，将自动重试";
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("monitor unavailable plan={} type={}",state.plan(),ex.getClass().getSimpleName());
        }finally{store.release(state,owner,failure);}
    }
    Map<String,Object> collect(AdPlan p,OptimizationStore.State state) {
        var facts=management.monitoringFacts(p.tenantId(),p.id());
        if(execution!=null)execution.observe(p,state,gateway.facts(p));
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
            var row=new LinkedHashMap<String,Object>();row.put("window_start",start.toOffsetDateTime().toString());row.put("window_end",end.toOffsetDateTime().toString());row.put("metric_mode","window");row.put("granularity","plan");row.put("complete",report.warning()==null || report.warning().isBlank());row.put("metrics",metrics);result.add(row);
        }
        return result;
    }
}
