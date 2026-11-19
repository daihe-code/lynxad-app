package com.lynx.ad.modules.billing.api.dto;

import java.util.List;

public record PlanItemView(
        String code,
        String name,
        int listPriceFen,
        double listPriceYuan,
        int adAccountLimit,
        int childAccountLimit,
        long periodPoints,
        String suitableFor,
        List<String> benefits,
        boolean current,
        String action) {}
