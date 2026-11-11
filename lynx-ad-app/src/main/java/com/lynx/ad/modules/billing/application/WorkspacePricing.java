package com.lynx.ad.modules.billing.application;

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
    private boolean enabled;
    private Map<String, Integer> childSeats = new LinkedHashMap<>(Map.of("trial", 2, "basic", 2, "professional", 3, "flagship", 10));
    private Map<String, Long> periodPoints = new LinkedHashMap<>(Map.of("trial", 600L, "basic", 3000L, "professional", 9000L, "flagship", 30000L));
    private Map<String, Long> generationPoints = new LinkedHashMap<>(Map.of("video", 240L, "image", 15L, "native_image_text", 60L, "title", 1L));
    private Map<String, Recharge> recharge = new LinkedHashMap<>(Map.of(
            "r99", new Recharge(9900, 1000), "r299", new Recharge(29900, 3100), "r999", new Recharge(99900, 10800)));

    public record Recharge(int priceFen, long points) {}
    public record RechargeOption(String code, int priceFen, long points) {}
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Integer> getChildSeats() { return childSeats; }
    public void setChildSeats(Map<String, Integer> value) { childSeats = value; }
    public Map<String, Long> getPeriodPoints() { return periodPoints; }
    public void setPeriodPoints(Map<String, Long> value) { periodPoints = value; }
    public Map<String, Long> getGenerationPoints() { return generationPoints; }
    public void setGenerationPoints(Map<String, Long> value) { generationPoints = value; }
    public Map<String, Recharge> getRecharge() { return recharge; }
    public void setRecharge(Map<String, Recharge> value) { recharge = value; }
    public int seats(String plan) { return Math.max(0, childSeats.getOrDefault(plan.toLowerCase(Locale.ROOT), 0)); }
    public long points(String plan) { return Math.max(0, periodPoints.getOrDefault(plan.toLowerCase(Locale.ROOT), 0L)); }
    public BigDecimal rate(String type) {
        Long rate = generationPoints.get(type);
        if (rate == null || rate < 0) throw new IllegalStateException("Missing generation rate for " + type);
        return BigDecimal.valueOf(rate);
    }
    public List<RechargeOption> options() {
        return recharge.entrySet().stream().filter(e -> e.getValue().priceFen() > 0 && e.getValue().points() > 0)
                .map(e -> new RechargeOption(e.getKey(), e.getValue().priceFen(), e.getValue().points()))
                .sorted(java.util.Comparator.comparingInt(RechargeOption::priceFen)).toList();
    }
}
