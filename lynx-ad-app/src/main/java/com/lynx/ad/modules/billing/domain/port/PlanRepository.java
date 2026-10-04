package com.lynx.ad.modules.billing.domain.port;

import com.lynx.ad.modules.billing.domain.model.BillingPlan;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import java.util.List;
import java.util.Optional;

public interface PlanRepository {

    List<BillingPlan> findAll();

    Optional<BillingPlan> findByCode(PlanCode code);
}
