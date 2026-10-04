package com.lynx.ad.domain.dataops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lynx.ad.modules.dataops.domain.service.AmountConversion;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountConversionTest {

    @Test
    void divisorOneHundredThousand() {
        AmountConversion conv = new AmountConversion();
        assertEquals(100_000, AmountConversion.STAT_COST_DIVISOR);
        assertEquals(0, conv.toYuan(100_000).amount().compareTo(new BigDecimal("1.0000")));
        assertEquals(0, conv.toYuan(0).amount().compareTo(new BigDecimal("0.0000")));
    }
}
