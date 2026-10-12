package com.lynx.ad.modules.identity.domain.model;

import com.lynx.ad.common.domain.Ids.AdvertiserId;

public record Credential(String accessToken, AdvertiserId advertiserId, String accountId) {
    public Credential(String accessToken, AdvertiserId advertiserId) { this(accessToken,advertiserId,null); }

    @Override
    public String toString() {
        return "Credential(****)";
    }
}
