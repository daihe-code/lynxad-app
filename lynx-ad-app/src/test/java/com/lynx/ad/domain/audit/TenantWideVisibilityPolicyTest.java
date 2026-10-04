package com.lynx.ad.domain.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.modules.audit.domain.service.TenantWideVisibilityPolicy;
import org.junit.jupiter.api.Test;

class TenantWideVisibilityPolicyTest {

    @Test
    void sameTenantVisibleOthersAndNullHidden() {
        assertTrue(TenantWideVisibilityPolicy.visibleToTenant("t1", "t1"));
        assertFalse(TenantWideVisibilityPolicy.visibleToTenant("t1", "t2"));
        assertFalse(TenantWideVisibilityPolicy.visibleToTenant(null, "t1"));
        assertFalse(TenantWideVisibilityPolicy.visibleToTenant("t1", null));
    }
}
