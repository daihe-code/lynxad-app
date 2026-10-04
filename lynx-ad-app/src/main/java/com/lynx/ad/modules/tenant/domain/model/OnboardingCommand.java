package com.lynx.ad.modules.tenant.domain.model;

public record OnboardingCommand(
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
