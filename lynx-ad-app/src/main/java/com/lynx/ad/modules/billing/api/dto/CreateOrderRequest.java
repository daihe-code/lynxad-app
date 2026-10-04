package com.lynx.ad.modules.billing.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(@NotBlank String planCode) {}
