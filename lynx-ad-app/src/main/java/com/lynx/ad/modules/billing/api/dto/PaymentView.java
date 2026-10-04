package com.lynx.ad.modules.billing.api.dto;

import com.lynx.ad.modules.billing.domain.model.BillingPayment;

public record PaymentView(
        String paymentId,
        String channel,
        String codeUrl,
        boolean mockReady,
        String mockNonce,
        String status) {

    public static PaymentView from(BillingPayment payment, String codeUrl, String nonce) {
        boolean mock = "MOCK".equals(payment.channel().name());
        return new PaymentView(
                payment.id(),
                payment.channel().name(),
                codeUrl,
                mock,
                nonce,
                payment.status().name());
    }
}
