package com.lynx.ad.modules.delivery.domain.model;

public record AudienceJson(String raw) {

    public static AudienceJson empty() {
        return new AudienceJson("{}");
    }
}
