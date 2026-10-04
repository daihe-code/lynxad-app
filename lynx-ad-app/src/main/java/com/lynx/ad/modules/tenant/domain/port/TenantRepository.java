package com.lynx.ad.modules.tenant.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import java.util.List;
import java.util.Optional;

public interface TenantRepository {

    void save(Tenant tenant);

    Optional<Tenant> findById(TenantId id);

    Optional<Tenant> findByUserId(UserId userId);

    List<Tenant> findAll();
}
