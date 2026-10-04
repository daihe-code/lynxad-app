package com.lynx.ad.modules.billing.domain.model;

import java.time.Duration;

public record TrialWindow(int days) {

    public static TrialWindow sevenDays() {
        return new TrialWindow(7);
    }

    public Duration duration() {
        return Duration.ofDays(days <= 0 ? 7 : days);
    }
}
