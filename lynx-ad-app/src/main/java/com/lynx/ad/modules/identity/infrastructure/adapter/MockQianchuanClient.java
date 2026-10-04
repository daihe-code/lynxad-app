package com.lynx.ad.modules.identity.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.identity.domain.model.Aweme;
import com.lynx.ad.modules.identity.domain.model.AwemeType;
import com.lynx.ad.modules.identity.domain.model.EncryptedToken;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.service.AwemeCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockQianchuanClient {

    private final Encryptor encryptor;
    private final LynxAdProperties properties;
    private final AwemeCatalog catalog = new AwemeCatalog();

    public MockQianchuanClient(Encryptor encryptor, LynxAdProperties properties) {
        this.encryptor = encryptor;
        this.properties = properties;
    }

    public OauthAccount bindDemo(TenantId tenantId) {
        Instant exp = Instant.now().plusSeconds(3600);
        List<Aweme> awemes = new ArrayList<>();
        for (AwemeType type : AwemeType.values()) {
            boolean primary = type == AwemeType.OFFICIAL;
            awemes.add(new Aweme(
                    new AwemeId("aweme_" + type.name().toLowerCase()),
                    type.name() + "号",
                    null,
                    type,
                    primary,
                    catalog.sceneHint(type)));
        }
        return OauthAccount.authorize(
                tenantId,
                new AdvertiserId("adv_demo"),
                "演示千川账户",
                new EncryptedToken(encryptor.encrypt("mock-access")),
                new EncryptedToken(encryptor.encrypt("mock-refresh")),
                exp,
                exp.plusSeconds(86400),
                awemes);
    }

    public boolean mockEnabled() {
        return properties.getMock().isQianchuan();
    }
}
