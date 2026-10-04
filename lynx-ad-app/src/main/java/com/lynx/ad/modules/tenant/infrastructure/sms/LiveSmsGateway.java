package com.lynx.ad.modules.tenant.infrastructure.sms;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.tenant.domain.port.SmsGateway;

public class LiveSmsGateway implements SmsGateway {

    private final LynxAdProperties properties;

    public LiveSmsGateway(LynxAdProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(PhoneNumber phone, String code) {
        if (!properties.getSms().isLiveConfigured()) {
            throw new BizException(1114, "短信服务未配置");
        }
        throw new BizException(1113, "短信发送失败");
    }
}
