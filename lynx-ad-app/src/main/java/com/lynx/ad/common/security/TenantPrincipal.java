package com.lynx.ad.common.security;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import java.util.Optional;

public record TenantPrincipal(
        UserId userId,
        Optional<TenantId> tenantId,
        Optional<MemberRole> memberRole,
        SysRole sysRole,
        Optional<String> sessionId) {

    public TenantPrincipal(UserId userId, Optional<TenantId> tenantId, Optional<MemberRole> memberRole, SysRole sysRole) {
        this(userId, tenantId, memberRole, sysRole, Optional.empty());
    }
}
