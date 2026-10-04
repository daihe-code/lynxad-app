package com.lynx.ad.modules.billing.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(@NotBlank String orderId) {}
