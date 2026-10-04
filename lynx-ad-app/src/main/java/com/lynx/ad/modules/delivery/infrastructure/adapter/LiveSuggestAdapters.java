package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.delivery.domain.port.BudgetSuggestion;
import com.lynx.ad.modules.delivery.domain.port.RoiSuggestion;
import com.lynx.ad.modules.delivery.domain.port.SuggestBudgetPort;
import com.lynx.ad.modules.delivery.domain.port.SuggestRoiPort;
import com.lynx.ad.modules.delivery.domain.port.UpdateBidPort;
import com.lynx.ad.modules.delivery.domain.port.UpdateRoiPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "false")
public class LiveSuggestAdapters implements SuggestBudgetPort, SuggestRoiPort, UpdateBidPort, UpdateRoiPort {

    private static final Logger log = LoggerFactory.getLogger(LiveSuggestAdapters.class);

    private final QianchuanRestClient restClient;
    private final LynxAdProperties properties;
    private final OceanJson oceanJson;

    public LiveSuggestAdapters(QianchuanRestClient restClient, LynxAdProperties properties, OceanJson oceanJson) {
        this.restClient = restClient;
        this.properties = properties;
        this.oceanJson = oceanJson;
    }

    @Override
    public List<BudgetSuggestion> suggest(Credential c, AwemeId a, Money budget) {
        String url = properties.getQianchuan().getDelivery().getSuggestBudgetUrl()
                + "?advertiser_id="
                + c.advertiserId().value()
                + "&aweme_id="
                + a.value();
        String body = restClient.exchange(
                RequestSpec.get(url).header("Access-Token", c.accessToken()).build(), String.class);
        JsonNode data = oceanJson.dataOrThrow(body, 1406, "建议预算失败");
        BigDecimal amount = data.path("suggest_budget").isNumber()
                ? data.path("suggest_budget").decimalValue()
                : (budget == null ? BigDecimal.valueOf(300) : budget.amount());
        return List.of(new BudgetSuggestion(Money.ofYuan(amount), "live"));
    }

    @Override
    public Optional<RoiSuggestion> suggest(Credential c, AwemeId a) {
        String url = properties.getQianchuan().getDelivery().getSuggestRoiUrl()
                + "?advertiser_id="
                + c.advertiserId().value()
                + "&aweme_id="
                + a.value();
        String body = restClient.exchange(
                RequestSpec.get(url).header("Access-Token", c.accessToken()).build(), String.class);
        JsonNode data = oceanJson.dataOrThrow(body, 1406, "建议 ROI 失败");
        BigDecimal roi = data.path("suggest_roi").isNumber()
                ? data.path("suggest_roi").decimalValue()
                : new BigDecimal("2.50");
        return Optional.of(new RoiSuggestion(roi, "live"));
    }

    @Override
    public void updateBid(Credential c, QcPlanId id, Money bid) {
        log.info("UpdateBidPort unused in this round");
        throw new BizException(1406, "本专项不执行调价");
    }

    @Override
    public void updateRoi(Credential c, QcPlanId id, BigDecimal roi) {
        log.info("UpdateRoiPort unused in this round");
        throw new BizException(1406, "本专项不执行调价");
    }
}
