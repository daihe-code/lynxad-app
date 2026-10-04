package com.lynx.ad.modules.tenant.infrastructure.sms;

import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.tenant.domain.port.SmsGateway;

public class OffSmsGateway implements SmsGateway {

    @Override
    public void send(PhoneNumber phone, String code) {
        throw new BizException(1112, "短信网关已关闭");
    }
}
