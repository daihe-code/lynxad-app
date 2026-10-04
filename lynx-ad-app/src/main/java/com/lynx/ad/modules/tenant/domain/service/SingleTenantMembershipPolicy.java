package com.lynx.ad.modules.tenant.domain.service;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.DomainException;
import java.util.Optional;

public class SingleTenantMembershipPolicy {

    public void assertCanJoin(Optional<TenantId> existing, TenantId target) {
        if (existing.isPresent() && !existing.get().equals(target)) {
            throw new DomainException(1103, "本轮一名用户只能加入一家公司");
        }
    }
}
