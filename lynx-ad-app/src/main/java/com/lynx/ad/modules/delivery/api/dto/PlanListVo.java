package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;

public record PlanListVo(
        String planId,
        String name,
        String status,
        String source,
        String qcPlanId,
        boolean lingtou,
        String coverUrl,
        String guarantee,
        BigDecimal costYuan,
        BigDecimal gmvYuan,
        BigDecimal roi) {}
