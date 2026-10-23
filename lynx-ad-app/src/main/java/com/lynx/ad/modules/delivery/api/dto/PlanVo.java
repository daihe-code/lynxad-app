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
        String qcStatus, com.fasterxml.jackson.databind.JsonNode platformDetail,
        java.time.LocalDate statisticsDate,boolean historicalMetrics,
        com.lynx.ad.modules.delivery.infrastructure.persistence.PlanSettingsEditStore.State settingsEdit,
        com.lynx.ad.modules.delivery.domain.service.PlanReviewProgress.View review) {
    @com.fasterxml.jackson.annotation.JsonProperty("scheduleType")
    public String scheduleType() {
        var type=platformDetail==null?null:com.lynx.ad.modules.delivery.domain.service.PlanSchedule.type(platformDetail.path("delivery_setting"));
        return type==null ? null : type.name();
    }
    public PlanVo(String planId,String name,String status,String source,String qcPlanId,String smartBidType,
                  BigDecimal budget,String awemeId,String rejectReason,boolean lingtou,String guarantee,String coverUrl,
                  BigDecimal costYuan,BigDecimal gmvYuan,BigDecimal roi,String accountId,BigDecimal roi2Goal,
                  java.time.LocalDate startDate,java.time.LocalDate endDate,Long convert,java.time.Instant snapshotTs,
                  java.util.List<PlanListVo.ProductSummary> products,PlanListVo.MetricComparison comparison,
                  java.time.Instant createdAt,String deliveryPlatform,String qcStatus,com.fasterxml.jackson.databind.JsonNode platformDetail,
                  java.time.LocalDate statisticsDate,boolean historicalMetrics,
                  com.lynx.ad.modules.delivery.infrastructure.persistence.PlanSettingsEditStore.State settingsEdit) {
        this(planId,name,status,source,qcPlanId,smartBidType,budget,awemeId,rejectReason,lingtou,guarantee,coverUrl,costYuan,gmvYuan,roi,accountId,roi2Goal,startDate,endDate,convert,snapshotTs,products,comparison,createdAt,deliveryPlatform,qcStatus,platformDetail,statisticsDate,historicalMetrics,settingsEdit,null);
    }
    public PlanVo(
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
        String qcStatus, com.fasterxml.jackson.databind.JsonNode platformDetail,
        java.time.LocalDate statisticsDate,boolean historicalMetrics) {
        this(planId,name,status,source,qcPlanId,smartBidType,budget,awemeId,rejectReason,lingtou,guarantee,coverUrl,costYuan,gmvYuan,roi,accountId,roi2Goal,startDate,endDate,convert,snapshotTs,products,comparison,createdAt,deliveryPlatform,qcStatus,platformDetail,statisticsDate,historicalMetrics,null);
    }

    public PlanVo(String planId,String name,String status,String source,String qcPlanId,String smartBidType,
                  BigDecimal budget,String awemeId,String rejectReason,boolean lingtou,String guarantee,
                  String coverUrl,BigDecimal costYuan,BigDecimal gmvYuan,BigDecimal roi) {
        this(planId,name,status,source,qcPlanId,smartBidType,budget,awemeId,rejectReason,lingtou,guarantee,coverUrl,
                costYuan,gmvYuan,roi,null,null,null,null,null,null,java.util.List.of(),null,null,null,null,null,null,false);
    }
}
