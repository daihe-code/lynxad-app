package com.lynx.ad.modules.catalog.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Platform reference prices in yuan. This is separate from a user's calculation price. */
public record PlatformProductPrice(BigDecimal minPrice, BigDecimal maxPrice, BigDecimal marketPrice,
        String source, Instant fetchedAt, boolean stale) {
    public PlatformProductPrice staleCopy() {
        return new PlatformProductPrice(minPrice, maxPrice, marketPrice, source, fetchedAt, true);
    }

    public static PlatformProductPrice available(JsonNode product, Instant at) {
        BigDecimal low = positive(product.path("discount_lower_price"));
        BigDecimal high = positive(product.path("discount_higher_price"));
        // Only discount_price is denominated in fen; the lower/upper bounds are yuan.
        BigDecimal fen = positive(product.path("discount_price"));
        if (low == null && high == null && fen != null) low = high = fen.movePointLeft(2);
        if (low == null) low = high;
        if (high == null) high = low;
        if (low == null || low.compareTo(high) > 0) return null;
        return new PlatformProductPrice(yuan(low), yuan(high), yuan(positive(product.path("market_price"))),
                "QIANCHUAN_AVAILABLE", at, false);
    }

    public static PlatformProductPrice direct(JsonNode value, Instant at) {
        BigDecimal price = positive(value);
        return price == null ? null : new PlatformProductPrice(yuan(price), yuan(price), null, "QIANCHUAN_PRODUCT", at, false);
    }

    private static BigDecimal yuan(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal positive(JsonNode value) {
        try {
            if (!value.isNumber() && !value.isTextual()) return null;
            BigDecimal amount = new BigDecimal(value.asText().trim());
            return amount.signum() > 0 && amount.compareTo(new BigDecimal("9999999999.99")) <= 0 ? amount : null;
        } catch (NumberFormatException ignored) { return null; }
    }
}
