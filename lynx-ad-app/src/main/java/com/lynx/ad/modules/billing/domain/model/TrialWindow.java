package com.lynx.ad.modules.billing.domain.model;

import java.time.Duration;

public record TrialWindow(int days) {

    public static TrialWindow threeDays() {
        return new TrialWindow(3);
    }

    public Duration duration() {
        return Duration.ofDays(days <= 0 ? 3 : days);
    }
}
