package com.lynx.ad.modules.billing.domain.port;

import com.lynx.ad.modules.billing.domain.model.PayChannel;
import java.util.Map;

public interface PaymentPort {

    record PaymentCreateCommand(
            String tenantId, String orderId, String paymentId, int amountFen, String description, String notifyUrl) {}

    record PaymentCreateResult(PayChannel channel, String channelTradeNo, String mockNonce, String codeUrl) {}

    record PaymentNotify(String paymentId, boolean success, String channelTradeNo, Integer amountFen) {}

    PaymentCreateResult create(PaymentCreateCommand command);

    PaymentNotify parseAndVerifyNotify(String rawBody, Map<String, String> headers);

    boolean verifyMockComplete(String paymentId, String mockNonce);
}
