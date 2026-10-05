package com.lynx.ad.modules.identity.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.identity.domain.model.AwemeType;
import com.lynx.ad.modules.identity.domain.port.OauthTokenPort;
import com.lynx.ad.modules.identity.domain.service.AwemeCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "false")
public class LiveOauthTokenAdapter implements OauthTokenPort {

    private final QianchuanRestClient restClient;
    private final LynxAdProperties properties;
    private final OceanJson oceanJson;
    private final AwemeCatalog catalog = new AwemeCatalog();

    public LiveOauthTokenAdapter(QianchuanRestClient restClient, LynxAdProperties properties, OceanJson oceanJson) {
        this.restClient = restClient;
        this.properties = properties;
        this.oceanJson = oceanJson;
    }

    @Override
    public OauthTokenResult exchange(String authCode) {
        assertLiveConfigured();
        var qc = properties.getOauth().getQianchuan();
        String body = restClient.exchange(
                RequestSpec.post(properties.getQianchuan().getOauth().getAccessTokenUrl())
                        .json("{\"app_id\":\""
                                + qc.getAppId()
                                + "\",\"secret\":\""
                                + qc.getAppSecret()
                                + "\",\"grant_type\":\"auth_code\",\"auth_code\":\""
                                + escape(authCode)
                                + "\"}")
                        .build()
                        .withoutReplay(),
                String.class);
        JsonNode data = oceanJson.dataOrThrow(body, 1205, "换票失败");
        return toResult(data, true);
    }

    @Override
    public OauthTokenResult refresh(String refreshTokenPlain) {
        assertLiveConfigured();
        var qc = properties.getOauth().getQianchuan();
        String body = restClient.exchange(
                RequestSpec.post(properties.getQianchuan().getOauth().getRefreshTokenUrl())
                        .json("{\"app_id\":\""
                                + qc.getAppId()
                                + "\",\"secret\":\""
                                + qc.getAppSecret()
                                + "\",\"grant_type\":\"refresh_token\",\"refresh_token\":\""
                                + escape(refreshTokenPlain)
                                + "\"}")
                        .build()
                        .withoutReplay(),
                String.class);
        JsonNode data = oceanJson.dataOrThrow(body, 1211, "刷新失败");
        return toResult(data, false);
    }

    private OauthTokenResult toResult(JsonNode data, boolean loadCatalog) {
        String access = text(data, "access_token");
        String refresh = loadCatalog ? text(data, "refresh_token") : data.path("refresh_token").asText("");
        if (refresh.isBlank()) refresh = null;
        long expiresIn = data.path("expires_in").asLong(86400);
        Instant issuedAt = Instant.now();
        Instant accessExp = issuedAt.plusSeconds(Math.max(expiresIn, 60));
        long refreshExpiresIn = data.path("refresh_token_expires_in").asLong(-1);
        Instant refreshExp = refreshExpiresIn < 0 ? null : issuedAt.plusSeconds(refreshExpiresIn);
        String advertiserId = firstAdvertiser(data);
        String name = data.path("advertiser_name").asText("千川账户");
        List<AwemeDraft> awemes = loadCatalog ? loadAwemes(access, advertiserId) : List.of();
        if (advertiserId.isBlank()) {
            throw new BizException(1205, "换票失败");
        }
        return new OauthTokenResult(advertiserId, name, access, refresh, accessExp, refreshExp, awemes);
    }

    private List<AwemeDraft> loadAwemes(String accessToken, String advertiserId) {
        try {
            String url = properties.getQianchuan().getOauth().getAwemeAuthorizedUrl()
                    + "?advertiser_id="
                    + advertiserId;
            String body = restClient.exchange(
                    RequestSpec.get(url).header("Access-Token", accessToken).build().withoutReplay(),
                    String.class);
            JsonNode data = oceanJson.dataOrThrow(body, 1205, "抖音号列表失败");
            List<AwemeDraft> out = new ArrayList<>();
            JsonNode list = data.has("list") ? data.path("list") : data.path("aweme_id_list");
            int i = 0;
            for (JsonNode n : list) {
                String id = n.path("aweme_id").asText(n.path("id").asText("aweme_live_" + i));
                String name = n.path("aweme_name").asText(n.path("name").asText("抖音号"));
                AwemeType type = i == 0 ? AwemeType.OFFICIAL : AwemeType.SELF;
                out.add(new AwemeDraft(id, name, null, type, i == 0, catalog.sceneHint(type)));
                i++;
            }
            if (out.isEmpty()) {
                out.add(new AwemeDraft("aweme_live", "授权抖音号", null, AwemeType.OFFICIAL, true, catalog.sceneHint(AwemeType.OFFICIAL)));
            }
            return out;
        } catch (BizException ex) {
            return List.of(new AwemeDraft(
                    "aweme_live", "授权抖音号", null, AwemeType.OFFICIAL, true, catalog.sceneHint(AwemeType.OFFICIAL)));
        }
    }

    private String firstAdvertiser(JsonNode data) {
        if (data.hasNonNull("advertiser_id")) {
            return data.path("advertiser_id").asText();
        }
        JsonNode ids = data.path("advertiser_ids");
        if (ids.isArray() && ids.size() > 0) {
            return ids.get(0).asText();
        }
        return "";
    }

    private void assertLiveConfigured() {
        if (!properties.isQianchuanLiveConfigured()) {
            throw new BizException(1205, "千川授权未配置");
        }
    }

    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText("");
        if (v.isBlank()) {
            throw new BizException(1205, "换票失败");
        }
        return v;
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
