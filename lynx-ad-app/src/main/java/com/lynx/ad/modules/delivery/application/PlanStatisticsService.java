package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import com.lynx.ad.modules.delivery.domain.model.PlanMetricTemplate;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PlanStatisticsService {
    public record Metrics(BigDecimal cost,BigDecimal gmv,BigDecimal roi,Long orders,
                          BigDecimal netGmv,BigDecimal netRoi,BigDecimal refundRate) {
        public Metrics(BigDecimal cost,BigDecimal gmv,BigDecimal roi,Long orders) {this(cost,gmv,roi,orders,null,null,null);}
    }
    private static final Set<String> PAYMENT_FIELDS=Set.of("cost","gmv","roi","orders");
    public record Snapshot(Map<String,Metrics> plans,Instant fetchedAt,Set<String> metricFields) {
        public Snapshot(Map<String,Metrics> plans,Instant fetchedAt) {this(plans,fetchedAt,knownFields(plans.values()));}
        /** A complete successful report omits plans with no activity; a present row may still lack a metric. */
        public Metrics forPlan(String id) {return plans.getOrDefault(id,noActivity(metricFields));}
    }
    public record TrendPoint(String planId,String time,Metrics metrics) {}
    public record TrendSnapshot(List<TrendPoint> points,String timeDimension,Instant fetchedAt,String warning,Set<String> metricFields) {
        public TrendSnapshot(List<TrendPoint> points,String timeDimension,Instant fetchedAt,String warning) {
            this(points,timeDimension,fetchedAt,warning,knownFields(points.stream().map(TrendPoint::metrics).toList()));
        }
    }
    static Metrics noActivity(Set<String> fields) {
        return new Metrics(zero(fields,"cost"),zero(fields,"gmv"),zero(fields,"roi"),fields.contains("orders")?0L:null,
                zero(fields,"netGmv"),zero(fields,"netRoi"),zero(fields,"refundRate"));
    }
    private static BigDecimal zero(Set<String> fields,String field) {return fields.contains(field)?BigDecimal.ZERO:null;}
    private static Set<String> knownFields(Collection<Metrics> rows) {
        var fields=new HashSet<>(PAYMENT_FIELDS);
        if(rows.stream().anyMatch(m->m.netGmv()!=null))fields.add("netGmv");
        if(rows.stream().anyMatch(m->m.netRoi()!=null))fields.add("netRoi");
        if(rows.stream().anyMatch(m->m.refundRate()!=null))fields.add("refundRate");
        return Set.copyOf(fields);
    }
    private record Key(String tenant,String account,String advertiser,PlanStatisticsRange range,PlanMetricTemplate template) {}
    private record TrendKey(Key scope,boolean daily) {}
    private final Map<Key,Snapshot> cache=new LinkedHashMap<>();
    private final Map<TrendKey,TrendSnapshot> trendCache=new LinkedHashMap<>();
    private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;
    private final AdManagementPort management;
    public PlanStatisticsService(OauthAccountRepository accounts,QianchuanCredentialFacade credentials,AdManagementPort management) {
        this.accounts=accounts;this.credentials=credentials;this.management=management;
    }
    /** Short-lived account/range cache keeps pagination and exports on the same report. */
    public synchronized Snapshot report(TenantId tenant,String accountId,PlanStatisticsRange range,boolean refresh) {
        return report(tenant,accountId,range,refresh,PlanMetricTemplate.PAYMENT);
    }
    public synchronized Snapshot report(TenantId tenant,String accountId,PlanStatisticsRange range,boolean refresh,PlanMetricTemplate template) {
        var credential=credential(tenant,accountId);
        Key key=new Key(tenant.value(),accountId,credential.advertiserId().value(),range,template);
        Instant now=Instant.now();
        cache.entrySet().removeIf(e->e.getValue().fetchedAt().plusSeconds(60).isBefore(now));
        Snapshot cached=cache.get(key);
        if(!refresh && cached!=null)return cached;
        Map<String,Metrics> values=new HashMap<>();
        var report=template==PlanMetricTemplate.NET?management.netReport(credential,range.startDate(),range.endDate()):null;
        var fields=report==null?Map.<String,String>of():report.metricFields();
        for(JsonNode row:report==null?management.report(credential,range.startDate(),range.endDate()):report.rows()) {
            String ad=PlanInsightsService.dimension(row,"ad_id");
            if(ad.isBlank())throw new BizException(1406,"千川区间报表缺少计划ID，请重试");
            if(values.putIfAbsent(ad,metrics(row.path("metrics"),fields))!=null)
                throw new BizException(1406,"千川区间报表包含重复计划，请重试");
        }
        var availableFields=new HashSet<>(PAYMENT_FIELDS);availableFields.addAll(fields.keySet());
        Snapshot result=new Snapshot(Map.copyOf(values),Instant.now(),Set.copyOf(availableFields));
        if(cache.size()>=256)cache.remove(cache.keySet().iterator().next());
        cache.put(key,result);return result;
    }
    private com.lynx.ad.modules.identity.domain.model.Credential credential(TenantId tenant,String accountId) {
        var id=new AccountId(accountId);
        var account=accounts.findById(tenant,id).orElseThrow(()->new BizException(1201,"授权账户不存在或不属于当前灵投账户"));
        if(account.platform()!=Platform.QIANCHUAN || !"ACTIVE".equals(account.status())) throw new BizException(1201,"该广告账户授权已失效，无法查询区间统计");
        return credentials.getUsableCredential(tenant,id);
    }
    public TrendSnapshot trend(TenantId tenant,String accountId,PlanStatisticsRange range,PlanMetricTemplate template,boolean daily,boolean refresh) {
        // A slow trend query must not hold the monitor used by ordinary list reports.
        synchronized(trendCache) { return cachedTrend(tenant,accountId,range,template,daily,refresh); }
    }
    private TrendSnapshot cachedTrend(TenantId tenant,String accountId,PlanStatisticsRange range,PlanMetricTemplate template,boolean daily,boolean refresh) {
        var credential=credential(tenant,accountId);
        var key=new TrendKey(new Key(tenant.value(),accountId,credential.advertiserId().value(),range,template),daily);
        trendCache.entrySet().removeIf(e->e.getValue().fetchedAt().plusSeconds(60).isBefore(Instant.now()));
        var cached=trendCache.get(key);if(!refresh && cached!=null)return cached;
        var report=management.planTrendReport(credential,range.startDate(),range.endDate(),template==PlanMetricTemplate.NET,daily);
        if(report.timeDimension()!=null && !Set.of("stat_time_hour","stat_time_day").contains(report.timeDimension()))throw new BizException(1406,"千川趋势报表的时间维度不正确");
        List<TrendPoint> points=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(JsonNode row:report.rows()) {
            String ad=PlanInsightsService.dimension(row,"ad_id");
            if(ad.isBlank() || report.timeDimension()==null)throw new BizException(1406,"千川趋势报表缺少计划或时间维度");
            String time=PlanInsightsService.reportTime(PlanInsightsService.dimension(row,report.timeDimension()),report.timeDimension(),range);
            try {
                time="stat_time_day".equals(report.timeDimension())?LocalDate.parse(time.substring(0,10)).toString():
                        LocalDateTime.parse(time.replace(' ','T')).withMinute(0).withSecond(0).withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch(RuntimeException invalid){throw new BizException(1406,"千川趋势报表的时间不正确");}
            if(!seen.add(ad+"/"+time))throw new BizException(1406,"千川趋势报表包含重复计划和时间，请重试");
            points.add(new TrendPoint(ad,time,metrics(row.path("metrics"),report.metricFields())));
        }
        var result=new TrendSnapshot(List.copyOf(points),report.timeDimension(),Instant.now(),report.warning(),Set.copyOf(report.metricFields().keySet()));
        if(trendCache.size()>=32)trendCache.remove(trendCache.keySet().iterator().next());
        trendCache.put(key,result);return result;
    }
    private static Metrics metrics(JsonNode m,Map<String,String> fields) {
        BigDecimal cost=QianchuanPlanMapping.number(m,fields.getOrDefault("cost","stat_cost")),gmv=QianchuanPlanMapping.number(m,fields.getOrDefault("gmv","total_pay_order_gmv_include_coupon_for_roi2"));
        BigDecimal orders=QianchuanPlanMapping.number(m,fields.getOrDefault("orders","total_pay_order_count_for_roi2")),roi=QianchuanPlanMapping.number(m,fields.getOrDefault("roi","total_prepay_and_pay_order_roi2"));
        if(roi==null && cost!=null && cost.signum()>0 && gmv!=null)roi=gmv.divide(cost,4,RoundingMode.HALF_UP);
        return new Metrics(cost,gmv,roi,orders==null?null:orders.longValue(),metric(m,fields,"netGmv"),metric(m,fields,"netRoi"),metric(m,fields,"refundRate"));
    }
    private static BigDecimal metric(JsonNode values,Map<String,String> fields,String key) {
        if(!fields.containsKey(key))return null;
        if("refundRate".equals(key)) {
            JsonNode value=values.path(fields.get(key));
            if(value.isObject())value=value.has("Value")?value.path("Value"):value.path("value");
            String text=value.asText().trim();
            if(text.endsWith("%"))try{return new BigDecimal(text.substring(0,text.length()-1).trim());}catch(NumberFormatException ignored){return null;}
        }
        return QianchuanPlanMapping.number(values,fields.get(key));
    }
}
