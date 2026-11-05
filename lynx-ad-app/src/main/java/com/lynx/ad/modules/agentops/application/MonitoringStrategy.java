package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Product/data sign-off is explicit. Candidate thresholds are never silently production policy. */
@Component
public class MonitoringStrategy {
    public record Parameters(String version, boolean approved, int windowHours, int maturityMinutes,
            BigDecimal minSpend, long minImpressions, long minClicks, int minOrders,
            BigDecimal declineRatio, int reviewHours, int maxReviewHours, boolean riskModelApproved) {
        public Parameters {
            if (version == null || !version.matches("[A-Za-z0-9_.-]{1,64}") || windowHours < 2 || windowHours > 8
                    || maturityMinutes < 60 || maturityMinutes > 1440 || minSpend == null || minSpend.signum() <= 0
                    || minImpressions < 100 || minClicks < 10 || minOrders < 1 || declineRatio == null
                    || declineRatio.signum() <= 0 || declineRatio.compareTo(BigDecimal.ONE) >= 0
                    || reviewHours < 2 || reviewHours > 24 || maxReviewHours < reviewHours || maxReviewHours > 168)
                throw new IllegalArgumentException("Invalid monitoring strategy parameters");
            if (riskModelApproved && !approved) throw new IllegalArgumentException("Risk model requires an approved strategy");
        }
        public static Parameters shadow() {
            return new Parameters("candidate-v1", false, 3, 60, new BigDecimal("100"), 1000, 50, 3,
                    new BigDecimal(".2"), 3, 72, false);
        }
    }
    private final Parameters parameters;
    public MonitoringStrategy(ObjectMapper json, @Value("${lynx.ad.monitoring.strategy-json:}") String config) {
        try { parameters = config == null || config.isBlank() ? Parameters.shadow() : json.readValue(config, Parameters.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid lynx.ad.monitoring.strategy-json", e); }
    }
    public Parameters parameters() { return parameters; }
}
