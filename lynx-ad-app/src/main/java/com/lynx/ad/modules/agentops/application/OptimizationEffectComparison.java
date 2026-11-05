package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Compares explicit equal mature windows. A before/after change is never causal attribution. */
public final class OptimizationEffectComparison {
    private OptimizationEffectComparison(){}
    public static ObjectNode compare(JsonNode baseline,JsonNode after,MonitoringStrategy.Parameters strategy){
        var result=JsonNodeFactory.instance.objectNode().put("status","WAITING_DATA");
        result.put("attribution","OBSERVED_CHANGE").put("note","同口径成熟窗口的观察变化，不代表全部由本次调整带来");
        if(baseline.path("metric_fields").isEmpty()||!baseline.path("metric_fields").equals(after.path("metric_fields"))){return result.put("status","INCOMPARABLE").put("reason","前后指标口径发生变化");}
        var b=totals(baseline,strategy);var a=totals(after,strategy);
        if(b==null||a==null)return result.put("reason","等待完整成熟窗口及有效消耗样本");
        BigDecimal before=b[1].divide(b[0],8,RoundingMode.HALF_UP),current=a[1].divide(a[0],8,RoundingMode.HALF_UP);
        result.put("status","COMPARED").put("before_roi",before.toPlainString()).put("after_roi",current.toPlainString()).put("roi_change",current.subtract(before).toPlainString());
        result.put("before_spend",b[0].toPlainString()).put("after_spend",a[0].toPlainString()).put("before_net_revenue",b[1].toPlainString()).put("after_net_revenue",a[1].toPlainString());
        result.set("before_window",baseline);result.set("after_window",after);return result;
    }
    private static BigDecimal[] totals(JsonNode source,MonitoringStrategy.Parameters p){
        if(!source.path("complete").asBoolean()||source.path("rows").size()!=p.reviewHours())return null;
        BigDecimal spend=BigDecimal.ZERO,net=BigDecimal.ZERO;OffsetDateTime previous=null;
        try {
            for(var row:source.path("rows")){
                var start=OffsetDateTime.parse(row.path("window_start").asText());var end=OffsetDateTime.parse(row.path("window_end").asText());
                if(!row.path("complete").asBoolean()||!Duration.between(start,end).equals(Duration.ofHours(1))||previous!=null&&!previous.equals(start))return null;previous=end;
                var cost=OptimizationDiagnostics.decimal(row.path("metrics"),"spend");var revenue=OptimizationDiagnostics.decimal(row.path("metrics"),"net_revenue");
                if(cost==null||revenue==null||cost.signum()<0||revenue.signum()<0)return null;spend=spend.add(cost);net=net.add(revenue);
            }
        }catch(RuntimeException e){return null;}
        return spend.compareTo(p.minSpend())<0?null:new BigDecimal[]{spend,net};
    }
}
