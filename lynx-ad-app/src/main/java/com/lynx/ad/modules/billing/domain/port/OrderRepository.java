package com.lynx.ad.modules.billing.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.BillingOrder;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    void save(BillingOrder order);

    Optional<BillingOrder> findById(TenantId tenantId, String orderId);

    Optional<BillingOrder> findPendingPay(TenantId tenantId);

    boolean hasPaidPlan(TenantId tenantId, PlanCode planCode);

    List<BillingOrder> findPaidWithAmount(TenantId tenantId);
}
