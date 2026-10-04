package com.lynx.ad.modules.tenant.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.PhoneNumber;
import java.time.Instant;

public record MemberInvitation(PhoneNumber phone, TenantId tenantId, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return now == null || expiresAt == null || !now.isBefore(expiresAt);
    }
}
