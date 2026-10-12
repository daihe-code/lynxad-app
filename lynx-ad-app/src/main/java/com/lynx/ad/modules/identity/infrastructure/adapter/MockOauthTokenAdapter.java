package com.lynx.ad.modules.identity.infrastructure.adapter;

import com.lynx.ad.modules.identity.domain.model.AwemeType;
import com.lynx.ad.modules.identity.domain.port.OauthTokenPort;
import com.lynx.ad.modules.identity.domain.service.AwemeCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnExpression("'${lynx.ad.oauth.qianchuan.mode:legacy}' != 'live' && '${lynx.ad.mock.qianchuan:true}' == 'true'")
public class MockOauthTokenAdapter implements OauthTokenPort {

    private final AwemeCatalog catalog = new AwemeCatalog();

    @Override
    public OauthTokenResult exchange(String authCode) {
        Instant exp = Instant.now().plusSeconds(3600);
        List<AwemeDraft> awemes = new ArrayList<>();
        for (AwemeType type : AwemeType.values()) {
            if(type == AwemeType.AUTHORIZED) continue;
            awemes.add(new AwemeDraft(
                    "aweme_cb_" + type.name().toLowerCase(),
                    type.name() + "回调号",
                    null,
                    type,
                    type == AwemeType.OFFICIAL,
                    catalog.sceneHint(type)));
        }
        return new OauthTokenResult(
                "adv_oauth_mock",
                "回调千川账户",
                "callback-access",
                "callback-refresh",
                exp,
                exp.plusSeconds(86400),
                awemes);
    }

    @Override
    public OauthTokenResult refresh(String refreshTokenPlain) {
        Instant exp = Instant.now().plusSeconds(3600);
        return new OauthTokenResult(
                "adv_oauth_mock",
                "回调千川账户",
                "callback-access-refreshed-" + exp.getEpochSecond(),
                "callback-refresh-refreshed-" + exp.getEpochSecond(),
                exp,
                exp.plusSeconds(86400),
                List.of());
    }
}
