package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lynx.ad.workspace-pricing")
public class WorkspacePricing {
    public static final String VIDEO_SECOND = "video_second";
    public static final String IMAGE_UNIT = "image_unit";
    public static final String PRODUCT_ANALYSIS = "product_analysis";
    public static final String REMAKE_720P_SECOND = "remake_720p_second";
    public static final String REMAKE_1080P_SECOND = "remake_1080p_second";
    private static final int MIN_RECHARGE_FEN = 9_900;
    private static final int PAYMENT_FEE_BPS = 60;
    private static final int POINT_COST_FEN = 3;

    private boolean enabled;
    private Map<String, Integer> childSeats = new LinkedHashMap<>(Map.of("trial", 2, "basic", 0, "professional", 3, "flagship", 10));
    private Map<String, Long> periodPoints = new LinkedHashMap<>(Map.of("basic", 1800L, "professional", 2400L, "flagship", 5000L));
    private Map<String, Long> generationPoints = new LinkedHashMap<>(Map.of("video", 240L, "image", 15L,
            "native_image_text", 60L, "title", 1L, VIDEO_SECOND, 65L, IMAGE_UNIT, 15L, PRODUCT_ANALYSIS, 10L,
            REMAKE_720P_SECOND, 40L, REMAKE_1080P_SECOND, 60L));

    public record RechargeOption(String code, int priceFen, long points) {}
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Integer> getChildSeats() { return childSeats; }
    public void setChildSeats(Map<String, Integer> value) { childSeats = value; }
    public Map<String, Long> getPeriodPoints() { return periodPoints; }
    public void setPeriodPoints(Map<String, Long> value) { periodPoints = value; }
    public Map<String, Long> getGenerationPoints() { return generationPoints; }
    public void setGenerationPoints(Map<String, Long> value) { generationPoints = value; }
    public int seats(String plan) { return Math.max(0, childSeats.getOrDefault(plan.toLowerCase(Locale.ROOT), 0)); }
    public long points(String plan) { return Math.max(0, periodPoints.getOrDefault(plan.toLowerCase(Locale.ROOT), 0L)); }
    public BigDecimal rate(String type) {
        Long rate = generationPoints.get(type);
        if (rate == null || rate <= 0) throw new IllegalStateException("Missing or invalid generation rate for " + type);
        return BigDecimal.valueOf(rate);
    }
    public List<RechargeOption> options() {
        return List.of(9_900, 100_000, 500_000, 1_000_000, 2_000_000).stream()
                .map(this::rechargeQuote).toList();
    }

    public RechargeOption rechargeQuote(int priceFen) {
        if (priceFen < MIN_RECHARGE_FEN) throw new BizException(1001, "单笔充值至少 ¥99");
        int marginBps = priceFen >= 2_000_000 ? 3_000 : priceFen >= 1_000_000 ? 3_500
                : priceFen >= 500_000 ? 4_000 : 5_000;
        long points = (long) priceFen * (10_000 - PAYMENT_FEE_BPS - marginBps) / (10_000 * POINT_COST_FEN);
        return new RechargeOption("fen-" + priceFen, priceFen, points);
    }

    RechargeOption rechargeOption(String code) {
        // Older clients can still send their fixed price codes.
        if ("r99".equals(code)) return rechargeQuote(9_900);
        if ("r299".equals(code)) return rechargeQuote(29_900);
        if ("r999".equals(code)) return rechargeQuote(99_900);
        if (code != null && code.matches("fen-[0-9]{1,10}")) {
            long amount = Long.parseLong(code.substring(4));
            if (amount <= Integer.MAX_VALUE && code.equals("fen-" + amount)) return rechargeQuote((int) amount);
        }
        throw new BizException(1001, "充值金额无效，请重新报价");
    }

    long pointsForLegacyPrice(PlanCode code, int listPriceFen) {
        return switch (code) {
            case BASIC -> listPriceFen == 29_900 ? 3_000 : points(code.name());
            case PROFESSIONAL -> listPriceFen == 79_900 ? 9_000 : points(code.name());
            case FLAGSHIP -> listPriceFen == 299_900 ? 30_000 : points(code.name());
        };
    }
}
