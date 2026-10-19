package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import org.springframework.stereotype.Service;

@Service
public class PlanAccountLinkService {

    private final AdPlanRepository plans;

    public PlanAccountLinkService(AdPlanRepository plans) {
        this.plans = plans;
    }

    public void detach(AccountId accountId) {
        plans.detachOauthAccount(accountId);
    }
}
