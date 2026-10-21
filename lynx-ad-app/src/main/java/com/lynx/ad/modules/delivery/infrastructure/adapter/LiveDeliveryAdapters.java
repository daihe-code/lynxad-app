package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.Qianchuan401ReplayCodes;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.delivery.domain.model.PlanAction;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.domain.port.AdCreatePort;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
import com.lynx.ad.modules.delivery.domain.port.AdStatusPort;
import com.lynx.ad.modules.delivery.domain.port.AdUpdatePort;
import com.lynx.ad.modules.delivery.domain.port.OceanAdCreateCommand;
import com.lynx.ad.modules.delivery.domain.port.OceanAdUpdateCommand;
import com.lynx.ad.modules.delivery.domain.port.OceanPlanRow;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "false")
public class LiveDeliveryAdapters implements AdCreatePort, AdUpdatePort, AdListPort, AdStatusPort {

    private final QianchuanRestClient restClient;
    private final LynxAdProperties properties;
    private final OceanJson oceanJson;

    public LiveDeliveryAdapters(QianchuanRestClient restClient, LynxAdProperties properties, OceanJson oceanJson) {
        this.restClient = restClient;
        this.properties = properties;
        this.oceanJson = oceanJson;
    }

    @Override
    public QcPlanId create(Credential c, OceanAdCreateCommand cmd) {
        assertConfigured();
        assertBid(cmd.smartBidType());
        try {
            String body = restClient.exchange(
                    RequestSpec.post(properties.getQianchuan().getDelivery().getAdCreateUrl())
                            .header("Access-Token", c.accessToken())
                            .json("{\"advertiser_id\":\""
                                    + c.advertiserId().value()
                                    + "\",\"name\":\""
                                    + escape(cmd.name())
                                    + "\",\"marketing_goal\":\"VIDEO_PROM_GOODS\",\"smart_bid_type\":\""
                                    + cmd.smartBidType()
                                    + "\",\"budget\":"
                                    + cmd.budget().amount()
                                    + "}")
                            .build(),
                    String.class);
            JsonNode data = oceanJson.dataOrThrow(body, 1406, "投放创建失败");
            String id = firstId(data);
            if (id == null || id.startsWith("mock_")) {
                throw new BizException(1406, "投放创建失败");
            }
            return new QcPlanId(id);
        } catch (BizException ex) {
            throw remapDelivery(ex, "投放创建失败");
        }
    }

    @Override
    public void update(Credential c, OceanAdUpdateCommand cmd) {
        assertConfigured();
        try {
        String body = restClient.exchange(
                RequestSpec.post(properties.getQianchuan().getDelivery().getAdUpdateUrl())
                        .header("Access-Token", c.accessToken())
                        .json("{\"advertiser_id\":\""
                                + c.advertiserId().value()
                                + "\",\"ad_id\":\""
                                + cmd.qcPlanId()
                                + "\",\"name\":\""
                                + escape(cmd.name())
                                + "\",\"budget\":"
                                + cmd.budget().amount()
                                + "}")
                        .build(),
                String.class);
        oceanJson.dataOrThrow(body, 1406, "投放更新失败");
        } catch (BizException ex) {
            throw remapDelivery(ex, "投放更新失败");
        }
    }

    @Override
    public void updateStatus(Credential c, QcPlanId id, PlanAction action) {
        if (action == PlanAction.FINISH) throw new BizException(1403, "结束计划尚无对应接口，不能替代为删除操作");
        String failure = action == PlanAction.DELETE ? "删除失败" : "启停失败";
        try {
        String body = restClient.exchange(
                RequestSpec.post(properties.getQianchuan().getDelivery().getAdStatusUrl())
                        .header("Access-Token", c.accessToken())
                        .json("{\"advertiser_id\":"
                                + LiveAdManagementAdapter.numericId(c.advertiserId().value())
                                + ",\"ad_ids\":["
                                + LiveAdManagementAdapter.numericId(id.value())
                                + "],\"opt_status\":\""
                                + action.name()
                                + "\"}")
                        .build(),
                String.class);
        JsonNode data = oceanJson.dataOrThrow(body, 1406, failure);
        boolean acknowledged = false;
        for (JsonNode row : data.path("results")) {
            if (!id.value().equals(row.path("ad_id").asText())) continue;
            if (!row.path("flag").isBoolean() || !row.path("flag").booleanValue()) throw new BizException(1406,
                    row.path("error").path("error_message").asText("平台拒绝更新计划状态"));
            acknowledged = true;
        }
        if (!acknowledged) throw new BizException(1406, "平台未确认计划状态，请同步核查，勿重复提交");
        } catch (BizException ex) {
            throw remapDelivery(ex, failure);
        }
    }

