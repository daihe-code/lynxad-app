package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;

public record PlanTransferRequest(String requestKey, String expectedQcPlanId, String smartBidType,
                                  BigDecimal budget, BigDecimal roi2Goal, boolean existingOnly,
                                  String name, String scheduleType, java.time.LocalDate endDate) {
    public PlanTransferRequest(String requestKey,String expectedQcPlanId,String smartBidType,BigDecimal budget,BigDecimal roi2Goal,boolean existingOnly) {
        this(requestKey,expectedQcPlanId,smartBidType,budget,roi2Goal,existingOnly,null,null,null);
    }
    public PlanTransferRequest(String requestKey,String expectedQcPlanId,String smartBidType,BigDecimal budget,BigDecimal roi2Goal) {
        this(requestKey,expectedQcPlanId,smartBidType,budget,roi2Goal,false);
    }
}
