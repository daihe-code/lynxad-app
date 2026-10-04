package com.lynx.ad.modules.identity.domain.service;

import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import java.time.Instant;

public class TokenRefreshPolicy {

    public boolean due(OauthAccount account, Instant now) {
        return account != null && account.needsRefresh(now);
    }
}
