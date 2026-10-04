package com.lynx.ad.domain.tenant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.modules.tenant.domain.service.OnboardingPolicy;
import org.junit.jupiter.api.Test;

class OnboardingPolicyTest {

    private final OnboardingPolicy policy = new OnboardingPolicy();

    @Test
    void firstOnboardingCreatesTenant() {
        assertTrue(policy.shouldCreateTenant(false));
    }

    @Test
    void secondLoginDoesNotCreateTenant() {
        assertFalse(policy.shouldCreateTenant(true));
    }
}
