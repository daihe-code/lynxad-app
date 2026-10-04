package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PlanCreativeBindingQuery {

    private final AdPlanRepository planRepository;
    private final AdPlanCreativeBindingStore store;

    public PlanCreativeBindingQuery(AdPlanRepository planRepository, AdPlanCreativeBindingStore store) {
        this.planRepository = planRepository;
        this.store = store;
    }

    public Set<String> usedKeys(TenantId tenantId) {
        return store.usedKeys(planRepository.findByTenant(tenantId).stream().map(p -> p.id().value()).toList());
    }
}
