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
        return ocean.dataOrThrow(client.exchange(RequestSpec.get(url+"?"+query).header("Access-Token",c.accessToken()).build(),String.class),1406,"千川查询失败");
    }
    private JsonNode post(Credential c,String url,Map<String,Object> params) {
        Map<String,Object> body=new LinkedHashMap<>(params); body.put("advertiser_id",numericId(c.advertiserId().value()));
        try { return ocean.dataOrThrow(client.exchange(RequestSpec.post(url).header("Access-Token",c.accessToken()).json(json.writeValueAsString(body)).build(),String.class),1406,"千川操作失败"); }
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
            int total=data.path("page_info").path("total_page").asInt(0);
            if((total>0 && page>=total) || (total==0 && rows.size()<20)) return all;
            if(rows.isEmpty()) throw new BizException(1406,"千川分页响应不完整，请重新同步");
        }
        throw new BizException(1406,"计划数据过多，请分账户同步");
    }
    @Override public List<JsonNode> report(Credential c,LocalDate day) {
        return paged(c,properties.getQianchuan().getReport().getCustomUrl(),Map.of(
                "data_topic","SITE_PROMOTION_PRODUCT_AD", "dimensions",List.of("ad_id"),
                "metrics",List.of("stat_cost","total_pay_order_count_for_roi2","total_pay_order_gmv_include_coupon_for_roi2","total_prepay_and_pay_order_roi2"),
                "start_time",day+" 00:00:00", "end_time",day+" 23:59:59"),"rows");
    }
    @Override public List<JsonNode> materials(Credential c,String adId) {
        return paged(c,url("ad/material/get/"),Map.of("ad_id",numericId(adId)),"ad_material_infos");
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
        LocalDate today=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        return paged(c,url("ad/control_task/list/"),Map.of("ad_id",numericId(adId),"marketing_goal","VIDEO_PROM_GOODS",
                "start_time",today.minusDays(30)+" 00:00:00","end_time",today+" 23:59:59"),"task_list");
    }
}
