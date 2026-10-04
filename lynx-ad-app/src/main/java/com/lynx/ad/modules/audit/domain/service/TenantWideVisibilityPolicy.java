package com.lynx.ad.modules.audit.domain.service;

public final class TenantWideVisibilityPolicy {

    private TenantWideVisibilityPolicy() {}

    public static boolean visibleToTenant(String recordTenantId, String currentTenantId) {
        if (currentTenantId == null || currentTenantId.isBlank()) {
            return false;
        }
        return currentTenantId.equals(recordTenantId);
    }
}
