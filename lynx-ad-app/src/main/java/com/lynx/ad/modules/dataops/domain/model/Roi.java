package com.lynx.ad.modules.dataops.domain.model;

import java.math.BigDecimal;

public record Roi(BigDecimal value) {

    public static Roi of(BigDecimal value) {
        return new Roi(value == null ? BigDecimal.ZERO : value);
    }
}
