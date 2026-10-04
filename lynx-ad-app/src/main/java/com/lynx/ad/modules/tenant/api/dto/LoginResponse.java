package com.lynx.ad.modules.tenant.api.dto;

public record LoginResponse(String token, long expiresIn, boolean needOnboarding, boolean demoMode) {}
