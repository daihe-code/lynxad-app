package com.lynx.ad.application.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.domain.port.PaymentPort;
import com.lynx.ad.modules.billing.infrastructure.adapter.WechatPaymentAdapter;
import org.junit.jupiter.api.Test;

class BillingPayConfigIT {

    @Test
    void liveUnconfiguredReturns1610() {
        LynxAdProperties props = new LynxAdProperties();
        props.getPayment().setMode("live");
        WechatPaymentAdapter adapter = new WechatPaymentAdapter(props);
        BizException ex = assertThrows(
                BizException.class,
                () -> adapter.create(new PaymentPort.PaymentCreateCommand("t", "o", "p", 100, "d", "")));
        assertEquals(1610, ex.code());
    }
}
