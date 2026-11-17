package com.lynx.ad.modules.billing.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.billing.domain.port.PaymentPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class PaymentConfig {

    @Bean
    @Primary
    public PaymentPort paymentPort(
            LynxAdProperties properties, MockPaymentAdapter mock, WechatPaymentAdapter wechat) {
        return switch (properties.getPayment().getMode().toLowerCase(java.util.Locale.ROOT)) {
            case "mock" -> mock;
            case "live", "alipay" -> wechat;
            default -> throw new IllegalArgumentException("Unknown payment mode");
        };
    }
}
