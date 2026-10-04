package com.lynx.ad.modules.tenant.infrastructure.sms;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.tenant.domain.port.SmsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsGatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "lynx.ad.sms.mode", havingValue = "mock", matchIfMissing = true)
    public SmsGateway mockSmsGateway() {
        return new MockSmsGateway();
    }

    @Bean
    @ConditionalOnProperty(name = "lynx.ad.sms.mode", havingValue = "off")
    public SmsGateway offSmsGateway() {
        return new OffSmsGateway();
    }

    @Bean
    @ConditionalOnProperty(name = "lynx.ad.sms.mode", havingValue = "live")
    public SmsGateway liveSmsGateway(LynxAdProperties properties) {
        return new LiveSmsGateway(properties);
    }
}
