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
        BigDecimal roi,
        String accountId, BigDecimal roi2Goal, java.time.LocalDate startDate, java.time.LocalDate endDate,
        Long convert, java.time.Instant snapshotTs, java.util.List<PlanListVo.ProductSummary> products,
        PlanListVo.MetricComparison comparison, java.time.Instant createdAt, String deliveryPlatform,
        String qcStatus, com.fasterxml.jackson.databind.JsonNode platformDetail) {
    public PlanVo(String planId,String name,String status,String source,String qcPlanId,String smartBidType,
                  BigDecimal budget,String awemeId,String rejectReason,boolean lingtou,String guarantee,
                  String coverUrl,BigDecimal costYuan,BigDecimal gmvYuan,BigDecimal roi) {
        this(planId,name,status,source,qcPlanId,smartBidType,budget,awemeId,rejectReason,lingtou,guarantee,coverUrl,
                costYuan,gmvYuan,roi,null,null,null,null,null,null,java.util.List.of(),null,null,null,null,null);
    }
}
