package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.crypto.AesGcmEncryptor;
import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import com.lynx.ad.modules.identity.domain.model.EncryptedToken;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DemoLiveCredentialTest {

    @Test
    void liveModeRejectsDemoAdvertisersAndDemoTokens() {
        assertRejected("adv_demo", "real-looking-token");
        assertRejected("adv_oauth_mock", "real-looking-token");
        assertRejected("adv_real_9", "mock-access");
        assertRejected("adv_real_9", "callback-access");
        assertRejected("adv_real_9", "callback-access-refreshed-1");
    }

    @Test
    void liveModeReturnsARealToken() {
        AtomicInteger sends = new AtomicInteger();
        QianchuanCredentialFacade facade = facade(false);
        OauthAccount account = account("adv_real_9", "live-access-token-ok");
        ((MemoryAccounts) facadeRepository(facade, account)).store(account);
        Credential credential = facade.get(account.id());
        assertEquals("live-access-token-ok", credential.accessToken());
        assertEquals(0, sends.get());
    }

    @Test
    void mockModeReturnsDemoToken() {
        QianchuanCredentialFacade facade = facade(true);
        OauthAccount account = account("adv_demo", "mock-access");
        facadeRepository(facade, account);
        Credential credential = facade.get(account.id());
        assertEquals("mock-access", credential.accessToken());
    }

    private static void assertRejected(String advertiserId, String plain) {
        AtomicInteger sends = new AtomicInteger();
        QianchuanCredentialFacade facade = facade(false);
        OauthAccount account = account(advertiserId, plain);
        facadeRepository(facade, account);
        try {
            Credential credential = facade.get(account.id());
            sends.incrementAndGet();
            throw new AssertionError("expected 1207 but got token " + credential.accessToken());
        } catch (BizException ex) {
            assertEquals(1207, ex.code());
            assertEquals("演示授权不能用于千川真链", ex.getMessage());
        }
        assertEquals(0, sends.get());
    }

    private static QianchuanCredentialFacade facade(boolean mock) {
        LynxAdProperties properties = new LynxAdProperties();
        properties.getMock().setQianchuan(mock);
        return new QianchuanCredentialFacade(new MemoryAccounts(), new AesGcmEncryptor(properties), properties);
    }

    private static OauthAccount account(String advertiserId, String plain) {
        LynxAdProperties properties = new LynxAdProperties();
        Encryptor encryptor = new AesGcmEncryptor(properties);
        Instant exp = Instant.now().plusSeconds(3600);
        return OauthAccount.authorize(
                new TenantId("tenant-1"),
                new AdvertiserId(advertiserId),
                "账户",
                new EncryptedToken(encryptor.encrypt(plain)),
                new EncryptedToken(encryptor.encrypt("refresh")),
                exp,
                exp.plusSeconds(86400),
                List.of());
    }

    private static MemoryAccounts facadeRepository(QianchuanCredentialFacade facade, OauthAccount account) {
        MemoryAccounts accounts = (MemoryAccounts) repositoryOf(facade);
        accounts.store(account);
        return accounts;
    }

    private static OauthAccountRepository repositoryOf(QianchuanCredentialFacade facade) {
        try {
            var field = QianchuanCredentialFacade.class.getDeclaredField("repository");
            field.setAccessible(true);
            return (OauthAccountRepository) field.get(facade);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class MemoryAccounts implements OauthAccountRepository {
        private OauthAccount account;

        void store(OauthAccount account) {
            this.account = account;
        }

        @Override
        public Optional<OauthAccount> findByIdOnly(AccountId id) {
            if (account != null && account.id().equals(id)) {
                return Optional.of(account);
            }
            return Optional.empty();
        }

        @Override
        public void save(OauthAccount account) {}

        @Override
        public void saveRenewingAuthorization(OauthAccount account) {}

        @Override
        public List<OauthAccount> findByTenant(TenantId id) {
            return List.of();
        }

        @Override
        public Optional<OauthAccount> findById(TenantId tenantId, AccountId id) {
            return Optional.empty();
        }

        @Override
        public List<OauthAccount> findDueRefresh(Instant deadline) {
            return List.of();
        }

        @Override
        public void deleteAwemes(AccountId id) {}

        @Override
        public void deleteById(AccountId id) {}
    }
}
