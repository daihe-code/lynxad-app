package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;

public record PlanSettingsRequest(String name, BigDecimal budget, BigDecimal roi2Goal) {}
