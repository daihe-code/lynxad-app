package com.lynx.ad.modules.billing.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PricePolicy {

    public static final String FIRST_MONTH_LABEL = "首月9折";

    public int firstMonthPayableFen(int listPriceFen, boolean hadPaidThisPlan) {
        if (hadPaidThisPlan) {
            return listPriceFen;
        }
        BigDecimal yuan = BigDecimal.valueOf(listPriceFen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        int discountedYuan = yuan.multiply(new BigDecimal("0.9")).setScale(0, RoundingMode.HALF_UP).intValue();
        return discountedYuan * 100;
    }

    public int upgradePayableFen(int targetFirstMonthFen, int paidFenThisPeriod) {
        return Math.max(0, targetFirstMonthFen - paidFenThisPeriod);
    }

    public String firstMonthLabel(int listPriceFen, int payableFen) {
        return payableFen < listPriceFen ? FIRST_MONTH_LABEL : null;
    }
}
