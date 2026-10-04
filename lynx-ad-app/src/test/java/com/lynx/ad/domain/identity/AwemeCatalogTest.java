package com.lynx.ad.domain.identity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.identity.domain.model.Aweme;
import com.lynx.ad.modules.identity.domain.model.AwemeType;
import com.lynx.ad.modules.identity.domain.model.EncryptedToken;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.service.AwemeCatalog;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AwemeCatalogTest {

    private final AwemeCatalog catalog = new AwemeCatalog();

    @Test
    void noAwemeRejectsSelect() {
        OauthAccount empty = OauthAccount.authorize(
                new TenantId("t1"),
                new AdvertiserId("adv"),
                "demo",
                new EncryptedToken("a"),
                new EncryptedToken("r"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                List.of());
        assertThrows(DomainException.class, () -> catalog.requireUsableAweme(empty));
    }

    @Test
    void primaryNotReassigned() {
        Aweme official = new Aweme(new AwemeId("aw1"), "官号", null, AwemeType.OFFICIAL, true, "官方");
        OauthAccount account = OauthAccount.authorize(
                new TenantId("t1"),
                new AdvertiserId("adv"),
                "demo",
                new EncryptedToken("a"),
                new EncryptedToken("r"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                List.of(official));
        assertTrue(account.primaryAweme().isPresent());
        assertThrows(DomainException.class, () -> catalog.assertPrimaryNotReassigned(account, true));
        assertDoesNotThrow(() -> catalog.assertPrimaryNotReassigned(account, false));
    }
}
