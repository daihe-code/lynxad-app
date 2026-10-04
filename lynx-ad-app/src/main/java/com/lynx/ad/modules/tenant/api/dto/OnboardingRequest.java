package com.lynx.ad.modules.tenant.api.dto;

public record OnboardingRequest(
        String nickname,
        String email,
        String avatarUrl,
        String companyName,
        String industry,
        String position,
        String monthlyBudget,
        String adExperience,
        String coreDemand,
        String password,
        boolean agreeTerms,
        boolean agreePrivacy) {}
