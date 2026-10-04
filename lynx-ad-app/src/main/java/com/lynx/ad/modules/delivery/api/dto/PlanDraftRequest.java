package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlanDraftRequest(
        String accountId,
        String awemeId,
        String name,
        String smartBidType,
        BigDecimal dailyBudget,
        BigDecimal roi2Goal,
        List<String> productIds,
        String packageId) {}
