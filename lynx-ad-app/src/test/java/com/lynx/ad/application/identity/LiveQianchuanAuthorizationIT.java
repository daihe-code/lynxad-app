package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.EntitlementQueryService;
import com.lynx.ad.modules.identity.application.*;
import com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter;
import com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter.*;
import com.lynx.ad.modules.identity.infrastructure.persistence.OauthAuthorizationStore;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes=LynxAdApplication.class,properties={
    "lynx.ad.oauth.qianchuan.mode=live","lynx.ad.oauth.qianchuan.app-id=fixture-qianchuan-app",
    "lynx.ad.oauth.qianchuan.app-secret=fixture-secret","lynx.ad.oauth.qianchuan.redirect-uri=https://example.com/qianchuan/callback"})
@ActiveProfiles("test")
class LiveQianchuanAuthorizationIT extends AbstractContainerIT {
    @Autowired LiveQianchuanAuthorizationService service;
    @Autowired OauthAuthorizationStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired QianchuanCredentialFacade credentials;
    @Autowired OauthQueryService queries;
    @MockitoBean LiveOauthTokenAdapter remote;
    @MockitoBean EntitlementQueryService entitlements;
    private TenantId tenant;
    private UserId user;
    @BeforeEach void fixtures() {
        tenant=new TenantId(UUID.randomUUID().toString()); user=new UserId(UUID.randomUUID().toString());
        jdbc.update("INSERT INTO sys_tenant(id,name,status) VALUES(?,?,'ACTIVE')",tenant.value(),"OAuth contract tenant");
        jdbc.update("INSERT INTO sys_user(id,phone,status) VALUES(?,?,'ACTIVE')",user.value(),UUID.randomUUID().toString().substring(0,20));
        jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role) VALUES(?,?,'OWNER')",tenant.value(),user.value());
        when(entitlements.adAccountLimit(any())).thenReturn(2);
        when(remote.exchangeToken(anyString(),anyString(),anyString())).thenReturn(new TokenBundle("fixture-access","fixture-refresh",Instant.now().plusSeconds(3600),Instant.now().plusSeconds(86400)));
        when(remote.discover(anyString())).thenReturn(List.of(new AuthorizedAdvertiser("991001","Real fixture account","ADVERTISER",null)));
        when(remote.loadAwemes(anyString(),anyString())).thenReturn(List.of());
        when(remote.refreshToken(anyString(),anyString(),anyString())).thenReturn(new TokenBundle("rotated-access","rotated-refresh",Instant.now().plusSeconds(7200),Instant.now().plusSeconds(172800)));
    }
    private String start() {
        var url=service.start(tenant,user,"/accounts?tab=oauth").url();
        assertTrue(url.contains("material_auth=1")); assertTrue(url.contains("fixture-qianchuan-app"));
        for(String part:URI.create(url).getRawQuery().split("&")) if(part.startsWith("state=")) return URLDecoder.decode(part.substring(6),StandardCharsets.UTF_8);
        throw new AssertionError("missing state");
    }
    private LiveQianchuanAuthorizationService.CallbackOutcome authorize() { return service.callback(UUID.randomUUID().toString(),start()); }
    private String accountId(String session) { return service.view(session,tenant,user).boundAccountIds().getFirst(); }
    @Test void singleAccountCallbackEncryptsApplicationAndTokensAndEmptyAwemesStayEmpty() {
        var outcome=authorize(); assertTrue(outcome.ok(),outcome.error()); var view=service.view(outcome.sessionId(),tenant,user);
        assertEquals("COMPLETED",view.status()); assertEquals("/accounts?tab=oauth",view.returnPath());
        String id=accountId(outcome.sessionId()); var token=credentials.getUsableCredential(tenant,new AccountId(id)); assertEquals("fixture-access",token.accessToken());
        String enc=jdbc.queryForObject("SELECT access_token_enc FROM oauth_account WHERE id=?",String.class,id); assertFalse(enc.contains("fixture-access"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_aweme WHERE oauth_account_id=?",Integer.class,id));
        String secret=jdbc.queryForObject("SELECT secret_enc FROM oauth_application WHERE app_id=?",String.class,"fixture-qianchuan-app"); assertFalse(secret.contains("fixture-secret"));
        assertFalse(view.toString().contains("fixture-access"));
    }
    @Test void reauthorizationKeepsAccountIdEvenAtAccountLimit() {
        when(entitlements.adAccountLimit(any())).thenReturn(1);
        String first=accountId(authorize().sessionId()),second=accountId(authorize().sessionId()); assertEquals(first,second);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_account WHERE tenant_id=?",Integer.class,tenant.value()));
    }
    @Test void repeatedCallbackDoesNotExchangeCodeTwice() {
        String state=start(),code=UUID.randomUUID().toString(); var first=service.callback(code,state); var second=service.callback(code,state);
        assertTrue(first.ok()); assertTrue(second.ok()); assertEquals(first.sessionId(),second.sessionId()); verify(remote,times(1)).exchangeToken(anyString(),anyString(),eq(code));
    }
    @Test void codeCannotBeReusedWithAnotherState() {
        String code=UUID.randomUUID().toString(); assertTrue(service.callback(code,start()).ok()); assertFalse(service.callback(code,start()).ok());
        verify(remote,times(1)).exchangeToken(anyString(),anyString(),eq(code));
    }
    @Test void failedDiscoveryCanResumeWithoutExchangingCodeAgain() {
        when(remote.discover(anyString())).thenThrow(new BizException(9999,"temporary network failure"))
            .thenReturn(List.of(new AuthorizedAdvertiser("991001","Recovered","ADVERTISER",null)));
        var outcome=authorize(); assertTrue(outcome.ok()); assertEquals("DISCOVERY_FAILED",service.view(outcome.sessionId(),tenant,user).status());
        assertEquals("COMPLETED",service.retry(outcome.sessionId(),tenant,user).status()); verify(remote,times(1)).exchangeToken(anyString(),anyString(),anyString());
    }
    @Test void severalAccountsShareOneGrantAndOnlyAuthorizedCandidatesCanBind() {
        when(remote.discover(anyString())).thenReturn(List.of(new AuthorizedAdvertiser("991001","A","ADVERTISER",null),new AuthorizedAdvertiser("991002","B","ADVERTISER",null)));
        var outcome=authorize(); assertEquals("SELECTING",service.view(outcome.sessionId(),tenant,user).status());
        assertEquals(403,assertThrows(BizException.class,()->service.bind(outcome.sessionId(),tenant,user,List.of("999"))).code());
        var bound=service.bind(outcome.sessionId(),tenant,user,List.of("991001","991002")); assertEquals(2,bound.size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(DISTINCT grant_id) FROM oauth_account WHERE tenant_id=?",Integer.class,tenant.value()));
        service.refresh(tenant,new AccountId(bound.getFirst().accountId()),true,false);
        assertEquals("rotated-access",credentials.getUsableCredential(tenant,new AccountId(bound.getLast().accountId())).accessToken());
        verify(remote,times(1)).refreshToken(anyString(),anyString(),anyString());
    }
    @Test void accountLimitFailureKeepsSessionAvailableForChoosingFewerAccounts() {
        when(entitlements.adAccountLimit(any())).thenReturn(1);
        when(remote.discover(anyString())).thenReturn(List.of(new AuthorizedAdvertiser("991001","A","ADVERTISER",null),new AuthorizedAdvertiser("991002","B","ADVERTISER",null)));
        var outcome=authorize(); assertThrows(BizException.class,()->service.bind(outcome.sessionId(),tenant,user,List.of("991001","991002")));
        assertEquals("SELECTING",service.view(outcome.sessionId(),tenant,user).status()); assertEquals(1,service.bind(outcome.sessionId(),tenant,user,List.of("991002")).size());
    }
    @Test void sessionCannotBeReadOrBoundByAnotherUserOrTenant() {
        var outcome=authorize(); TenantId otherTenant=new TenantId(UUID.randomUUID().toString()); UserId otherUser=new UserId(UUID.randomUUID().toString());
        assertThrows(BizException.class,()->service.view(outcome.sessionId(),otherTenant,otherUser));
        assertThrows(BizException.class,()->service.bind(outcome.sessionId(),tenant,otherUser,List.of("991001")));
    }
    @Test void temporaryRefreshFailureDoesNotInvalidateAuthorizationAndLeaseIsReleased() {
        var outcome=authorize(); String id=accountId(outcome.sessionId());
        when(remote.refreshToken(anyString(),anyString(),anyString())).thenThrow(new LiveOauthTokenAdapter.RemoteFailure(1205,"temporary",false));
        assertThrows(BizException.class,()->service.refresh(tenant,new AccountId(id),true,false));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM oauth_account WHERE id=?",String.class,id));
        assertNull(jdbc.queryForObject("SELECT refresh_owner FROM oauth_grant WHERE id=(SELECT grant_id FROM oauth_account WHERE id=?)",String.class,id));
    }
    @Test void permanentRefreshFailureInvalidatesSharedGrant() {
        var outcome=authorize(); String id=accountId(outcome.sessionId());
        when(remote.refreshToken(anyString(),anyString(),anyString())).thenThrow(new LiveOauthTokenAdapter.RemoteFailure(1211,"expired",true));
        assertThrows(BizException.class,()->service.refresh(tenant,new AccountId(id),true,false));
        assertEquals("UNUSABLE",jdbc.queryForObject("SELECT status FROM oauth_account WHERE id=?",String.class,id));
        assertThrows(BizException.class,()->credentials.getUsableCredential(tenant,new AccountId(id)));
    }
    @Test void repeatedCallbackDuringExchangeDoesNotCorruptSession() throws Exception {
        String state=start(),code=UUID.randomUUID().toString(); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(remote.exchangeToken(anyString(),anyString(),eq(code))).thenAnswer(call->{ entered.countDown(); assertTrue(release.await(10,TimeUnit.SECONDS)); return new TokenBundle("fixture-access","fixture-refresh",Instant.now().plusSeconds(3600),Instant.now().plusSeconds(86400)); });
        try(var pool=Executors.newSingleThreadExecutor()) {
            var first=pool.submit(()->service.callback(code,state)); assertTrue(entered.await(10,TimeUnit.SECONDS));
            assertFalse(service.callback(code,state).ok()); release.countDown(); assertTrue(first.get(15,TimeUnit.SECONDS).ok());
        } finally { release.countDown(); }
    }
    @Test void cancelledAccountCannotSupplyCredentialsAndReauthorizationRestoresSameAccount() {
        String id=accountId(authorize().sessionId());
        assertThrows(BizException.class,()->service.changeLocalStatus(tenant,new AccountId(id),true));
        service.changeLocalStatus(tenant,new AccountId(id),false);
        assertEquals("UNBOUND",jdbc.queryForObject("SELECT status FROM oauth_account WHERE id=?",String.class,id));
        assertThrows(BizException.class,()->credentials.getUsableCredential(tenant,new AccountId(id)));
        service.changeLocalStatus(tenant,new AccountId(id),true);
        assertEquals("DELETED",jdbc.queryForObject("SELECT status FROM oauth_account WHERE id=?",String.class,id));
        assertEquals(id,accountId(authorize().sessionId()));
        assertEquals("fixture-access",credentials.getUsableCredential(tenant,new AccountId(id)).accessToken());
    }
    @Test void concurrentRefreshCannotConsumeSameRefreshTokenTwice() throws Exception {
        String id=accountId(authorize().sessionId()); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(remote.refreshToken(anyString(),anyString(),anyString())).thenAnswer(call->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return new TokenBundle("rotated-access","rotated-refresh",Instant.now().plusSeconds(7200),Instant.now().plusSeconds(86400));});
        try(var pool=Executors.newSingleThreadExecutor()) {
            var first=pool.submit(()->service.refresh(tenant,new AccountId(id),true,false)); assertTrue(entered.await(10,TimeUnit.SECONDS));
            assertThrows(BizException.class,()->service.refresh(tenant,new AccountId(id),true,false));release.countDown();first.get(15,TimeUnit.SECONDS);
        } finally {release.countDown();}
        verify(remote,times(1)).refreshToken(anyString(),anyString(),anyString());
        assertEquals("rotated-access",credentials.getUsableCredential(tenant,new AccountId(id)).accessToken());
    }
    @Test void accountDetailIncludesStoredIdentitiesAndAwemeAvatarButNeverCredentials() throws Exception {
        jdbc.update("INSERT INTO user_profile(user_id,nickname) VALUES(?,?)",user.value(),"绑定操作者");
        when(remote.discover(anyString())).thenReturn(List.of(new AuthorizedAdvertiser("991001","真实店铺","CUSTOMER_ADMIN","88001")));
        when(remote.loadAwemes(anyString(),anyString())).thenReturn(List.of(new com.lynx.ad.modules.identity.domain.port.OauthTokenPort.AwemeDraft("77001","店铺抖音号","https://example.com/avatar.png",com.lynx.ad.modules.identity.domain.model.AwemeType.AUTHORIZED,false,"已授权抖音号")));
        String id=accountId(authorize().sessionId()); var info=queries.detail(tenant,new AccountId(id));
        assertEquals("fixture-qianchuan-app",info.appId());assertEquals(tenant.value(),info.tenantId());
        assertEquals("CUSTOMER_ADMIN",info.accountRole());assertEquals("88001",info.parentAccountId());assertEquals("绑定操作者",info.authorizedByName());
        assertTrue(info.accessTokenStored());assertTrue(info.refreshTokenStored());
        assertEquals("77001",info.account().awemes().getFirst().awemeId());assertEquals("https://example.com/avatar.png",info.account().awemes().getFirst().avatar());
        String serialized=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(info);
        assertFalse(serialized.contains("fixture-access"));assertFalse(serialized.contains("fixture-refresh"));assertFalse(serialized.contains("fixture-secret"));
        assertThrows(BizException.class,()->queries.detail(new TenantId(UUID.randomUUID().toString()),new AccountId(id)));
    }
}
