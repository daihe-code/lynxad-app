package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Select only configured metrics and required dimensions; never average daily rates into totals. */
final class LibraryMaterialReportReader {
    static final Map<String,String> FIELDS;
    static {
        var fields=new LinkedHashMap<String,String>();
        fields.put("cost","stat_cost_for_roi2");
        fields.put("ctr","product_cvr_rate_for_roi2");
        fields.put("cpm","product_ecpm_for_roi2");
        fields.put("cvr","product_convert_rate_for_roi2");
        fields.put("payment","total_pay_order_gmv_for_roi2");
        fields.put("orderCost","total_cost_per_pay_order_for_roi2");
        fields.put("roi","total_prepay_and_pay_order_roi2");
        FIELDS=Collections.unmodifiableMap(fields);
    }
    static String topic(String type) {
        return switch(type) {
            case "VIDEO" -> "SITE_PROMOTION_PRODUCT_POST_DATA_VIDEO";
            case "IMAGE" -> "SITE_PROMOTION_PRODUCT_POST_DATA_IMAGE";
            case "CAROUSEL" -> "ROI2_IMAGE_AGG_MATERIAL_ANALYSIS";
            default -> throw new BizException(1402,"此类型暂无投放数据");
        };
    }
    record Selection(List<String> dimensions,Map<String,String> fields,boolean daily) {}
    static Selection select(JsonNode topic,boolean daily) {
        var dimensions=new LinkedHashSet<String>();
        JsonNode material=null,time=null;
        for(var d:topic.path("dimensions")) {
            String field=d.path("field").asText();
            if("material_id".equals(field))material=d;
            if("stat_time_day".equals(field) && !d.path("filter_only").asBoolean())time=d;
            if(d.path("is_required").asBoolean() && !d.path("filter_only").asBoolean() && !field.isBlank())dimensions.add(field);
            if(d.path("filter_config").path("filter_required").asBoolean())throw unavailable();
        }
        if(material==null || !material.path("filterable").asBoolean() || material.path("filter_only").asBoolean())throw unavailable();
        dimensions.add("material_id");
        if(daily && time!=null)dimensions.add("stat_time_day");
        var fields=new LinkedHashMap<String,String>();
        for(var metric:topic.path("metrics")) {
            String field=metric.path("field").asText();boolean excluded=false;
            for(var dim:metric.path("exclusion_dims"))if(dimensions.contains(dim.asText()))excluded=true;
            if(!excluded)FIELDS.forEach((key,value)->{if(value.equals(field))fields.put(key,value);});
        }
        if(fields.isEmpty())throw unavailable();
        return new Selection(List.copyOf(dimensions),Collections.unmodifiableMap(fields),daily && time!=null);
    }
    static LibraryMaterialReport read(String material,LocalDate start,LocalDate end,Selection total,List<JsonNode> summary,
            Selection daily,List<JsonNode> rows) {
        var totals=new LinkedHashMap<String,BigDecimal>();FIELDS.keySet().forEach(k->totals.put(k,null));
        if(summary.size()>1)throw unavailable(); // Additional provider groupings must not be silently double-counted.
        if(!summary.isEmpty()){verify(summary.getFirst(),material);totals.putAll(metrics(summary.getFirst(),total.fields()));}
        var points=new TreeMap<LocalDate,MaterialDay>();
        for(var row:rows) {
            verify(row,material);LocalDate day;
            try { day=LocalDate.parse(value(row.path("dimensions").path("stat_time_day")).substring(0,10)); }
            catch(RuntimeException badDate){throw unavailable();}
            if(day.isBefore(start) || day.isAfter(end) || points.containsKey(day))throw unavailable();
            points.put(day,new MaterialDay(day,metrics(row,daily.fields())));
        }
        return new LibraryMaterialReport(Collections.unmodifiableMap(totals),List.copyOf(points.values()),!summary.isEmpty(),daily.daily());
    }
    private static void verify(JsonNode row,String material) {
        if(!material.equals(value(row.path("dimensions").path("material_id"))))throw unavailable();
    }
    private static String value(JsonNode node) {
        if(node.isObject())node=node.has("Value")?node.path("Value"):node.path("value");
        return node.asText("");
    }
    private static Map<String,BigDecimal> metrics(JsonNode row,Map<String,String> fields) {
        var result=new LinkedHashMap<String,BigDecimal>();FIELDS.keySet().forEach(k->result.put(k,null));
        fields.forEach((key,field)->result.put(key,QianchuanPlanMapping.number(row.path("metrics"),field)));
        return Collections.unmodifiableMap(result);
    }
    private static BizException unavailable(){return new BizException(1406,"素材投放数据暂时无法读取");}
}
