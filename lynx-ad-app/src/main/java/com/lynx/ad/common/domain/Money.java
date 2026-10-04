package com.lynx.ad.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (currency == null) {
            currency = Currency.getInstance("CNY");
        }
        amount = amount.setScale(4, RoundingMode.HALF_UP);
    }

    public static Money ofYuan(BigDecimal amount) {
        return new Money(amount, Currency.getInstance("CNY"));
    }

    public static Money ofYuan(double amount) {
        return ofYuan(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return ofYuan(BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        return ofYuan(this.amount.add(other.amount));
    }

    public BigDecimal ratioOf(Money other) {
        if (other.amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return this.amount.divide(other.amount, 6, RoundingMode.HALF_UP);
    }
}
