package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;

public record PlanTransferRequest(String requestKey, String expectedQcPlanId, String smartBidType,
                                  BigDecimal budget, BigDecimal roi2Goal, boolean existingOnly) {
    public PlanTransferRequest(String requestKey,String expectedQcPlanId,String smartBidType,BigDecimal budget,BigDecimal roi2Goal) {
        this(requestKey,expectedQcPlanId,smartBidType,budget,roi2Goal,false);
    }
}
