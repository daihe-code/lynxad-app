package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

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
        BigDecimal roi,
        String smartBidType,
        BigDecimal budget,
        BigDecimal roi2Goal,
        LocalDate startDate,
        LocalDate endDate,
        Long convert,
        Instant snapshotTs,
        List<ProductSummary> products,
        MetricComparison comparison,
        Instant createdAt,
        String deliveryPlatform) {
    public record ProductSummary(String id, String name, String imageUrl) {}
    public record MetricComparison(BigDecimal costChangePct, BigDecimal convertChangePct,
                                   BigDecimal roiChange, Instant previousSnapshotTs,
                                   BigDecimal roiChangePct) {}
}
