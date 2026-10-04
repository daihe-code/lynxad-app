package com.lynx.ad.modules.billing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyYuan {

    private MoneyYuan() {}

    public static double ofFen(int fen) {
        return BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).doubleValue();
    }
}
