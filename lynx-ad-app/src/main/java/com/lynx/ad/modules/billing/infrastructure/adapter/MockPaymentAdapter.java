package com.lynx.ad.modules.billing.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.domain.model.PayChannel;
import com.lynx.ad.modules.billing.domain.port.PaymentPort;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentAdapter implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentAdapter.class);

    private final LynxAdProperties properties;

    public MockPaymentAdapter(LynxAdProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentCreateResult create(PaymentCreateCommand command) {
        String nonce = sign(command.paymentId(), command.amountFen(), command.tenantId());
        log.info("mock payment created paymentId={} amountFen={}", command.paymentId(), command.amountFen());
        return new PaymentCreateResult(PayChannel.MOCK, null, nonce, null);
    }

    @Override
    public PaymentNotify parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        throw new BizException(1605, "mock 不走通道回调");
    }

    @Override
    public boolean verifyMockComplete(String paymentId, String mockNonce) {
        return mockNonce != null && !mockNonce.isBlank() && paymentId != null;
    }

    public String expectedNonce(String paymentId, int amountFen, String tenantId) {
        return sign(paymentId, amountFen, tenantId);
    }

    private String sign(String paymentId, int amountFen, String tenantId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getCrypto().getAesKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((paymentId + "|" + amountFen + "|" + tenantId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("mock nonce", ex);
        }
    }
}
