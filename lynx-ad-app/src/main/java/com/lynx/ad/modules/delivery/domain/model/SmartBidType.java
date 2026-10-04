package com.lynx.ad.modules.delivery.domain.model;

public enum SmartBidType {
    CONSERVATIVE,
    CUSTOM;

    public String oceanValue() {
        return switch (this) {
            case CONSERVATIVE -> "SMART_BID_CONSERVATIVE";
            case CUSTOM -> "SMART_BID_CUSTOM";
        };
    }

    public static SmartBidType fromOcean(String raw) {
        if (raw == null) {
            return CONSERVATIVE;
        }
        String v = raw.toUpperCase();
        if (v.contains("CUSTOM")) {
            return CUSTOM;
        }
        return CONSERVATIVE;
    }
}
