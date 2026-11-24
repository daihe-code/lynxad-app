package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanRemoteStore;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferRebuildStore.ArchivedReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

final class TransferReportTotals {
    private static final Map<String,String> PAYMENT=Map.of("cost","stat_cost","orders","total_pay_order_count_for_roi2",
            "gmv","total_pay_order_gmv_include_coupon_for_roi2","roi","total_prepay_and_pay_order_roi2");
    private static final List<String> ADDITIVE=List.of("cost","orders","gmv","netGmv","shows","clicks");
    record Totals(Map<String,BigDecimal> summary, List<PlanInsightsService.Point> days, boolean partial) {}

    private TransferReportTotals() {}

    static Totals archived(List<ArchivedReport> instances,String currentId,PlanStatisticsRange range) {
        List<Map<String,BigDecimal>> totals=new ArrayList<>();
        Map<String,List<Map<String,BigDecimal>>> days=new TreeMap<>();
        boolean partial=false;
        for(var instance:instances) {
            if(instance.qcPlanId().equals(currentId))continue;
            var snapshot=instance.snapshot();
            var history=snapshot.path("historyReport");
            LocalDate start=day(history.path("start").asText()),end=day(history.path("end").asText());
            boolean datesKnown=start!=null && end!=null;
            if(datesKnown && (start.isAfter(range.endDate())||end.isBefore(range.startDate())))continue;
            var report=history.path("report");
            Map<String,String> fields=new HashMap<>(PAYMENT);
            report.path("metricFields").properties().forEach(e->fields.put(e.getKey(),e.getValue().asText()));
            var instanceDays=new TreeMap<LocalDate,Map<String,BigDecimal>>();
            if(datesKnown && "stat_time_day".equals(report.path("timeDimension").asText())) {
                var reportRange=new PlanStatisticsRange(start,end);
                for(var row:report.path("trend")) {
                    if(!instance.qcPlanId().equals(PlanInsightsService.dimension(row,"ad_id"))) { partial=true; continue; }
                    try {
                        var date=day(PlanInsightsService.reportTime(PlanInsightsService.dimension(row,"stat_time_day"),"stat_time_day",reportRange));
                        if(date!=null && inRange(date,range))instanceDays.putIfAbsent(date,PlanInsightsService.metrics(row,fields));
                    } catch(BizException invalid) { partial=true; }
                }
            }
            var summary=report.path("summary");
            boolean full=datesKnown && !start.isBefore(range.startDate()) && !end.isAfter(range.endDate())
                    && instance.qcPlanId().equals(PlanInsightsService.dimension(summary,"ad_id")) && PlanRemoteStore.validReport(summary.path("metrics"));
            if(full) {
                totals.add(PlanInsightsService.metrics(summary,fields));
                if(instanceDays.isEmpty() && start.equals(end))instanceDays.put(start,totals.getLast());
            } else {
                var cached=snapshot.path("metadata");
                var reportDay=day(snapshot.path("reportDate").asText());
                addCached(instanceDays,range,day(snapshot.path("lastReportDate").asText()),cached.path("lastReport"));
                addCached(instanceDays,range,reportDay,cached.path("todayReport"));
                if(reportDay!=null)addCached(instanceDays,range,reportDay.minusDays(1),cached.path("yesterdayReport"));
                totals.addAll(instanceDays.values());
                partial|=!datesKnown || !"stat_time_day".equals(report.path("timeDimension").asText())
                        || report.path("warning").isTextual();
            }
            instanceDays.forEach((date,values)->days.computeIfAbsent(date.toString(),ignored->new ArrayList<>()).add(values));
        }
        return new Totals(combine(totals),days.entrySet().stream().map(e->new PlanInsightsService.Point(e.getKey(),combine(e.getValue()))).toList(),partial);
    }

    static Map<String,BigDecimal> combine(List<Map<String,BigDecimal>> rows) {
        Map<String,BigDecimal> result=new LinkedHashMap<>();
        for(String key:ADDITIVE) {
            if(!Set.of("cost","orders","gmv").contains(key) && rows.stream().anyMatch(row->row.get(key)==null))continue;
            for(var row:rows)if(row.get(key)!=null)result.merge(key,row.get(key),BigDecimal::add);
        }
        ratio(result,"roi","gmv",rows);
        ratio(result,"netRoi","netGmv",rows);
        return Collections.unmodifiableMap(result);
    }

    private static void ratio(Map<String,BigDecimal> total,String key,String amount,List<Map<String,BigDecimal>> rows) {
        var cost=total.get("cost");var value=total.get(amount);
        if(cost!=null && cost.signum()>0 && value!=null && rows.stream().allMatch(row->row.get("cost")!=null && row.get(amount)!=null))
            total.put(key,value.divide(cost,4,RoundingMode.HALF_UP));
        else if(rows.size()==1 && rows.getFirst().get(key)!=null)total.put(key,rows.getFirst().get(key));
    }
    private static void addCached(Map<LocalDate,Map<String,BigDecimal>> days,PlanStatisticsRange range,LocalDate day,JsonNode values) {
        if(day!=null && inRange(day,range) && PlanRemoteStore.validReport(values)) {
            Map<String,BigDecimal> metrics=new HashMap<>();
            PAYMENT.forEach((key,field)->metrics.put(key,com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping.number(values,field)));
            days.putIfAbsent(day,metrics);
        }
    }
    private static boolean inRange(LocalDate day,PlanStatisticsRange range) {
        return !day.isBefore(range.startDate()) && !day.isAfter(range.endDate());
    }
    private static LocalDate day(String date) {
        try { return LocalDate.parse(date); }
        catch(java.time.format.DateTimeParseException invalid) { return null; }
    }
}
