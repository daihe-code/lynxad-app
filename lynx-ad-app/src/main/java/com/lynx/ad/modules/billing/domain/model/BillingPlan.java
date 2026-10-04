package com.lynx.ad.modules.billing.domain.model;

import java.util.List;

public record BillingPlan(
        PlanCode code,
        String name,
        int listPriceFen,
        int adAccountLimit,
        int sortNo,
        List<String> benefits,
        String suitableFor) {}
