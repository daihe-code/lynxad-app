package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.api.dto.TrialVo;
import com.lynx.ad.modules.billing.domain.model.BillingPlan;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.port.PlanRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class EntitlementQueryService {

    private final SubscriptionSupport subscriptionSupport;
    private final BillingTrialService billingTrialService;
    private final PlanRepository planRepository;

    public EntitlementQueryService(
            SubscriptionSupport subscriptionSupport,
            BillingTrialService billingTrialService,
            PlanRepository planRepository) {
        this.subscriptionSupport = subscriptionSupport;
        this.billingTrialService = billingTrialService;
        this.planRepository = planRepository;
    }

    public int adAccountLimit(TenantId tenantId) {
        Instant now = Instant.now();
        Optional<Subscription> sub = subscriptionSupport.effective(tenantId, now);
        if (sub.isPresent()) {
            return planRepository
                    .findByCode(sub.get().planCode())
                    .map(BillingPlan::adAccountLimit)
                    .orElse(1);
        }
        TrialVo trial = billingTrialService.current(tenantId, now);
        if (trial != null && trial.active()) {
            return planRepository.findByCode(PlanCode.PROFESSIONAL).map(BillingPlan::adAccountLimit).orElse(3);
        }
        return 0;
    }
}
