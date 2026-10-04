package com.lynx.ad.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lynx.ad.common.web.OceanIdJson;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OceanIdJsonTest {

    @Test
    void toOceanNumberLiteralKeepsDigits() {
        assertEquals("17800112233", OceanIdJson.toOceanNumberLiteral("17800112233"));
    }

    @Test
    void fromOceanUnifiesNumberAndString() {
        assertEquals("17800112233", OceanIdJson.fromOcean(17800112233L));
        assertEquals("17800112233", OceanIdJson.fromOcean("17800112233"));
        assertEquals("17800112233", OceanIdJson.fromOcean(new BigDecimal("17800112233.0")));
    }
}
