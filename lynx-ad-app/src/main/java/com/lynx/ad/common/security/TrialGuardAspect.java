package com.lynx.ad.common.security;

import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import java.time.Instant;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TrialGuardAspect {

    private final BillingTrialService billingTrialService;

    public TrialGuardAspect(BillingTrialService billingTrialService) {
        this.billingTrialService = billingTrialService;
    }

    @Before("@annotation(com.lynx.ad.common.security.RequiresActiveTrial)")
    public void check() {
        billingTrialService.assertWritable(TenantContext.requireTenantId(), Instant.now(), WriteAction.PUBLISH);
    }
}
