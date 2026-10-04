package com.lynx.ad.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.persistence.TenantRepositorySupport;
import com.lynx.ad.common.security.SysRole;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.common.security.TenantPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextIsolationTest {

    private final Support support = new Support();

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void otherTenantRejected() {
        TenantContext.set(new TenantPrincipal(new UserId("u1"), Optional.of(new TenantId("t1")), Optional.empty(), SysRole.USER));
        assertDoesNotThrow(() -> support.check("t1"));
        assertThrows(BizException.class, () -> support.check("t2"));
    }

    static class Support extends TenantRepositorySupport {
        void check(String tenantId) {
            assertSameTenant(tenantId);
        }
    }
}
