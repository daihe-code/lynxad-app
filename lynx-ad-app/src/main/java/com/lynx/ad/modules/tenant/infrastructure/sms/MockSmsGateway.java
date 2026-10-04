package com.lynx.ad.modules.tenant.infrastructure.sms;

import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.port.SmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(MockSmsGateway.class);

    @Override
    public void send(PhoneNumber phone, String code) {
        log.info("sms mock skipped for {} (dev no-send)", mask(phone.value()));
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
