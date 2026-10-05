package com.lynx.ad.infrastructure.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LiveOauthValidityTest {
    private LiveOauthTokenAdapter adapter(String body) {
        var client = mock(QianchuanRestClient.class);
        when(client.exchange(any(), eq(String.class))).thenReturn(body);
        var properties = new LynxAdProperties();
        properties.getOauth().getQianchuan().setAppId("fixture-app");
        properties.getOauth().getQianchuan().setAppSecret("fixture-secret");
        return new LiveOauthTokenAdapter(client, properties, new OceanJson(new ObjectMapper()));
    }

    @Test
    void usesReturnedRefreshTtlIndependentlyOfAccessTtl() {
        var result = adapter("""
                {"code":0,"data":{"advertiser_id":"fixture-adv","access_token":"access",
                "refresh_token":"refresh","expires_in":3600,"refresh_token_expires_in":2592000}}
                """).refresh("old-refresh");
        assertEquals(Duration.ofSeconds(2592000 - 3600),
                Duration.between(result.accessExpiresAt(), result.refreshExpiresAt()));
    }

    @Test
    void missingRefreshTtlAndTokenDoNotInventAValidityExtension() {
        var result = adapter("""
                {"code":0,"data":{"advertiser_id":"fixture-adv","access_token":"access","expires_in":3600}}
                """).refresh("old-refresh");
        assertNull(result.refreshExpiresAt());
        assertNull(result.refreshTokenPlain());
    }
}
