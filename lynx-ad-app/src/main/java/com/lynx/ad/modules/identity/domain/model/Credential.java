package com.lynx.ad.modules.identity.domain.model;

import com.lynx.ad.common.domain.Ids.AdvertiserId;

public record Credential(String accessToken, AdvertiserId advertiserId) {

    @Override
    public String toString() {
        return "Credential(****)";
    }
}
