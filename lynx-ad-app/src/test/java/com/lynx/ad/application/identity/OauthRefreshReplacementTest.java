package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter;
import org.junit.jupiter.api.Test;

class OauthRefreshReplacementTest {
    @Test void supersededRefreshTokenRequiresReauthorizationRatherThanEndlessTransientRetries() {
        var client=mock(com.lynx.ad.common.http.QianchuanRestClient.class);
        when(client.exchange(any(),eq(String.class))).thenReturn("{\"code\":40103,\"message\":\"refresh token superseded by reauthorization\"}");
        var adapter=new LiveOauthTokenAdapter(client,new LynxAdProperties(),new com.lynx.ad.common.http.OceanJson(new ObjectMapper()));
        var error=assertThrows(LiveOauthTokenAdapter.RemoteFailure.class,()->adapter.refreshToken("app","secret","old-refresh"));
        assertEquals(1211,error.code());assertTrue(error.permanent());assertFalse(error.getMessage().contains("secret"));
    }
}
