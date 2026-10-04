package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TenantMembershipFacade {

    private final TenantRepository tenantRepository;

    public TenantMembershipFacade(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public boolean isOwner(TenantId tenantId, UserId userId) {
        return tenantRepository
                .findById(tenantId)
                .flatMap(t -> t.memberOf(userId))
                .map(m -> m.role() == MemberRole.OWNER)
                .orElse(false);
    }

    public Optional<TenantId> currentTenantOf(UserId userId) {
        return tenantRepository.findByUserId(userId).map(t -> t.id());
    }
}
