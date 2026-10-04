package com.lynx.ad.modules.identity.domain.service;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.identity.domain.model.Aweme;
import com.lynx.ad.modules.identity.domain.model.AwemeType;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;

public class AwemeCatalog {

    public void requireUsableAweme(OauthAccount account) {
        if (account == null || !account.hasUsableAweme()) {
            throw new DomainException(1202, "请先授权可用抖音号");
        }
    }

    public void assertPrimaryNotReassigned(OauthAccount account, boolean requestedPrimaryChange) {
        if (requestedPrimaryChange && account.primaryAweme().isPresent()) {
            throw new DomainException(1203, "官方号已设为主号，不可改派");
        }
    }

    public String sceneHint(AwemeType type) {
        return switch (type) {
            case OFFICIAL -> "官方";
            case SELF -> "自运营";
            case COOP -> "合作达人";
            case COOP_AGENCY -> "合作机构";
            case ALLIANCE -> "联盟";
        };
    }
}
