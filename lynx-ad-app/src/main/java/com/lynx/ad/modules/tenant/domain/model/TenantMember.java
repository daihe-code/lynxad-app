package com.lynx.ad.modules.tenant.domain.model;

import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.security.MemberRole;
import java.time.Instant;

public class TenantMember {

    private final UserId userId;
    private final MemberRole role;
    private final Instant joinedAt;

    public TenantMember(UserId userId, MemberRole role, Instant joinedAt) {
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UserId userId() {
        return userId;
    }

    public MemberRole role() {
        return role;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public boolean canInvite() {
        return role == MemberRole.OWNER;
    }
}
