package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.Qianchuan401ReplayCodes;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanInsightsStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PlanInsightsService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    public record Point(String time,Map<String,BigDecimal> metrics) {}
    public record Statistics(Map<String,BigDecimal> summary,List<Point> trend,String timeDimension,Instant fetchedAt,String warning,Map<String,String> metricFields) {
        public Statistics(Map<String,BigDecimal> summary,List<Point> trend,String timeDimension,Instant fetchedAt,String warning){this(summary,trend,timeDimension,fetchedAt,warning,Map.of());}
    }
    public record PlatformLog(String id,String time,String actor,String operation,List<String> details) {}
    public record GuaranteeView(String status,String reason,String url,Instant fetchedAt,String warning,PlanInsightsStore.Page<PlanInsightsStore.Guarantee> history) {}
    public record SpendingDay(LocalDate date,BigDecimal cost) {}
    public record DailySpending(LocalDate startDate,LocalDate endDate,BigDecimal totalCost,List<SpendingDay> days,Instant fetchedAt,String warning) {}
    private record Access(AdPlan plan,Credential credential) {}
    private record Key(String tenant,String id,PlanStatisticsRange range) {}
    private final Map<Key,Statistics> cache=new ConcurrentHashMap<>();
    private final AdPlanRepository plans;
    private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;
    private final AdManagementPort port;
    private final PlanStatisticsService statistics;
    private final PlanInsightsStore store;
    public PlanInsightsService(AdPlanRepository plans,OauthAccountRepository accounts,QianchuanCredentialFacade credentials,
            AdManagementPort port,PlanStatisticsService statistics,PlanInsightsStore store) {
        this.plans=plans;this.accounts=accounts;this.credentials=credentials;this.port=port;this.statistics=statistics;this.store=store;
    }
    private Access access(TenantId tenant,PlanId id) {
        AdPlan plan=plans.findById(tenant,id).filter(p->p.status()!=PlanStatus.DELETED).orElseThrow(()->new BizException(1405,"计划不存在"));
        var account=accounts.findById(tenant,plan.accountId()).orElseThrow(()->new BizException(1201,"授权账户不存在"));
        if(account.platform()!=Platform.QIANCHUAN || !"ACTIVE".equals(account.status()))throw new BizException(1201,"广告账户授权已失效，请重新授权");
        return new Access(plan,credentials.getUsableCredential(tenant,plan.accountId()));
    }
    private String qcId(Access access) { return access.plan().qcPlanId().orElseThrow(()->new BizException(1403,"草稿尚未发布，暂无平台数据")).value(); }
    private void page(int page) { if(page<1 || page>10000)throw new BizException(1402,"分页参数不正确"); }
    static String dimension(JsonNode row,String name) {
        JsonNode value=row.path("dimensions").path(name);
        if(value.isObject())value=value.has("Value")?value.path("Value"):value.path("value");
        return value.isNull() || value.isMissingNode()?"":value.asText();
    }
    static void verifyId(JsonNode row,String id) {
        if(!id.equals(dimension(row,"ad_id")))throw new BizException(1406,"千川报表的计划ID不匹配，请重试");
    }
    static Map<String,BigDecimal> metrics(JsonNode row,Map<String,String> fields) {
        Map<String,BigDecimal> result=new LinkedHashMap<>();
        for(String name:List.of("cost","orders","gmv","roi","netRoi","netGmv","settlementRate","refundRate","shows","clicks"))
            result.put(name,fields.containsKey(name)?QianchuanPlanMapping.number(row.path("metrics"),fields.get(name)):null);
        if(result.get("roi")==null && result.get("cost")!=null && result.get("cost").signum()>0 && result.get("gmv")!=null)
            result.put("roi",result.get("gmv").divide(result.get("cost"),4,RoundingMode.HALF_UP));
        return Collections.unmodifiableMap(result);
    }
    static String reportTime(String value,String dimension,PlanStatisticsRange range) {
        // The live uni_promotion API returns time dimensions as Unix milliseconds.
        if(value.matches("\\d{13}") || value.matches("\\d{10}")) {
            long timestamp=Long.parseLong(value);if(value.length()==10)timestamp*=1000;
            var time=Instant.ofEpochMilli(timestamp).atZone(SHANGHAI);
            if(time.toLocalDate().isBefore(range.startDate()) || time.toLocalDate().isAfter(range.endDate()))throw new BizException(1406,"千川趋势报表日期超出所选区间");
            return "stat_time_day".equals(dimension)?time.toLocalDate().toString():time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        // Single-day hourly reports may return only the hour, rather than a full timestamp.
        if("stat_time_hour".equals(dimension) && range.startDate().equals(range.endDate()) && value.matches("(?:[01]?\\d|2[0-3])(?::[0-5]\\d(?::[0-5]\\d)?)?")) {
            String[] parts=value.split(":");
            return range.startDate()+String.format(" %02d:%02d:%02d",Integer.parseInt(parts[0]),parts.length>1?Integer.parseInt(parts[1]):0,parts.length>2?Integer.parseInt(parts[2]):0);
        }
        try {
            LocalDate day=LocalDate.parse(value.length()>=10?value.substring(0,10):value);
            if(day.isBefore(range.startDate()) || day.isAfter(range.endDate()))throw new IllegalArgumentException();
            return value;
        } catch(RuntimeException ex) { throw new BizException(1406,"千川趋势报表日期不正确（"+value.substring(0,Math.min(32,value.length()))+"）"); }
    }
    public Statistics data(TenantId tenant,PlanId id,PlanStatisticsRange range,boolean refresh) {
        Access access=access(tenant,id);String qcId=qcId(access);Key key=new Key(tenant.value(),id.value(),range);
        LocalDate today = LocalDate.now(SHANGHAI);
        boolean todayOnly = today.equals(range.startDate()) && today.equals(range.endDate());
        cache.entrySet().removeIf(e->e.getValue().fetchedAt().plusSeconds(60).isBefore(Instant.now()));
        Statistics cached=cache.get(key);if(!refresh && cached!=null)return cached;
        AdManagementPort.InsightReport report;
        try { report=port.insightReport(access.credential(),qcId,range.startDate(),range.endDate()); }
        catch(BizException ex) {
            var snapshot=statistics.report(tenant,access.plan().accountId().value(),range,refresh);
            var value=todayOnly?snapshot.forPlan(qcId):snapshot.plans().get(qcId);Map<String,BigDecimal> summary=new LinkedHashMap<>();
            summary.put("cost",value==null?null:value.cost());summary.put("orders",value==null || value.orders()==null?null:BigDecimal.valueOf(value.orders()));
            summary.put("gmv",value==null?null:value.gmv());summary.put("roi",value==null?null:value.roi());
            return new Statistics(Collections.unmodifiableMap(summary),List.of(),null,snapshot.fetchedAt(),"扩展报表暂不可用："+ex.getMessage());
        }
        if(!report.summary().isEmpty())verifyId(report.summary(),qcId);
        List<Point> points=new ArrayList<>();Set<String> times=new HashSet<>();
        for(JsonNode row:report.trend()) {
            verifyId(row,qcId);String time=reportTime(dimension(row,report.timeDimension()),report.timeDimension(),range);
            if(!times.add(time))throw new BizException(1406,"千川趋势报表包含重复时间，请重试");
            points.add(new Point(time,metrics(row,report.metricFields())));
        }
        points.sort(Comparator.comparing(Point::time));
        Instant fetchedAt = Instant.now();
        Map<String,BigDecimal> summary = metrics(report.summary(),report.metricFields());
        if (todayOnly && summary.get("cost") == null) {
            // A successful filtered report may still omit the aggregate. Check the same plan in the account report.
            try {
                var snapshot = statistics.report(tenant,access.plan().accountId().value(),range,refresh);
                var cost = snapshot.forPlan(qcId).cost();
                if (cost != null && cost.signum() >= 0 && (cost.signum() > 0 || points.stream().noneMatch(PlanInsightsService::hasSpend))) {
                    var combined = new LinkedHashMap<>(summary);
                    combined.put("cost",cost);
                    summary = Collections.unmodifiableMap(combined);
                    if (snapshot.fetchedAt().isBefore(fetchedAt)) fetchedAt = snapshot.fetchedAt();
                }
            } catch (BizException ex) {
                if (ex.code() == 1201 || ex.code() == 1211 || Qianchuan401ReplayCodes.isAuthExpiredCode(ex.code())) throw ex;
                // Keep the available metrics. Spend-dependent callers will reject the missing value.
            }
        }
        Statistics result=new Statistics(summary,List.copyOf(points),report.timeDimension(),fetchedAt,report.warning(),report.metricFields());
        if(cache.size()>=256)cache.clear();cache.put(key,result);return result;
    }
    private static boolean hasSpend(Point point) {
        var cost = point.metrics().get("cost");
        return cost != null && cost.signum() > 0;
    }
    public PlanInsightsStore.Page<PlatformLog> platformLogs(TenantId tenant,PlanId id,PlanStatisticsRange range,int page) {
        page(page);Access access=access(tenant,id);String qcId=qcId(access);
        JsonNode data=port.operationLogs(access.credential(),qcId,range.startDate(),range.endDate(),page,20);
        if(!data.path("logs").isArray() || !data.path("page_info").hasNonNull("total_number"))throw new BizException(1406,"千川日志响应不完整");
        List<PlatformLog> records=new ArrayList<>();
        for(JsonNode row:data.path("logs")) {
            if(!qcId.equals(row.path("object_id").asText()))throw new BizException(1406,"千川操作日志计划ID不匹配");
            List<String> details=new ArrayList<>();row.path("content_log").forEach(v->details.add(v.asText()));
            for(JsonNode sub:row.path("sub_logs")) {
                if(sub.hasNonNull("object_id") && !qcId.equals(sub.path("object_id").asText()))continue;
                if(sub.hasNonNull("content_title"))details.add(sub.path("content_title").asText());
                sub.path("content_log").forEach(v->details.add(v.asText()));
            }
            records.add(new PlatformLog(row.path("log_id").asText(),row.path("create_time").asText(),row.path("operator_name").asText(),row.path("content_title").asText(),List.copyOf(details)));
        }
        return new PlanInsightsStore.Page<>(records,data.path("page_info").path("total_number").asLong(),page,20);
    }
    public PlanInsightsStore.Page<PlanInsightsStore.Log> localLogs(TenantId tenant,PlanId id,PlanStatisticsRange range,int page) {
        page(page);access(tenant,id);return store.logs(tenant,id.value(),range,page);
    }
    public GuaranteeView guarantee(TenantId tenant,PlanId id,PlanStatisticsRange range,int page) {
        page(page);Access access=access(tenant,id);String qcId=qcId(access);
        String status=null,reason=null,url=null,warning=null;
        try {
            JsonNode row=port.guaranteeDetail(access.credential(),qcId);
            if(row.hasNonNull("ad_id") && !qcId.equals(row.path("ad_id").asText()))throw new BizException(1406,"保障状态计划ID不匹配");
            status=row.path("compensate_status").asText(null);reason=row.path("reason").asText(null);url=row.path("url").asText(null);
            store.observeGuarantee(tenant,id.value(),status,reason,url);
        } catch(BizException ex) { warning=ex.getMessage(); }
        return new GuaranteeView(status,reason,url,Instant.now(),warning,store.guarantees(tenant,id.value(),range,page));
    }
    public DailySpending dailySpending(TenantId tenant,PlanId id,PlanStatisticsRange range) {
        Access access=access(tenant,id);String qcId=qcId(access);
        var report=port.insightReport(access.credential(),qcId,range.startDate(),range.endDate(),true);
        if(!report.summary().isEmpty())verifyId(report.summary(),qcId);
        Map<LocalDate,BigDecimal> costs=new HashMap<>();
        if("stat_time_day".equals(report.timeDimension()))for(JsonNode row:report.trend()) {
            verifyId(row,qcId);
            String value=reportTime(dimension(row,"stat_time_day"),"stat_time_day",range);
            LocalDate day=LocalDate.parse(value.substring(0,10));
            if(costs.containsKey(day))throw new BizException(1406,"千川日消耗报表包含重复日期，请重试");
            costs.put(day,metrics(row,report.metricFields()).get("cost"));
        }
        List<SpendingDay> days=new ArrayList<>();
        for(LocalDate day=range.startDate();!day.isAfter(range.endDate());day=day.plusDays(1))days.add(new SpendingDay(day,costs.get(day)));
        String warning=report.warning();
        if(!"stat_time_day".equals(report.timeDimension()))warning="平台未提供日维度消耗，暂无法展示每日明细";
        return new DailySpending(range.startDate(),range.endDate(),metrics(report.summary(),report.metricFields()).get("cost"),List.copyOf(days),Instant.now(),warning);
    }
}
