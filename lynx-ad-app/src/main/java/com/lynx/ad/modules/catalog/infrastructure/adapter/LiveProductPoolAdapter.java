package com.lynx.ad.modules.catalog.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.catalog.domain.port.ProductPoolPort;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
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
public class LiveProductPoolAdapter implements ProductPoolPort {

    private final QianchuanRestClient restClient;
    private final LynxAdProperties properties;
    private final OceanJson oceanJson;
    private final QianchuanCredentialFacade credentialFacade;

    public LiveProductPoolAdapter(
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
    public List<PoolItem> list(AccountId accountId, String awemeId) {
        try {
            Credential c = credentialFacade.get(accountId);
            String url = properties.getQianchuan().getCatalog().getProductGetUrl()
                    + "?advertiser_id="
                    + c.advertiserId().value()
                    + "&aweme_id="
                    + (awemeId == null ? "" : awemeId);
            String body = restClient.exchange(
                    RequestSpec.get(url).header("Access-Token", c.accessToken()).build(), String.class);
            JsonNode data = oceanJson.dataOrThrow(body, 1306, "商品池查询失败");
            JsonNode list = data.has("list") ? data.path("list") : data.path("product_list");
            List<PoolItem> items = new ArrayList<>();
            for (JsonNode n : list) {
                String id = n.path("product_id").asText(n.path("id").asText(""));
                if (id.isBlank() || id.startsWith("qp0")) {
                    continue;
                }
                BigDecimal price = n.path("price").isNumber() ? n.path("price").decimalValue() : BigDecimal.ZERO;
                items.add(new PoolItem(
                        new QcProductId(id),
                        n.path("name").asText(id),
                        Money.ofYuan(price),
                        n.path("image_url").asText(n.path("image").asText(null))));
            }
            return items;
        } catch (BizException ex) {
            if (ex.code() == 1306 || ex.code() == 1201 || ex.code() == 1205) {
                throw ex.code() == 1201 || ex.code() == 1205 ? new BizException(1306, "商品池查询失败") : ex;
            }
            throw new BizException(1306, "商品池查询失败");
        } catch (RuntimeException ex) {
            throw new BizException(1306, "商品池查询失败");
        }
    }
}
