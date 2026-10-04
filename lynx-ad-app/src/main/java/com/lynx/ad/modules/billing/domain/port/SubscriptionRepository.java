package com.lynx.ad.modules.billing.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import java.util.Optional;

public interface SubscriptionRepository {

    void save(Subscription subscription);

    Optional<Subscription> findByTenant(TenantId tenantId);
}
