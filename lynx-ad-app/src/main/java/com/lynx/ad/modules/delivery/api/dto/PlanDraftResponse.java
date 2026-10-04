package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;

public record PlanDraftResponse(PlanVo plan, BigDecimal suggestBudget, BigDecimal suggestRoi) {}
