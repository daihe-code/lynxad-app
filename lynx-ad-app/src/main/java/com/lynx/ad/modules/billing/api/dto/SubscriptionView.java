package com.lynx.ad.modules.billing.api.dto;

import java.time.Instant;

public record SubscriptionView(
        boolean hasPaidSubscription,
        boolean trialMapped,
        String planCode,
        String planName,
        double listPriceYuan,
        String discountLabel,
        Instant periodStart,
        Instant periodEnd,
        int remainingDays,
        Boolean autoRenew,
        String payMethod,
        String status) {}