    @Override
    public List<OceanPlanRow> list(Credential c, SmartBidType bidType) {
        return list(c,bidType,false);
    }
    @Override
    public List<OceanPlanRow> listIncludingDeleted(Credential c, SmartBidType bidType) {
        return list(c,bidType,true);
    }
    private List<OceanPlanRow> list(Credential c, SmartBidType bidType,boolean includeDeleted) {
        if (bidType != null) return listOne(c, bidType,includeDeleted);
        List<OceanPlanRow> merged = new ArrayList<>();
        merged.addAll(listOne(c, SmartBidType.CONSERVATIVE,includeDeleted));
        merged.addAll(listOne(c, SmartBidType.CUSTOM,includeDeleted));
        return merged;
    }

    private List<OceanPlanRow> listOne(Credential c, SmartBidType bidType,boolean includeDeleted) {
        List<OceanPlanRow> rows = new ArrayList<>();
        var day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var charset=java.nio.charset.StandardCharsets.UTF_8;
        for (int page=1; page<=1000; page++) {
            String filtering = java.net.URLEncoder.encode("{\"smart_bid_type\":\""+bidType.oceanValue()+"\""+(includeDeleted?",\"status\":\"ALL_INCLUDE_DELETED\"":"")+"}",java.nio.charset.StandardCharsets.UTF_8);
            String url = properties.getQianchuan().getDelivery().getAdListUrl()+"?advertiser_id="
                    +LiveAdManagementAdapter.numericId(c.advertiserId().value())+"&marketing_goal=VIDEO_PROM_GOODS&filtering="+filtering+"&page="+page+"&page_size=20"
                    +"&start_time="+java.net.URLEncoder.encode(day+" 00:00:00",charset)
                    +"&end_time="+java.net.URLEncoder.encode(day+" 23:59:59",charset)
                    +"&fields="+java.net.URLEncoder.encode("[\"stat_cost\"]",charset);
            JsonNode data = oceanJson.dataOrThrow(restClient.exchange(
                    RequestSpec.get(url).header("Access-Token",c.accessToken()).replayAccountId(c.accountId()).build(),String.class),1406,"列表失败");
            JsonNode list=data.path("ad_list");
            if (!list.isArray()) throw new BizException(1406,"计划列表响应不完整");
            for (JsonNode payload:list) {
                JsonNode n=QianchuanPlanMapping.info(payload);
                String id=QianchuanPlanMapping.id(payload), name=QianchuanPlanMapping.text(n,"name");
                if(id==null || name==null || name.isBlank()) throw new BizException(1406,"计划列表缺少ID或名称");
                var budget=QianchuanPlanMapping.number(n,"budget");
                if(budget==null) throw new BizException(1406,"计划列表缺少日预算，请核查接口返回字段");
                rows.add(new OceanPlanRow(id,name,QianchuanPlanMapping.text(n,"status"),bidType,
                        Money.ofYuan(budget),null,null,payload));
            }
            int total=data.path("page_info").path("total_page").asInt(0);
            if((total>0 && page>=total) || (total==0 && list.size()<20)) return rows;
            if(list.isEmpty()) throw new BizException(1406,"计划分页响应不完整");
        }
        throw new BizException(1406,"计划数据过多，请分账户同步");
    }

    private static BizException remapDelivery(BizException ex, String message) {
        if (ex.code() == 1205 || ex.code() == 1211 || Qianchuan401ReplayCodes.isAuthExpired(ex)) {
            return ex;
        }
        return new BizException(1406, ex.getMessage() == null ? message : ex.getMessage());
    }

    private void assertConfigured() {
        if (!properties.isQianchuanLiveConfigured()) {
            throw new BizException(1205, "千川授权未配置");
        }
    }

    private static void assertBid(String smartBidType) {
        if (smartBidType == null) {
            throw new BizException(1406, "仅支持 SMART_BID_CONSERVATIVE / SMART_BID_CUSTOM");
        }
        String v = smartBidType.toUpperCase();
        if (!v.contains("CONSERVATIVE") && !v.contains("CUSTOM")) {
            throw new BizException(1406, "仅支持 SMART_BID_CONSERVATIVE / SMART_BID_CUSTOM");
        }
    }

    private static String firstId(JsonNode data) {
        if (data.hasNonNull("ad_id")) {
            return data.path("ad_id").asText();
        }
        if (data.hasNonNull("id")) {
            return data.path("id").asText();
        }
        JsonNode list = data.path("ad_ids");
        if (list.isArray() && list.size() > 0) {
            return list.get(0).asText();
        }
        return null;
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
