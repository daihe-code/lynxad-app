package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="lynx.ad.mock", name="qianchuan", havingValue="false")
public class LiveAdManagementAdapter implements AdManagementPort {
    private final QianchuanRestClient client;
    private final OceanJson ocean;
    private final ObjectMapper json;
    private final LynxAdProperties properties;
    public LiveAdManagementAdapter(QianchuanRestClient client, OceanJson ocean, ObjectMapper json, LynxAdProperties properties) {
        this.client=client; this.ocean=ocean; this.json=json; this.properties=properties;
    }
    private String url(String suffix) { return properties.getQianchuan().getDelivery().getManagementBaseUrl()+suffix; }
    public static BigInteger numericId(String id) {
        if (id == null || !id.matches("[1-9][0-9]*")) throw new BizException(1402,"千川ID格式不正确");
        return new BigInteger(id);
    }
    private String encode(Object value) {
        try { return URLEncoder.encode(value instanceof String s ? s : json.writeValueAsString(value), StandardCharsets.UTF_8); }
        catch (Exception e) { throw new BizException(1402,"请求参数不正确"); }
    }
    private JsonNode get(Credential c, String url, Map<String, Object> params) {
        StringJoiner query=new StringJoiner("&"); query.add("advertiser_id="+numericId(c.advertiserId().value()));
        params.forEach((key,value)->query.add(key+"="+encode(value)));
        return ocean.dataOrThrow(client.exchange(RequestSpec.get(url+"?"+query).header("Access-Token",c.accessToken()).replayAccountId(c.accountId()).build(),String.class),1406,"千川查询失败");
    }
    private JsonNode post(Credential c,String url,Map<String,Object> params) {
        Map<String,Object> body=new LinkedHashMap<>(params); body.put("advertiser_id",numericId(c.advertiserId().value()));
        try { return ocean.dataOrThrow(client.exchange(RequestSpec.post(url).header("Access-Token",c.accessToken()).replayAccountId(c.accountId()).json(json.writeValueAsString(body)).build(),String.class),1406,"千川操作失败"); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new BizException(1402,"请求参数不正确"); }
    }
    @Override public JsonNode detail(Credential c,String adId) {
        JsonNode detail=get(c,url("ad/detail/"),Map.of("ad_id",numericId(adId)));
        if(!adId.equals(QianchuanPlanMapping.id(detail)) || !detail.path("delivery_setting").isObject())
            throw new BizException(1406,"平台计划详情响应不完整或ID不匹配");
        return detail;
    }
    @Override public Map<String,String> guarantees(Credential c,List<String> ids) {
        Map<String,String> result=new HashMap<>();
        // Conservative batches; this is a client batch size, not a claimed platform maximum.
        for(int start=0; start<ids.size(); start+=10) {
            var batch=ids.subList(start,Math.min(start+10,ids.size()));
            JsonNode data=get(c,properties.getQianchuan().getDelivery().getCompensateStatusUrl(),Map.of("ad_ids",batch.stream().map(LiveAdManagementAdapter::numericId).toList()));
            JsonNode rows=data.path("list"); if(!rows.isArray()) throw new BizException(1406,"保障状态响应不完整");
            for(JsonNode row:rows) if(row.hasNonNull("ad_id") && row.hasNonNull("compensate_status")) result.put(row.path("ad_id").asText(),row.path("compensate_status").asText());
        }
        return result;
    }
    @Override public void settings(Credential c,String adId,String name,BigDecimal budget,BigDecimal roi) {
        settings(c,adId,name,budget,roi,null);
    }
    @Override public void settings(Credential c,String adId,String name,BigDecimal budget,BigDecimal roi,String deepExternalAction) {
        if(name==null && roi!=null) {
            Map<String,Object> item=new LinkedHashMap<>();item.put("ad_id",numericId(adId));item.put("roi2_goal",roi);
            if(deepExternalAction!=null && !deepExternalAction.isBlank()) {
                if(!Set.of("AD_CONVERT_TYPE_LIVE_PAY_ROI","AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI").contains(deepExternalAction))
                    throw new BizException(1403,"平台ROI类型暂不支持修改，请先同步计划");
                item.put("deep_external_action",deepExternalAction);
            }
            if(budget!=null)settings(c,adId,null,budget,null);
            try {
                JsonNode data=post(c,properties.getQianchuan().getDelivery().getAdRoiUpdateUrl(),Map.of("update_roi2_infos",List.of(item)));
                JsonNode results=data.path("results");
                if(results.isArray()) for(JsonNode result:results) {
                    if(!adId.equals(result.path("ad_id").asText()))continue;
                    if("SUCCESS".equals(result.path("status").asText()))return;
                    if("FAILED".equals(result.path("status").asText()))
                        throw new BizException(1406,"千川ROI修改失败："+result.path("error_message").asText("平台拒绝修改"));
                }
                throw new BizException(1406,"千川未明确确认ROI修改，请同步核查，勿重复提交");
            }catch(BizException ex) {
                if(budget!=null)throw new BizException(1406,"预算已接受修改，ROI结果未确认："+ex.getMessage()+"。请同步核查，勿重复提交");
                throw ex;
            }
        }
        if(name==null && roi==null && budget!=null) {
            // Full-domain plans use the dedicated batch budget API, even for one plan.
            JsonNode data=post(c,properties.getQianchuan().getDelivery().getAdBudgetUpdateUrl(),
                    Map.of("update_budget_infos",List.of(Map.of("ad_id",numericId(adId),"budget",budget))));
            JsonNode results=data.path("results");
            if(results.isArray()) for(JsonNode result:results) {
                if(!adId.equals(result.path("ad_id").asText())) continue;
                if("SUCCESS".equals(result.path("status").asText())) return;
                if("FAILED".equals(result.path("status").asText()))
                    throw new BizException(1406,"千川预算修改失败："+result.path("error_message").asText("平台拒绝修改"));
            }
            throw new BizException(1406,"千川未明确确认预算修改，请同步核查预算，勿重复提交");
        }
        Map<String,Object> params=new LinkedHashMap<>(); params.put("ad_id",numericId(adId));
        if(name!=null) params.put("name",name);
        Map<String,Object> setting=new LinkedHashMap<>();
        if(budget!=null) setting.put("budget",budget);
        if(roi!=null) setting.put("roi2_goal",roi);
        if(!setting.isEmpty()) params.put("delivery_setting",setting);
        // PATCH-like update: never send unmodified product/creative settings.
        post(c,properties.getQianchuan().getDelivery().getAdUpdateUrl(),params);
    }
    private List<JsonNode> paged(Credential c,String url,Map<String,Object> parameters,String field) {
        List<JsonNode> all=new ArrayList<>();
        for(int page=1; page<=1000; page++) {
            Map<String,Object> params=new LinkedHashMap<>(parameters); params.put("page",page); params.put("page_size",20);
            JsonNode data=get(c,url,params), rows=data.path(field);
            if(!rows.isArray()) throw new BizException(1406,"千川分页响应缺少"+field);
            rows.forEach(all::add);
            JsonNode pagination=data.has("pagination")?data.path("pagination"):data.path("page_info");
            int total=pagination.path("total_page").asInt(0);
            if((total>0 && page>=total) || (total==0 && rows.size()<20)) return all;
            if(rows.isEmpty()) throw new BizException(1406,"千川分页响应不完整，请重新同步");
        }
        throw new BizException(1406,"计划数据过多，请分账户同步");
    }
    @Override public List<JsonNode> report(Credential c,LocalDate day) {
        return report(c,day,day);
    }
    @Override public List<JsonNode> report(Credential c,LocalDate start,LocalDate end) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);
        return paged(c,properties.getQianchuan().getReport().getCustomUrl(),Map.of(
                "data_topic","SITE_PROMOTION_PRODUCT_AD", "dimensions",List.of("ad_id"),
                "metrics",List.of("stat_cost","total_pay_order_count_for_roi2","total_pay_order_gmv_include_coupon_for_roi2","total_prepay_and_pay_order_roi2"),
                "filters",List.of(), "order_by",List.of(Map.of("type",1,"field","ad_id")),
                "start_time",start+" 00:00:00", "end_time",end+" 23:59:59"),"rows");
    }
    @Override public List<JsonNode> materials(Credential c,String adId) {
        LocalDate today=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        return materialReport(c,adId,today,today,"VIDEO");
    }
    @Override public List<JsonNode> materialReport(Credential c,String adId,LocalDate start,LocalDate end,String type) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);
        if(!Set.of("VIDEO","IMAGE","CAROUSEL","TITLE","LIVE_ROOM").contains(type))throw new BizException(1402,"素材类型不正确");
        var filtering=new LinkedHashMap<String,Object>();filtering.put("material_type",type);filtering.put("material_status","ALL");
        filtering.put("start_date",start.toString());filtering.put("end_date",end.toString());
        // The title report rejects exposure/click and settlement fields accepted for visual materials.
        var fields="TITLE".equals(type)?List.of("stat_cost_for_roi2","total_pay_order_gmv_include_coupon_for_roi2","total_prepay_and_pay_order_roi2","total_cost_per_pay_order_for_roi2"):
            List.of("product_show_count_for_roi2","product_click_count_for_roi2","product_cvr_rate_for_roi2","product_convert_rate_for_roi2",
                "stat_cost_for_roi2","total_pay_order_count_for_roi2","total_pay_order_gmv_include_coupon_for_roi2","total_prepay_and_pay_order_roi2",
                "total_cost_per_pay_order_for_roi2","total_pay_order_gmv_for_roi2","total_prepay_and_pay_settle_roi2_1h","total_order_settle_amount_for_roi2_1h",
                "total_order_settle_amount_rate_for_roi2_1h","total_refund_order_gmv_for_roi2_1h_rate");
        return paged(c,url("ad/material/get/"),Map.of("ad_id",numericId(adId),"filtering",filtering,"fields",fields),"ad_material_infos");
    }
    private String nativeUrl(String path) {
        var base=java.net.URI.create(properties.getQianchuan().getDelivery().getManagementBaseUrl());
        return base.getScheme()+"://"+base.getRawAuthority()+path;
    }
    @Override public List<JsonNode> materialMetadata(Credential c,String type,List<String> ids) {
        if(!Set.of("VIDEO","IMAGE","CAROUSEL").contains(type))return List.of();
        List<JsonNode> rows=new ArrayList<>();
        for(int start=0;start<ids.size();start+=50) rows.addAll(paged(c,nativeUrl("/open_api/v1.0/qianchuan/"+type.toLowerCase(Locale.ROOT)+"/get/"),
                Map.of("filtering",Map.of("material_ids",ids.subList(start,Math.min(start+50,ids.size())).stream().map(LiveAdManagementAdapter::numericId).toList())),"CAROUSEL".equals(type)?"carousels":"list"));
        return rows;
    }
    @Override public List<JsonNode> materialAttributes(Credential c,List<String> ids) {
        List<JsonNode> rows=new ArrayList<>();
        for(int start=0;start<ids.size();start+=50) rows.addAll(paged(c,nativeUrl("/open_api/2/file/material_attributes/list/"),
                Map.of("account_id",numericId(c.advertiserId().value()),"account_type","QIANCHUAN",
                    "filtering",Map.of("material_ids",ids.subList(start,Math.min(start+50,ids.size())).stream().map(LiveAdManagementAdapter::numericId).toList())),"materials"));
        return rows;
    }
    @Override public List<JsonNode> products(Credential c,String adId,LocalDate start,LocalDate end) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);
        return paged(c,url("ad/product/get/"),Map.of("ad_id",numericId(adId),"start_date",start.toString(),"end_date",end.toString(),
                "fields",List.of("product_show_count_for_roi2","product_click_count_for_roi2","product_cvr_rate_for_roi2",
                    "product_convert_rate_for_roi2","stat_cost_for_roi2","total_pay_order_count_for_roi2",
                    "total_pay_order_gmv_include_coupon_for_roi2","total_prepay_and_pay_order_roi2")),"product_list");
    }
    @Override public void deleteProduct(Credential c,String adId,String productId) {
        post(c,url("ad/product/delete/"),Map.of("ad_id",numericId(adId),"product_ids",List.of(numericId(productId))));
    }
    @Override public String control(Credential c,String adId,String scene,String name,BigDecimal budget,BigDecimal duration,List<String> materials) {
        Map<String,Object> body=new LinkedHashMap<>(); body.put("ad_id",numericId(adId)); body.put("scene",scene);
        body.put("name",name); body.put("budget",budget); body.put("duration",duration);
        if("MATERIAL_ADD_BUDGET".equals(scene)) {
            body.put("material_type","VIDEO"); body.put("smart_bid_type","SMART_BID_CONSERVATIVE");
            body.put("material_ids",materials.stream().map(LiveAdManagementAdapter::numericId).toList());
        }
        JsonNode data=post(c,url("ad/control_task/create/"),body);
        String id=QianchuanPlanMapping.text(data,"id");
        if(id==null || id.isBlank()) throw new BizException(1406,"平台未返回调控任务ID，请核查任务列表后再操作");
        return id;
    }
    @Override public List<JsonNode> controls(Credential c,String adId) {
        List<JsonNode> rows=new ArrayList<>();
        for(String scene:List.of("SMART_BOOST","MATERIAL_ADD_BUDGET"))rows.addAll(controls(c,adId,scene));
        return List.copyOf(rows);
    }
    @Override public List<JsonNode> controls(Credential c,String adId,String scene) {
        LocalDate today=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        return controlTasks(c,adId,scene,today.minusDays(29),today,false);
    }
    @Override public List<JsonNode> controls(Credential c,String adId,String scene,LocalDate start,LocalDate end) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);
        return controlTasks(c,adId,scene,start,end,true);
    }
    private List<JsonNode> controlTasks(Credential c,String adId,String scene,LocalDate start,LocalDate end,boolean metrics) {
        if(!Set.of("SMART_BOOST","MATERIAL_ADD_BUDGET").contains(scene))throw new BizException(1402,"调控类型不正确");
        Map<String,Object> params=new LinkedHashMap<>(Map.of("ad_id",numericId(adId),"marketing_goal","VIDEO_PROM_GOODS","scene",scene,
                "start_time",start+" 00:00:00","end_time",end+" 23:59:59"));
        if(metrics)params.put("fields",List.of("show_cnt_for_roi2_assist","click_cnt_for_roi2_assist","ctr_for_roi2_assist",
                "convert_rate_for_roi2_assist","stat_cost_for_roi2_assist","total_pay_order_count_for_roi2_assist",
                "total_pay_order_gmv_include_coupon_for_roi2_assist","total_prepay_and_pay_order_roi2_assist",
                "total_order_settle_amount_for_roi2_1h_assist","total_prepay_and_pay_settle_roi2_1h_assist",
                "total_order_settle_amount_rate_for_roi2_1h_assist","total_refund_order_gmv_for_roi2_1h_rate_assist"));
        var rows=paged(c,url("ad/control_task/list/"),params,"task_list");
        List<JsonNode> result=new ArrayList<>();
        for(JsonNode row:rows) {
            if(!row.isObject() || !row.hasNonNull("id"))throw new BizException(1406,"千川调控任务响应缺少任务ID");
            if("DELETED".equals(row.path("task_status").asText()))continue;
            var item=(com.fasterxml.jackson.databind.node.ObjectNode)row.deepCopy();
            item.put("id",row.path("id").asText());item.put("scene",scene);
            for(JsonNode material:item.path("material_list"))if(material.isObject())for(String field:List.of("material_id","aweme_item_id"))
                if(material.hasNonNull(field))((com.fasterxml.jackson.databind.node.ObjectNode)material).put(field,material.path(field).asText());
            result.add(item);
        }
        return List.copyOf(result);
    }
    @Override public InsightReport insightReport(Credential c,String adId,LocalDate start,LocalDate end) {
        return insightReport(c,adId,start,end,false);
    }
    @Override public void controlStatus(Credential c,String taskId,String operation) {
        if(!Set.of("PAUSE","ENABLE","DISABLE","DELETE").contains(operation))throw new BizException(1402,"调控操作不正确");
        JsonNode data=post(c,url("ad/control_task/status/update/"),Map.of("task_ids",List.of(numericId(taskId)),"opt_type",operation));
        for(JsonNode error:data.path("errors"))if(taskId.equals(error.path("task_id").asText()))
            throw new BizException(1406,"千川调控操作失败："+error.path("error_message").asText("平台拒绝修改"));
        for(JsonNode accepted:data.path("task_ids"))if(accepted.isIntegralNumber() && taskId.equals(accepted.asText()))return;
        throw new BizException(1406,"千川未明确确认调控操作结果，请刷新任务列表核查，勿重复提交");
    }
    @Override public InsightReport controlReport(Credential c,String adId,String taskId,String scene,LocalDate start,LocalDate end,String granularity) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);numericId(adId);numericId(taskId);
        String sceneValue=switch(scene) { case "SMART_BOOST"->"1";case "MATERIAL_ADD_BUDGET"->"2";default->throw new BizException(1402,"调控类型不正确"); };
        if(!Set.of("HOUR","DAY").contains(granularity))throw new BizException(1402,"报表时间维度不正确");
        String timeField="HOUR".equals(granularity)?"stat_time_hour":"stat_time_day";
        long days=java.time.temporal.ChronoUnit.DAYS.between(start,end)+1;
        if("HOUR".equals(granularity) && days>7)throw new BizException(1402,"分时报表区间最长7天，请改用分日报表");
        String reportUrl=properties.getQianchuan().getReport().getCustomUrl();
        String configUrl=reportUrl.replace("/uni_promotion/data/get/","/uni_promotion/config/get/").replace("/custom/get/","/custom/config/get/");
        if(configUrl.equals(reportUrl))throw new BizException(1406,"尚未配置对应报表的指标配置接口");
        String topicName="SITE_PROMOTION_PRODUCT_POST_ASSIST_TASK";JsonNode topic=null;
        for(JsonNode item:get(c,configUrl,Map.of("data_topics",List.of(topicName))).path("custom_config_datas"))if(topicName.equals(item.path("data_topic").asText()))topic=item;
        if(topic==null)throw new BizException(1406,"千川未开放调控任务数据主题");
        Map<String,JsonNode> dimensions=new HashMap<>();for(JsonNode d:topic.path("dimensions"))dimensions.put(d.path("field").asText(),d);
        if(!dimensions.containsKey(timeField) || !dimensions.containsKey("assist_aid"))throw new BizException(1406,"千川未开放任务或时间维度");
        List<Map<String,Object>> filters=new ArrayList<>();
        for(String field:List.of("ad_id","assist_aid","assist_task_scene")) {
            JsonNode d=dimensions.get(field);
            if(d==null || !d.path("filterable").asBoolean() || !d.path("filter_config").path("operator").isIntegralNumber())throw new BizException(1406,"千川未开放按计划和任务筛选");
            var config=d.path("filter_config");Map<String,Object> filter=new LinkedHashMap<>();filter.put("field",field);filter.put("operator",config.path("operator").asInt());
            filter.put("values",List.of("ad_id".equals(field)?adId:"assist_aid".equals(field)?taskId:sceneValue));
            if(config.path("type").isIntegralNumber())filter.put("type",config.path("type").asInt());filters.add(filter);
        }
        List<String> requested=List.of("show_cnt_for_roi2_assist","click_cnt_for_roi2_assist","ctr_for_roi2_assist","convert_rate_for_roi2_assist",
                "stat_cost_for_roi2_assist","total_pay_order_count_for_roi2_assist","total_pay_order_gmv_include_coupon_for_roi2_assist",
                "total_pay_order_gmv_for_roi2_assist","total_prepay_and_pay_order_roi2_assist","total_order_settle_amount_for_roi2_1h_assist",
                "total_prepay_and_pay_settle_roi2_1h_assist","total_order_settle_amount_rate_for_roi2_1h_assist","total_refund_order_gmv_for_roi2_1h_rate_assist");
        Map<String,String> fields=new LinkedHashMap<>();Set<String> excluded=new HashSet<>();
        for(JsonNode m:topic.path("metrics")) {String field=m.path("field").asText();if(requested.contains(field)) {fields.put(field,field);for(JsonNode d:m.path("exclusion_dims"))if(timeField.equals(d.asText()))excluded.add(field);}}
        if(fields.isEmpty())throw new BizException(1406,"千川未开放调控任务统计指标");
        Map<String,Object> params=new LinkedHashMap<>();params.put("data_topic",topicName);params.put("dimensions",List.of("assist_aid"));
        params.put("metrics",new ArrayList<>(fields.values()));params.put("filters",filters);params.put("order_by",List.of(Map.of("field","assist_aid","type",1)));
        params.put("start_time",start+" 00:00:00");params.put("end_time",end+" 23:59:59");
        var aggregates=paged(c,reportUrl,params,"rows");if(aggregates.size()>1)throw new BizException(1406,"千川任务汇总报表返回多条记录");
        JsonNode summary=aggregates.isEmpty()?json.createObjectNode():aggregates.getFirst();
        params.put("dimensions",List.of("assist_aid",timeField));params.put("metrics",fields.values().stream().filter(field->!excluded.contains(field)).toList());
        params.put("order_by",List.of(Map.of("field",timeField,"type",1)));
        List<JsonNode> trend=List.of();String warning=null;
        try {trend=paged(c,reportUrl,params,"rows");}catch(BizException ex){warning="任务趋势查询失败："+ex.getMessage();}
        return new InsightReport(summary,trend,timeField,Map.copyOf(fields),warning);
    }
    @Override public InsightReport insightReport(Credential c,String adId,LocalDate start,LocalDate end,boolean daily) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);numericId(adId);
        String reportUrl=properties.getQianchuan().getReport().getCustomUrl();
        String configUrl=reportUrl.replace("/uni_promotion/data/get/","/uni_promotion/config/get/").replace("/custom/get/","/custom/config/get/");
        if(configUrl.equals(reportUrl))throw new BizException(1406,"尚未配置对应报表的指标配置接口");
        JsonNode configuration=get(c,configUrl,Map.of("data_topics",List.of("SITE_PROMOTION_PRODUCT_AD")));
        JsonNode topic=null;
        for(JsonNode item:configuration.path("custom_config_datas"))if("SITE_PROMOTION_PRODUCT_AD".equals(item.path("data_topic").asText()))topic=item;
        if(topic==null)throw new BizException(1406,"千川未开放该计划的数据主题");
        Map<String,String> fields=new LinkedHashMap<>();
        fields.put("cost","stat_cost");fields.put("orders","total_pay_order_count_for_roi2");
        fields.put("gmv","total_pay_order_gmv_include_coupon_for_roi2");fields.put("roi","total_prepay_and_pay_order_roi2");
        Map<String,Set<String>> excludedTimeDimensions=new HashMap<>();
        for(JsonNode metric:topic.path("metrics")) {
            String name=metric.path("name").asText().replace(" ","");String field=metric.path("field").asText();
            if(field.isBlank())continue;
            Set<String> excluded=new HashSet<>();metric.path("exclusion_dims").forEach(v->excluded.add(v.asText()));excludedTimeDimensions.put(field,excluded);
            if(name.equals("净成交ROI"))fields.put("netRoi",field);
            else if(name.equals("净成交金额") || name.equals("净成交金额(元)") || name.equals("净成交金额（元）"))fields.put("netGmv",field);
            else if(name.equals("净成交金额结算率"))fields.put("settlementRate",field);
            else if(name.equals("1小时内退款率"))fields.put("refundRate",field);
        }
        String timeField=null;JsonNode adDimension=null;
        Set<String> dimensions=new HashSet<>();
        for(JsonNode dimension:topic.path("dimensions")) {
            String field=dimension.path("field").asText();dimensions.add(field);
            if("ad_id".equals(field))adDimension=dimension;
        }
        if(!daily && start.equals(end) && dimensions.contains("stat_time_hour"))timeField="stat_time_hour";
        else if(dimensions.contains("stat_time_day"))timeField="stat_time_day";
        if(adDimension==null)throw new BizException(1406,"千川报表未开放计划维度");
        var filterConfig=adDimension.path("filter_config");
        if(!adDimension.path("filterable").asBoolean() || !filterConfig.path("operator").isIntegralNumber())
            throw new BizException(1406,"千川报表未开放按计划筛选");
        Map<String,Object> filter=new LinkedHashMap<>();filter.put("field","ad_id");filter.put("values",List.of(adId));
        filter.put("operator",filterConfig.path("operator").asInt());
        if(filterConfig.path("type").isIntegralNumber())filter.put("type",filterConfig.path("type").asInt());
        Map<String,Object> params=new LinkedHashMap<>();params.put("data_topic","SITE_PROMOTION_PRODUCT_AD");
        params.put("dimensions",List.of("ad_id"));params.put("metrics",new ArrayList<>(new LinkedHashSet<>(fields.values())));
        params.put("filters",List.of(filter));params.put("order_by",List.of(Map.of("field","ad_id","type",1)));
        params.put("start_time",start+" 00:00:00");params.put("end_time",end+" 23:59:59");
        var aggregate=paged(c,reportUrl,params,"rows");
        if(aggregate.size()>1)throw new BizException(1406,"千川计划汇总报表返回多条记录");
        JsonNode summary=aggregate.isEmpty()?json.createObjectNode():aggregate.getFirst();
        List<JsonNode> trend=List.of();String warning=null;
        if(timeField==null)warning="当前账户未开放时间维度，暂无法查询趋势";
        else {
            params.put("dimensions",List.of("ad_id",timeField));params.put("order_by",List.of(Map.of("field",timeField,"type",1)));
            String selectedTime=timeField;
            params.put("metrics",new LinkedHashSet<>(fields.values()).stream().filter(field->!excludedTimeDimensions.getOrDefault(field,Set.of()).contains(selectedTime)).toList());
            try {trend=paged(c,reportUrl,params,"rows");}
            catch(BizException ex){warning="趋势查询失败："+ex.getMessage();}
        }
        return new InsightReport(summary,trend,timeField,Map.copyOf(fields),warning);
    }
    @Override public JsonNode operationLogs(Credential c,String adId,LocalDate start,LocalDate end,int page,int pageSize) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);
        if(page<1 || pageSize<1 || pageSize>20)throw new BizException(1402,"日志分页参数不正确");
        return get(c,properties.getQianchuan().getDelivery().getOperationLogUrl(),Map.of("object_type","AD","object_id",numericId(adId),
                "start_time",start+" 00:00:00","end_time",end+" 23:59:59","page",page,"page_size",pageSize));
    }
    @Override public JsonNode guaranteeDetail(Credential c,String adId) {
        var data=get(c,properties.getQianchuan().getDelivery().getCompensateStatusUrl(),Map.of("ad_ids",List.of(numericId(adId))));
        for(JsonNode row:data.path("list"))if(adId.equals(row.path("ad_id").asText()))return row;
        throw new BizException(1406,"千川未返回该计划的保障状态");
    }
}
