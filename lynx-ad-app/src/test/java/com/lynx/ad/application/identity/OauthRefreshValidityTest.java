package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.billing.application.EntitlementQueryService;
import com.lynx.ad.modules.identity.application.OauthCommandService;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.*;
import com.lynx.ad.modules.identity.infrastructure.adapter.MockQianchuanClient;
import com.lynx.ad.modules.tenant.application.TenantQuerySupport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OauthRefreshValidityTest {
    private final TenantId tenant = new TenantId("refresh-tenant");
    private final OauthAccountRepository repository = mock(OauthAccountRepository.class);
    private final OauthTokenPort port = mock(OauthTokenPort.class);
    private final Encryptor encryptor = mock(Encryptor.class);

    @SuppressWarnings("unchecked")
    private OauthCommandService commands() {
        ObjectProvider<OauthTokenPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        when(encryptor.decrypt("old-refresh-enc")).thenReturn("old-refresh");
        when(encryptor.encrypt("new-access")).thenReturn("new-access-enc");
        when(encryptor.encrypt("new-refresh")).thenReturn("new-refresh-enc");
        return new OauthCommandService(repository, mock(OauthAuthCodeAuditRepository.class),
                mock(MockQianchuanClient.class), new LynxAdProperties(), provider,
                encryptor, mock(TenantQuerySupport.class), mock(AuditRecorder.class),
                mock(EntitlementQueryService.class));
    }

    private OauthAccount account() {
        var account = new OauthAccount(new AccountId("original-account"), tenant, Platform.QIANCHUAN,
                new AdvertiserId("original-advertiser"), "原千川账户", new EncryptedToken("old-access-enc"),
                new EncryptedToken("old-refresh-enc"), Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(60), "ACTIVE",
                List.of(new Aweme(new AwemeId("original-aweme"), "原抖音号", null, AwemeType.OFFICIAL, true, "电商")))
                .withAuthorizedAt(Instant.parse("2026-10-04T00:00:00Z"));
        when(repository.findById(tenant, account.id())).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void successfulRefreshPersistsBothExpirationsAndKeepsOriginalRelations() {
        var account = account();
        var accessExp = Instant.now().plusSeconds(3600);
        var refreshExp = Instant.now().plusSeconds(86400);
        when(port.refresh("old-refresh")).thenReturn(new OauthTokenPort.OauthTokenResult(
                "provider-adv", "provider-name", "new-access", "new-refresh", accessExp, refreshExp, List.of()));

        commands().refresh(tenant, account.id());

        verify(repository).save(account);
        assertEquals(accessExp, account.tokenExpiresAt());
        assertEquals(refreshExp, account.refreshExpiresAt());
        assertEquals("new-refresh-enc", account.refreshToken().cipherText());
        assertEquals("original-account", account.id().value());
        assertEquals("original-advertiser", account.advertiserId().value());
        assertEquals("original-aweme", account.awemes().getFirst().awemeId().value());
        assertEquals(Instant.parse("2026-10-04T00:00:00Z"), account.authorizedAt());
        assertEquals("ACTIVE", account.status());
    }

    @Test
    void missingRefreshFieldsRetainExistingTokenAndValidity() {
        var account = account();
        var originalExp = account.refreshExpiresAt();
        when(port.refresh("old-refresh")).thenReturn(new OauthTokenPort.OauthTokenResult(
                "provider-adv", "provider-name", "new-access", null, Instant.now().plusSeconds(3600), null, List.of()));

        commands().refresh(tenant, account.id());

        assertEquals(originalExp, account.refreshExpiresAt());
        assertEquals("old-refresh-enc", account.refreshToken().cipherText());
        verify(repository).save(account);
    }

    @Test
    void failedRefreshDoesNotExtendAuthorizationValidity() {
        var account = account();
        var originalExp = account.refreshExpiresAt();
        when(port.refresh("old-refresh")).thenThrow(new BizException(1211, "授权已失效"));

        assertThrows(BizException.class, () -> commands().refresh(tenant, account.id()));

        assertEquals(originalExp, account.refreshExpiresAt());
        verify(repository, never()).save(any());
    }
}
