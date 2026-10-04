package com.lynx.ad.modules.tenant.domain.port;

import com.lynx.ad.common.domain.PhoneNumber;

public interface SmsGateway {

    void send(PhoneNumber phone, String code);
}
