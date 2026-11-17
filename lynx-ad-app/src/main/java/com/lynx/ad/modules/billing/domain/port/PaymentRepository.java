package com.lynx.ad.modules.billing.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.BillingPayment;
import com.lynx.ad.modules.billing.domain.model.PayChannel;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentRepository {

    void save(BillingPayment payment);

    Optional<BillingPayment> findById(TenantId tenantId, String paymentId);

    Optional<BillingPayment> findByIdOnly(String paymentId);

    Map<String, PayChannel> findSucceededChannels(TenantId tenantId, List<String> orderIds);
}
