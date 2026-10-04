package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Money;

public record BudgetSuggestion(Money amount, String reason) {}
