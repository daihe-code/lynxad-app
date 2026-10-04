package com.lynx.ad.common.persistence;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;

public abstract class TenantRepositorySupport {

    protected TenantId currentTenant() {
        return TenantContext.tenantId().orElseThrow(() -> new BizException(401, "未开通公司"));
    }

    protected void assertSameTenant(String tenantId) {
        TenantId current = currentTenant();
        if (tenantId == null || !current.value().equals(tenantId)) {
            throw new BizException(403, "跨租户访问被拒绝");
        }
    }
}
