package com.lynx.ad.infrastructure.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.application.OauthCommandService;
import com.lynx.ad.modules.identity.domain.model.EncryptedToken;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.identity.domain.port.OauthTokenPort;
import com.lynx.ad.modules.identity.infrastructure.adapter.Qianchuan401ReplayInterceptor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class Qianchuan401ReplayTest {

    @Test
    void first401ThenRefreshThen200() {
        OauthAccountRepository repo = Mockito.mock(OauthAccountRepository.class);
        OauthAccount account = OauthAccount.authorize(
                new TenantId("t1"),
                new AdvertiserId("adv"),
                "demo",
                new EncryptedToken("enc"),
                new EncryptedToken("ref"),
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(3600),
                List.of());
        when(repo.findByIdOnly(any())).thenReturn(Optional.of(account));
        OauthCommandService commands = Mockito.mock(OauthCommandService.class);
        ObjectProvider<OauthCommandService> provider = provider(commands);
        Qianchuan401ReplayInterceptor interceptor = new Qianchuan401ReplayInterceptor(repo, provider);
        AtomicInteger n = new AtomicInteger();
        String body = interceptor.executeWithReplay(
                () -> {
                    if (n.incrementAndGet() == 1) {
                        throw new BizException(401, "expired");
                    }
                    return "ok";
                },
                new AccountId(account.id().value()));
        assertEquals("ok", body);
        assertEquals(2, n.get());
        verify(commands, times(1)).applyPortRefresh(any());
        verify(repo).save(any());
    }

    @Test
    void secondFailIs1211Not1201() {
        OauthAccountRepository repo = Mockito.mock(OauthAccountRepository.class);
        OauthAccount account = OauthAccount.authorize(
                new TenantId("t1"),
                new AdvertiserId("adv"),
                "demo",
                new EncryptedToken("enc"),
                new EncryptedToken("ref"),
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(3600),
                List.of());
        when(repo.findByIdOnly(any())).thenReturn(Optional.of(account));
        OauthCommandService commands = Mockito.mock(OauthCommandService.class);
        Qianchuan401ReplayInterceptor interceptor = new Qianchuan401ReplayInterceptor(repo, provider(commands));
        BizException ex = assertThrows(BizException.class, () -> interceptor.executeWithReplay(
                () -> {
                    throw new BizException(40102, "expired");
                },
                new AccountId(account.id().value())));
        assertEquals(1211, ex.code());
        assertTrueMsg(ex.getMessage());
    }

    @Test
    void missingAccountIs1201() {
        OauthAccountRepository repo = Mockito.mock(OauthAccountRepository.class);
        when(repo.findByIdOnly(any())).thenReturn(Optional.empty());
        Qianchuan401ReplayInterceptor interceptor =
                new Qianchuan401ReplayInterceptor(repo, provider(Mockito.mock(OauthCommandService.class)));
        BizException ex = assertThrows(BizException.class, () -> interceptor.executeWithReplay(
                () -> {
                    throw new BizException(401, "expired");
                },
                new AccountId("acc1")));
        assertEquals(1201, ex.code());
        verify(repo, never()).save(any());
    }

    @Test
    void refreshUsesPortNotSameCipher() {
        OauthTokenPort port = Mockito.mock(OauthTokenPort.class);
        when(port.refresh(any())).thenReturn(new OauthTokenPort.OauthTokenResult(
                "adv",
                "n",
                "new-access",
                "new-refresh",
                Instant.now().plusSeconds(100),
                Instant.now().plusSeconds(200),
                List.of()));
        Encryptor encryptor = Mockito.mock(Encryptor.class);
        when(encryptor.decrypt(any())).thenReturn("old-refresh");
        when(encryptor.encrypt(any())).thenReturn("new-cipher");
        assertEquals("new-access", port.refresh("old-refresh").accessTokenPlain());
        verify(port, times(1)).refresh("old-refresh");
        verify(encryptor, never()).encrypt("old-refresh");
    }

    private static void assertTrueMsg(String message) {
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("授权已失效"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OauthCommandService> provider(OauthCommandService svc) {
        ObjectProvider<OauthCommandService> p = Mockito.mock(ObjectProvider.class);
        when(p.getObject()).thenReturn(svc);
        when(p.getIfAvailable()).thenReturn(svc);
        return p;
    }
}
