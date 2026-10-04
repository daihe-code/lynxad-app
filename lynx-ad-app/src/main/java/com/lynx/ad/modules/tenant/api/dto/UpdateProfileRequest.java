package com.lynx.ad.modules.tenant.api.dto;

public record UpdateProfileRequest(
        String nickname, String email, String companyName, String industry, String companyScale) {}
