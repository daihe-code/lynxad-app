package com.lynx.ad.modules.tenant.api.dto;

public record ResetPasswordRequest(String phone, String code, String password) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[credentials=REDACTED]";
    }
}
