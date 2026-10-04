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
        assertConfigured();
        try {
        String body = restClient.exchange(
                RequestSpec.post(properties.getQianchuan().getDelivery().getAdStatusUrl())
                        .header("Access-Token", c.accessToken())
                        .json("{\"advertiser_id\":\""
                                + c.advertiserId().value()
                                + "\",\"ad_ids\":[\""
                                + id.value()
                                + "\"],\"opt_status\":\""
                                + action.name()
                                + "\"}")
                        .build(),
                String.class);
        oceanJson.dataOrThrow(body, 1406, "启停失败");
        } catch (BizException ex) {
            throw remapDelivery(ex, "启停失败");
        }
    }

    @Override
    public List<OceanPlanRow> list(Credential c, SmartBidType bidType) {
        assertConfigured();
        List<OceanPlanRow> merged = new ArrayList<>();
        merged.addAll(listOne(c, SmartBidType.CONSERVATIVE));
        merged.addAll(listOne(c, SmartBidType.CUSTOM));
        if (bidType == null) {
            return merged;
        }
        return merged.stream().filter(r -> r.bidType() == bidType).toList();
    }

    private List<OceanPlanRow> listOne(Credential c, SmartBidType bidType) {
        String url = properties.getQianchuan().getDelivery().getAdListUrl()
                + "?advertiser_id="
                + c.advertiserId().value()
                + "&smart_bid_type="
                + bidType.oceanValue();
        String body = restClient.exchange(
                RequestSpec.get(url).header("Access-Token", c.accessToken()).build(), String.class);
        JsonNode data = oceanJson.dataOrThrow(body, 1406, "列表失败");
        JsonNode list = data.has("list") ? data.path("list") : data.path("ad_list");
        List<OceanPlanRow> rows = new ArrayList<>();
        for (JsonNode n : list) {
            String id = n.path("ad_id").asText(n.path("id").asText(""));
            if (id.isBlank()) {
                continue;
            }
            BigDecimal budget = n.path("budget").isNumber()
                    ? n.path("budget").decimalValue()
                    : BigDecimal.ZERO;
            rows.add(new OceanPlanRow(
                    id,
                    n.path("name").asText(""),
                    n.path("status").asText("DELIVERING"),
                    SmartBidType.fromOcean(n.path("smart_bid_type").asText(bidType.oceanValue())),
                    Money.ofYuan(budget),
                    n.path("cover_url").asText(null),
                    n.path("guarantee").asText(null)));
        }
        return rows;
    }

    private static BizException remapDelivery(BizException ex, String message) {
        if (ex.code() == 1205 || ex.code() == 1211 || Qianchuan401ReplayCodes.isAuthExpired(ex)) {
            return ex;
        }
        return new BizException(1406, message);
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
