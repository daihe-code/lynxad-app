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
    private final com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore creation;

    public PlanCreativeBindingQuery(AdPlanRepository planRepository, AdPlanCreativeBindingStore store, com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore creation) {
        this.planRepository = planRepository;
        this.store = store;this.creation=creation;
    }

    public Set<String> usedKeys(TenantId tenantId) {
        Set<String> keys=new java.util.HashSet<>(store.usedKeys(planRepository.findByTenant(tenantId).stream().map(p -> p.id().value()).toList()));
        keys.addAll(creation.usedMedia(tenantId.value()));return keys;
    }
}
