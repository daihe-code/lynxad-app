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
        if (properties.getPayment().isMock()) {
            return mock;
        }
        return wechat;
    }
}
