package com.lynx.ad.modules.delivery.domain.model;

import java.math.BigDecimal;

public enum SmartBidType {
    CONSERVATIVE,
    CUSTOM;

    public BigDecimal minimumDailyBudget() {
        return new BigDecimal(this == CUSTOM ? "300" : "50");
    }

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
