package com.lynx.ad.domain.tenant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.tenant.domain.service.SingleTenantMembershipPolicy;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SingleTenantMembershipPolicyTest {

    private final SingleTenantMembershipPolicy policy = new SingleTenantMembershipPolicy();

    @Test
    void firstJoinAllowed() {
        assertDoesNotThrow(() -> policy.assertCanJoin(Optional.empty(), new TenantId("t1")));
    }

    @Test
    void sameTenantRejoinAllowed() {
        assertDoesNotThrow(() -> policy.assertCanJoin(Optional.of(new TenantId("t1")), new TenantId("t1")));
    }

    @Test
    void secondCompanyRejected() {
        assertThrows(DomainException.class, () -> policy.assertCanJoin(Optional.of(new TenantId("t1")), new TenantId("t2")));
    }
}
