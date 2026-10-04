package com.lynx.ad.modules.dataops.domain.model;

import java.math.BigDecimal;

public record Ctr(BigDecimal value) {

    public static Ctr of(BigDecimal value) {
        return new Ctr(value == null ? BigDecimal.ZERO : value);
    }
}
