package com.lynx.ad.modules.dataops.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.dataops.domain.port.ReportPullPort;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "false")
public class LiveReportPullAdapter implements ReportPullPort {

    private final QianchuanRestClient restClient;
    private final LynxAdProperties properties;
    private final OceanJson oceanJson;
    private final QianchuanCredentialFacade credentialFacade;

    public LiveReportPullAdapter(
            QianchuanRestClient restClient,
            LynxAdProperties properties,
            OceanJson oceanJson,
            QianchuanCredentialFacade credentialFacade) {
        this.restClient = restClient;
        this.properties = properties;
        this.oceanJson = oceanJson;
        this.credentialFacade = credentialFacade;
    }

    @Override
    public RawOceanReport pull(AdPlan plan) {
        if (!properties.isQianchuanLiveConfigured()) {
            throw new BizException(1205, "千川授权未配置");
        }
        Credential c = credentialFacade.get(plan.accountId());
        String url = properties.getQianchuan().getReport().getCustomUrl()
                + "?advertiser_id="
                + c.advertiserId().value()
                + "&ad_id="
                + plan.qcPlanId().map(id -> id.value()).orElse("");
        String body = restClient.exchange(
                RequestSpec.get(url).header("Access-Token", c.accessToken()).build(), String.class);
        JsonNode data = oceanJson.dataOrThrow(body, 1406, "报表失败");
        JsonNode row = data.has("list") && data.path("list").size() > 0 ? data.path("list").get(0) : data;
        long statCost = row.path("stat_cost").asLong(row.path("cost").asLong(0));
        long gmv = row.path("pay_order_amount").asLong(row.path("gmv").asLong(0));
        return new RawOceanReport(
                row.path("show_cnt").asLong(row.path("impression").asLong(0)),
                row.path("click_cnt").asLong(row.path("click").asLong(0)),
                statCost,
                row.path("convert_cnt").asLong(row.path("convert").asLong(0)),
                gmv,
                decimal(row, "roi", BigDecimal.ZERO),
                decimal(row, "ctr", BigDecimal.ZERO));
    }

    private static BigDecimal decimal(JsonNode row, String field, BigDecimal fallback) {
        return row.path(field).isNumber() ? row.path(field).decimalValue() : fallback;
    }
}
