package com.lynx.ad.infrastructure.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter;
import java.util.*;
import org.junit.jupiter.api.Test;

class QianchuanOAuthContractTest {
    private final ObjectMapper json=new ObjectMapper();
    private final LynxAdProperties properties=new LynxAdProperties();
    private final QianchuanRestClient client=mock(QianchuanRestClient.class);
    private final LiveOauthTokenAdapter adapter=new LiveOauthTokenAdapter(client,properties,new OceanJson(json));
    @Test void exchangeUsesStructuredJsonAndDoesNotRequireAdvertiserInTokenResponse() throws Exception {
        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec req=call.getArgument(0); var body=json.readTree(req.body());
            assertEquals("app-1",body.path("app_id").asText()); assertEquals("secret-quoted-\"",body.path("secret").asText());
            assertEquals("code-\"\\",body.path("auth_code").asText()); assertNull(req.replayAccountId());
            return "{\"code\":0,\"data\":{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":3600}}";
        });
        var bundle=adapter.exchangeToken("app-1","secret-quoted-\"","code-\"\\");
        assertEquals("access",bundle.accessToken()); assertNull(bundle.refreshExpiresAt());
        assertFalse(bundle.toString().contains("refresh"));
    }
    @Test void refreshResponseCanContainOnlyTokens() {
        when(client.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"access_token\":\"new-access\",\"expires_in\":3600}}");
        var token=adapter.refreshToken("app","secret","old-refresh"); assertNull(token.refreshToken()); assertEquals("new-access",token.accessToken());
    }
    @Test void missingAccessValidityIsRejected() {
        when(client.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"access_token\":\"access\",\"refresh_token\":\"refresh\"}}");
        assertThrows(BizException.class,()->adapter.exchangeToken("app","secret","code"));
    }
    @Test void expandsEveryOrganizationAndPageWithoutUsingParentIdsAsAdvertisers() {
        List<String> paths=new ArrayList<>();
        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec req=call.getArgument(0); String url=req.url(); paths.add(url); assertEquals("access",req.headers().get("Access-Token"));
            if(url.equals(properties.getQianchuan().getOauth().getAdvertiserGetUrl())) return """
                {"code":0,"data":{"list":[
                  {"account_id":11,"account_role":"PLATFORM_ROLE_ENTERPRISE_BP_ADMIN"},
                  {"account_id":12,"account_role":"PLATFORM_ROLE_ENTERPRISE_BP_ADMIN"},
                  {"account_id":13,"account_role":"PLATFORM_ROLE_SHOP_ACCOUNT"},
                  {"account_id":14,"account_role":"CUSTOMER_ADMIN"},
                  {"account_id":"999999999999999999","account_name":"direct","account_role":"ADVERTISER","account_type":"QIANCHUAN"},
                  {"account_id":15,"account_role":"ADVERTISER","account_type":"AD"}]}}
                """;
            if(url.contains("ebp/")) {
                if(url.contains("enterprise_organization_id=11") && url.contains("page=1&")) return "{\"code\":0,\"data\":{\"account_list\":[{\"account_id\":101,\"account_type\":\"QIANCHUAN\"}],\"page_info\":{\"total_page\":2}}}";
                return "{\"code\":0,\"data\":{\"account_list\":[{\"account_id\":102,\"account_type\":\"QIANCHUAN\"}],\"page_info\":{\"total_page\":1}}}";
            }
            if(url.contains("shop/")) return "{\"code\":0,\"data\":{\"list\":[{\"advertiser_id\":103,\"advertiser_name\":\"shop\"}]}}";
            return "{\"code\":0,\"data\":{\"list\":[{\"account_id\":104,\"account_type\":\"QIANCHUAN\"}]}}";
        });
        var accounts=adapter.discover("access"); assertEquals(Set.of("101","102","103","104","999999999999999999"),new HashSet<>(accounts.stream().map(a->a.advertiserId()).toList()));
        assertTrue(paths.stream().anyMatch(u->u.contains("enterprise_organization_id=12"))); assertTrue(paths.stream().anyMatch(u->u.contains("page=2")));
    }
    @Test void emptyAwemesDoNotBecomeFakeAccounts() {
        when(client.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"aweme_id_list\":[]}}");
        assertTrue(adapter.loadAwemes("access","1").isEmpty());
    }
    @Test void awemeFailureIsVisibleAndDoesNotBecomeFakeAccounts() {
        when(client.exchange(any(),eq(String.class))).thenThrow(new BizException(9999,"network"));
        assertThrows(BizException.class,()->adapter.loadAwemes("access","1"));
    }
    @Test void awemeUsesReturnedIdentityAvatarAndDoesNotInferOfficialFromPosition() {
        when(client.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"aweme_id_list\":[{\"aweme_id\":77,\"aweme_name\":\"shop\",\"aweme_avatar\":\"https://example.com/avatar.png\"}]}}");
        var a=adapter.loadAwemes("access","1").getFirst(); assertEquals("77",a.awemeId()); assertEquals("AUTHORIZED",a.type().name()); assertFalse(a.primary()); assertEquals("https://example.com/avatar.png",a.avatar());
    }
    @Test void permanentRefreshRejectionIsDifferentFromTemporaryPlatformFailure() {
        when(client.exchange(any(),eq(String.class))).thenReturn("{\"code\":40105,\"message\":\"expired\"}");
        assertTrue(assertThrows(LiveOauthTokenAdapter.RemoteFailure.class,()->adapter.refreshToken("app","secret","refresh")).permanent());
        when(client.exchange(any(),eq(String.class))).thenReturn("{\"code\":50001,\"message\":\"temporary\"}");
        assertFalse(assertThrows(LiveOauthTokenAdapter.RemoteFailure.class,()->adapter.refreshToken("app","secret","refresh")).permanent());
    }
    @Test void authRejectionInterceptedByHttpClientStillInvalidatesRefreshGrant() {
        when(client.exchange(any(),eq(String.class))).thenThrow(new BizException(40105,"授权已失效"));
        assertTrue(assertThrows(LiveOauthTokenAdapter.RemoteFailure.class,()->adapter.refreshToken("app","secret","refresh")).permanent());
        when(client.exchange(any(),eq(String.class))).thenThrow(new BizException(9999,"网络异常"));
        assertFalse(assertThrows(LiveOauthTokenAdapter.RemoteFailure.class,()->adapter.refreshToken("app","secret","refresh")).permanent());
    }
}
