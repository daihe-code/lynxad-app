package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.domain.model.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** The overview uses the same filtered plan scope as the list, before pagination. */
@Service
public class CampaignTrendService {
    public record Statistics(List<PlanInsightsService.Point> points,String timeDimension,PlanStatisticsRange statisticsRange,
                             PlanMetricTemplate metricTemplate,Instant fetchedAt,String warning) {}
    private final PlanQueryService queries;
    private final PlanStatisticsService statistics;
    public CampaignTrendService(PlanQueryService queries,PlanStatisticsService statistics) {this.queries=queries;this.statistics=statistics;}
    public Statistics data(TenantId tenant,PlanQueryService.Filters filter) {
        var range=filter.statisticsRange();
        if(range==null){var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));range=new PlanStatisticsRange(today,today);}
        var template=filter.metricTemplate()==null?PlanMetricTemplate.PAYMENT:filter.metricTemplate();
        boolean daily=!range.startDate().equals(range.endDate());
        var scope=queries.statisticsScope(tenant,filter);
        Map<String,PlanStatisticsService.TrendSnapshot> reports=new LinkedHashMap<>();
        for(String account:scope.keySet())reports.put(account,statistics.trend(tenant,account,range,template,daily,filter.refreshStatistics()));
        // Mixed account capabilities must not mix one account's daily total with another's hours.
        if(!daily && reports.values().stream().anyMatch(r->"stat_time_day".equals(r.timeDimension()))) {
            daily=true;
            for(String account:scope.keySet())if("stat_time_hour".equals(reports.get(account).timeDimension()))
                reports.put(account,statistics.trend(tenant,account,range,template,true,filter.refreshStatistics()));
        }
        String dimension=daily?"stat_time_day":"stat_time_hour";
        if(reports.values().stream().anyMatch(r->r.timeDimension()!=null && !dimension.equals(r.timeDimension())))
            throw new com.lynx.ad.common.exception.BizException(1406,"平台趋势的时间维度不一致，请重试");
        var warnings=new LinkedHashSet<String>();
        reports.values().stream().map(PlanStatisticsService.TrendSnapshot::warning).filter(Objects::nonNull).forEach(warnings::add);
        boolean available=reports.values().stream().allMatch(r->r.timeDimension()!=null);
        Map<String,Map<String,List<PlanStatisticsService.Metrics>>> buckets=new LinkedHashMap<>();
        if(available)for(var entry:reports.entrySet())for(var point:entry.getValue().points())
            if(scope.get(entry.getKey()).contains(point.planId()))buckets.computeIfAbsent(entry.getKey(),key->new TreeMap<>())
                    .computeIfAbsent(point.time(),key->new ArrayList<>()).add(point.metrics());
        List<PlanInsightsService.Point> points=new ArrayList<>();
        var now=ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        for(LocalDate date=range.startDate();!date.isAfter(range.endDate());date=date.plusDays(1)) {
            int count=daily?1:date.equals(now.toLocalDate())?now.getHour()+1:24;
            for(int hour=0;hour<count;hour++) {
                String time=daily?date.toString():date+String.format(" %02d:00:00",hour);
                List<PlanStatisticsService.Metrics> values=new ArrayList<>();
                if(available)for(var entry:reports.entrySet()) {
                    var rows=buckets.getOrDefault(entry.getKey(),Map.of()).getOrDefault(time,List.of());
                    // Completed sparse reports omit inactive buckets. Unsupported/missing metrics remain null.
                    if(rows.isEmpty())values.add(PlanStatisticsService.noActivity(entry.getValue().metricFields()));
                    else values.addAll(rows);
                }
                points.add(new PlanInsightsService.Point(time,aggregate(values)));
            }
        }
        Instant fetchedAt=reports.values().stream().map(PlanStatisticsService.TrendSnapshot::fetchedAt).min(Comparator.naturalOrder()).orElse(Instant.now());
        return new Statistics(List.copyOf(points),dimension,range,template,fetchedAt,warnings.isEmpty()?null:String.join("；",warnings));
    }
    static Map<String,BigDecimal> aggregate(List<PlanStatisticsService.Metrics> rows) {
        var cost=sum(rows,PlanStatisticsService.Metrics::cost);var gmv=sum(rows,PlanStatisticsService.Metrics::gmv);
        var net=sum(rows,PlanStatisticsService.Metrics::netGmv);
        Map<String,BigDecimal> result=new LinkedHashMap<>();
        result.put("cost",cost);result.put("orders",sum(rows,r->r.orders()==null?null:BigDecimal.valueOf(r.orders())));
        result.put("gmv",gmv);result.put("roi",ratio(gmv,cost,rows,PlanStatisticsService.Metrics::roi));result.put("netGmv",net);result.put("netRoi",ratio(net,cost,rows,PlanStatisticsService.Metrics::netRoi));
        BigDecimal rate=null;
        if(gmv!=null && gmv.signum()>0 && rows.stream().filter(r->r.gmv().signum()>0).allMatch(r->r.refundRate()!=null))
            rate=rows.stream().filter(r->r.gmv().signum()>0).map(r->r.gmv().multiply(r.refundRate())).reduce(BigDecimal.ZERO,BigDecimal::add).divide(gmv,4,RoundingMode.HALF_UP);
        else if(allZero(rows,PlanStatisticsService.Metrics::refundRate))rate=BigDecimal.ZERO;
        result.put("refundRate",rate);return Collections.unmodifiableMap(result);
    }
    private static BigDecimal sum(List<PlanStatisticsService.Metrics> rows,Function<PlanStatisticsService.Metrics,BigDecimal> field) {
        if(rows.isEmpty() || rows.stream().anyMatch(r->field.apply(r)==null))return null;
        return rows.stream().map(field).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private static boolean allZero(List<PlanStatisticsService.Metrics> rows,Function<PlanStatisticsService.Metrics,BigDecimal> field) {
        return !rows.isEmpty() && rows.stream().allMatch(r->field.apply(r)!=null && field.apply(r).signum()==0);
    }
    private static BigDecimal ratio(BigDecimal value,BigDecimal cost,List<PlanStatisticsService.Metrics> rows,Function<PlanStatisticsService.Metrics,BigDecimal> field) {
        return value!=null && cost!=null && cost.signum()>0?value.divide(cost,4,RoundingMode.HALF_UP):allZero(rows,field)?BigDecimal.ZERO:null;
    }
}
