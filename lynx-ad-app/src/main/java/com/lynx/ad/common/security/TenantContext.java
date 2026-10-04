package com.lynx.ad.common.security;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import java.util.Optional;

public final class TenantContext {

    private static final ThreadLocal<TenantPrincipal> HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(TenantPrincipal principal) {
        HOLDER.set(principal);
    }

    public static TenantPrincipal require() {
        TenantPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BizException(401, "未登录");
        }
        return principal;
    }

    public static Optional<TenantId> tenantId() {
        TenantPrincipal principal = HOLDER.get();
        if (principal == null) {
            return Optional.empty();
        }
        return principal.tenantId();
    }

    public static TenantId requireTenantId() {
        return tenantId().orElseThrow(() -> new BizException(401, "未开通公司"));
    }

    public static UserId userId() {
        return require().userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
