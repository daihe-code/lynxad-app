package com.lynx.ad.domain.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.modules.billing.domain.service.PricePolicy;
import org.junit.jupiter.api.Test;

class PricePolicyTest {

    private final PricePolicy policy = new PricePolicy();

    @Test
    void firstProfessionalIs71900() {
        int payable = policy.firstMonthPayableFen(79900, false);
        assertEquals(71900, payable);
        assertTrue(policy.firstMonthLabel(79900, payable).contains("9折"));
    }

    @Test
    void secondProfessionalIsFull() {
        assertEquals(79900, policy.firstMonthPayableFen(79900, true));
    }

    @Test
    void upgradeFromBasic299ToFirstPro() {
        assertEquals(42000, policy.upgradePayableFen(71900, 29900));
    }
}
