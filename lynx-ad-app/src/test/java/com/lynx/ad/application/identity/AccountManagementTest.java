package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.delivery.application.PlanAccountLinkService;
import com.lynx.ad.modules.identity.application.OauthCommandService;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.*;
import com.lynx.ad.modules.identity.infrastructure.adapter.MockQianchuanClient;
import com.lynx.ad.modules.tenant.application.TenantQuerySupport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AccountManagementTest {
    private final TenantId tenant = new TenantId("account-owner");
    private final OauthAccountRepository repository = mock(OauthAccountRepository.class);

    @SuppressWarnings("unchecked")
    private OauthCommandService commands() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new OauthCommandService(repository, mock(OauthAppBindingRepository.class),
                mock(OauthAuthCodeAuditRepository.class),
                mock(MockQianchuanClient.class), new LynxAdProperties(), mock(ObjectProvider.class),
                mock(Encryptor.class), mock(TenantQuerySupport.class), mock(AuditRecorder.class),
                mock(PlanAccountLinkService.class), transactions);
    }

    private OauthAccount account(String id, String status) {
        return new OauthAccount(new AccountId(id), tenant, Platform.QIANCHUAN, new AdvertiserId("adv-" + id),
                "账户 " + id, new EncryptedToken("encrypted-access"), new EncryptedToken("encrypted-refresh"),
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), status, List.of())
                .withAuthorizedAt(Instant.parse("2026-06-20T00:00:00Z"));
    }

    @Test
    void inactiveRemovalPreservesHistoryButHidesRecordAndBlocksRefresh() {
        var cancelled = account("cancelled", "UNBOUND");
        when(repository.findById(tenant, cancelled.id())).thenReturn(Optional.of(cancelled));
        var commands = commands();
        assertFalse(cancelled.needsRefresh(Instant.now()));
        assertThrows(BizException.class, () -> commands.refresh(tenant, cancelled.id()));
        commands.remove(tenant, cancelled.id());
        assertEquals("DELETED", cancelled.status());
        verify(repository).save(cancelled);
        assertFalse(cancelled.needsRefresh(Instant.now()));
        assertThrows(BizException.class, () -> commands.unbind(tenant, cancelled.id()));
        var active = account("active", "ACTIVE");
        when(repository.findByTenant(tenant)).thenReturn(List.of(cancelled, active));
        var query = new OauthQueryService(repository);
        assertEquals(List.of("active"), query.list(tenant).stream().map(a -> a.accountId()).toList());
        assertEquals(active.authorizedAt(), query.list(tenant).getFirst().authorizedAt());
        assertEquals(active.refreshExpiresAt(), query.list(tenant).getFirst().refreshExpiresAt());
        assertTrue(query.hasBoundQianchuan(tenant));
        when(repository.findByTenant(tenant)).thenReturn(List.of(cancelled));
        assertFalse(query.hasBoundQianchuan(tenant));
    }

    @Test
    void validAccountAndOtherTenantCannotBeRemoved() {
        var active = account("active", "ACTIVE");
        when(repository.findById(tenant, active.id())).thenReturn(Optional.of(active));
        var commands = commands();
        assertThrows(BizException.class, () -> commands.remove(tenant, active.id()));
        var otherTenant = new TenantId("other-tenant");
        when(repository.findById(otherTenant, active.id())).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> commands.remove(otherTenant, active.id()));
        verify(repository, never()).save(any());
    }

    @Test
    void expiredActiveAccountCanBeRemovedWithoutDeletingRelations() {
        var expired = new OauthAccount(new AccountId("expired"), tenant, Platform.QIANCHUAN,
                new AdvertiserId("adv-expired"), "过期账户", new EncryptedToken("access"), new EncryptedToken("refresh"),
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60), "ACTIVE", List.of());
        when(repository.findById(tenant, expired.id())).thenReturn(Optional.of(expired));
        commands().remove(tenant, expired.id());
        assertEquals("DELETED", expired.status());
        assertEquals("adv-expired", expired.advertiserId().value());
        verify(repository).save(expired);
    }
}
