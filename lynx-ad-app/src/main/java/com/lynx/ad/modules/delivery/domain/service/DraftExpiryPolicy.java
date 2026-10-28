package com.lynx.ad.modules.delivery.domain.service;

import java.time.Duration;
import java.time.Instant;

public final class DraftExpiryPolicy {
    public static final Duration LIFETIME = Duration.ofDays(7);
    private DraftExpiryPolicy() {}
    public static boolean expired(Instant createdAt, Instant now) {
        return createdAt != null && !createdAt.plus(LIFETIME).isAfter(now);
    }
}
