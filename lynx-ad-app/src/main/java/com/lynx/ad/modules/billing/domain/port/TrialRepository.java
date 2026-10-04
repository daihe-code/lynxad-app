package com.lynx.ad.modules.billing.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.Trial;
import java.util.Optional;

public interface TrialRepository {

    void save(Trial trial);

    Optional<Trial> findByTenant(TenantId id);
}
