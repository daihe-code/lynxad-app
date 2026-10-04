package com.lynx.ad.domain.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lynx.ad.modules.audit.domain.model.SourceAddress;
import org.junit.jupiter.api.Test;

class SourceAddressTest {

    @Test
    void masksIpv4AndSystemIsDash() {
        assertEquals("203.0.*.*", SourceAddress.maskedForDisplay("203.0.113.10", "张三"));
        assertEquals("-", SourceAddress.maskedForDisplay("203.0.113.10", "系统"));
        assertEquals("-", SourceAddress.maskedForDisplay(null, "张三"));
    }
}
