package com.lynx.ad.modules.tenant.api.dto;

import java.time.Instant;
import java.util.List;

public record CurrentUserResponse(
        String userId,
        String phone,
        String nickname,
        String tenantId,
        String tenantName,
        String memberRole,
        Instant trialExpiresAt,
        boolean trialActive,
        boolean needOnboarding,
        boolean demoMode,
        /** 等于演示投放开关，不是令牌。 */
        boolean mockQianchuan,
        String email,
        String companyName,
        String industry,
        String companyScale,
        Instant registeredAt,
        boolean hasPassword,
        long remainingTrialDays,
        String displayPlanCode,
        String displayPlanName,
        String trialStatusLabel,
        String subscriptionPlanCode,
        String subscriptionPlanName,
        Instant periodStart,
        Instant periodEnd,
        Boolean autoRenew,
        String payMethod,
        String subscriptionStatus,
        List<String> memberRoles) {}
