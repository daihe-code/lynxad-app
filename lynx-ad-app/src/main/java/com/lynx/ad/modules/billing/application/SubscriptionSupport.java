package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.SubStatus;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionSupport {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionSupport(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @org.springframework.transaction.annotation.Transactional
    public Optional<Subscription> loadAndSettle(TenantId tenantId, Instant now) {
        Optional<Subscription> found = subscriptionRepository.findByTenant(tenantId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Subscription sub = found.get();
        if (sub.status() != SubStatus.EXPIRED && now.isAfter(sub.periodEnd())) {
            sub.expire(now);
            subscriptionRepository.save(sub);
        }
        return Optional.of(sub);
    }

    @org.springframework.transaction.annotation.Transactional
    public Optional<Subscription> effective(TenantId tenantId, Instant now) {
        return loadAndSettle(tenantId, now).filter(s -> s.isEffective(now));
    }
}
