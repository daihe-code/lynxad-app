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
        String deliveryPlatform,
        String accountId, String qcStatus, boolean canEdit, boolean canControl) {
    public PlanListVo(String planId,String name,String status,String source,String qcPlanId,boolean lingtou,
                      String coverUrl,String guarantee,BigDecimal costYuan,BigDecimal gmvYuan,BigDecimal roi,
                      String smartBidType,BigDecimal budget,BigDecimal roi2Goal,LocalDate startDate,LocalDate endDate,
                      Long convert,Instant snapshotTs,List<ProductSummary> products,MetricComparison comparison,
                      Instant createdAt,String deliveryPlatform) {
        this(planId,name,status,source,qcPlanId,lingtou,coverUrl,guarantee,costYuan,gmvYuan,roi,smartBidType,budget,
                roi2Goal,startDate,endDate,convert,snapshotTs,products,comparison,createdAt,deliveryPlatform,
                null,null,false,false);
    }
    public record ProductSummary(String id, String name, String imageUrl) {}
    public record MetricComparison(BigDecimal costChangePct, BigDecimal convertChangePct,
                                   BigDecimal roiChange, Instant previousSnapshotTs,
                                   BigDecimal roiChangePct, String basis) {
        public MetricComparison(BigDecimal costChangePct,BigDecimal convertChangePct,BigDecimal roiChange,Instant previousSnapshotTs,BigDecimal roiChangePct) {
            this(costChangePct,convertChangePct,roiChange,previousSnapshotTs,roiChangePct,"PREVIOUS_SNAPSHOT");
        }
    }
}
