package com.lynx.ad.modules.dataops.domain.service;

import com.lynx.ad.common.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AmountConversion {

    public static final int STAT_COST_DIVISOR = 100_000;

    public Money toYuan(long statCost) {
        BigDecimal yuan = BigDecimal.valueOf(statCost)
                .divide(BigDecimal.valueOf(STAT_COST_DIVISOR), 4, RoundingMode.HALF_UP);
        return Money.ofYuan(yuan);
    }
}
