package com.lynx.ad.modules.identity.domain.port;

import com.lynx.ad.modules.identity.domain.model.AwemeType;
import java.time.Instant;
import java.util.List;

public interface OauthTokenPort {

    record AwemeDraft(String awemeId, String name, String avatar, AwemeType type, boolean primary, String sceneHint,
                      List<String> authTypes, Boolean isAllowMall) {
        public AwemeDraft { authTypes = authTypes == null ? List.of() : List.copyOf(authTypes); }
        public AwemeDraft(String id,String name,String avatar,AwemeType type,boolean primary,String hint) {
            this(id,name,avatar,type,primary,hint,List.of(),null);
        }
    }

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
