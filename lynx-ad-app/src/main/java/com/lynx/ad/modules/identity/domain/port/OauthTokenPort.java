package com.lynx.ad.modules.identity.domain.port;

import com.lynx.ad.modules.identity.domain.model.AwemeType;
import java.time.Instant;
import java.util.List;

public interface OauthTokenPort {

    record AwemeDraft(String awemeId, String name, String avatar, AwemeType type, boolean primary, String sceneHint) {}

    record OauthTokenResult(
            String advertiserId,
            String accountName,
            String accessTokenPlain,
            String refreshTokenPlain,
            Instant accessExpiresAt,
            Instant refreshExpiresAt,
            List<AwemeDraft> awemes) {}

    OauthTokenResult exchange(String authCode);

    OauthTokenResult refresh(String refreshTokenPlain);
}
