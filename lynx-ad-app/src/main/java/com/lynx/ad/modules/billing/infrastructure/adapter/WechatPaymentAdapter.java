package com.lynx.ad.modules.billing.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.domain.model.PayChannel;
import com.lynx.ad.modules.billing.domain.port.PaymentPort;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WechatPaymentAdapter implements PaymentPort {

    private final LynxAdProperties properties;

    public WechatPaymentAdapter(LynxAdProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentCreateResult create(PaymentCreateCommand command) {
        if (!properties.getPayment().isLiveConfigured()) {
            throw new BizException(1610, "支付通道未配置");
        }
        throw new BizException(1610, "支付通道未配置");
    }

    @Override
    public PaymentNotify parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        if (!properties.getPayment().isLiveConfigured()) {
            throw new BizException(1606, "回调验签失败");
        }
        throw new BizException(1606, "回调验签失败");
    }

    @Override
    public boolean verifyMockComplete(String paymentId, String mockNonce) {
        return false;
    }

    public PayChannel channel() {
        return PayChannel.WECHAT;
    }
}
