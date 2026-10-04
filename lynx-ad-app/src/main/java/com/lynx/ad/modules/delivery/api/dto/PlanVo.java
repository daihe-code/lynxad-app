package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;

public record PlanVo(
        String planId,
        String name,
        String status,
        String source,
        String qcPlanId,
        String smartBidType,
        BigDecimal budget,
        String awemeId,
        String rejectReason,
        boolean lingtou,
        String guarantee,
        String coverUrl,
        BigDecimal costYuan,
        BigDecimal gmvYuan,
        BigDecimal roi) {}
