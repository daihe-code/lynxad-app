package com.lynx.ad.modules.billing.api.dto;

import com.lynx.ad.modules.billing.application.MoneyYuan;
import com.lynx.ad.modules.billing.domain.model.BillingOrder;
import java.time.Instant;

public record OrderView(
        String orderId,
        String type,
        String planCode,
        String fromPlanCode,
        int payableFen,
        double payableYuan,
        String discountLabel,
        String status,
        Instant paymentExpiresAt) {

    public OrderView(String orderId, String type, String planCode, String fromPlanCode, int payableFen,
                     double payableYuan, String discountLabel, String status) {
        this(orderId, type, planCode, fromPlanCode, payableFen, payableYuan, discountLabel, status, null);
    }

    public static OrderView from(BillingOrder order) {
        return from(order, null);
    }

    public static OrderView from(BillingOrder order, Instant paymentExpiresAt) {
        return new OrderView(
                order.id(),
                order.type().name(),
                order.planCode().name(),
                order.fromPlanCode() == null ? null : order.fromPlanCode().name(),
                order.payableFen(),
                MoneyYuan.ofFen(order.payableFen()),
                order.discountLabel(),
                order.status().name(),
                paymentExpiresAt);
    }
}
