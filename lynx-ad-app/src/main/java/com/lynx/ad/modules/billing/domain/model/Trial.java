package com.lynx.ad.modules.billing.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.DomainException;
import java.time.Instant;

public class Trial {

    private final TenantId tenantId;
    private final Instant startedAt;
    private final Instant expiresAt;

    public Trial(TenantId tenantId, Instant startedAt, Instant expiresAt) {
        this.tenantId = tenantId;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public static Trial start(TenantId tenantId, Instant now, TrialWindow window) {
        return new Trial(tenantId, now, now.plus(window.duration()));
    }

    public boolean isActive(Instant now) {
        return now.isBefore(expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !isActive(now);
    }

    public void assertNotRestarted(Trial existing) {
        if (existing != null) {
            throw new DomainException(1104, "试用已开通，本轮不可续期");
        }
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
