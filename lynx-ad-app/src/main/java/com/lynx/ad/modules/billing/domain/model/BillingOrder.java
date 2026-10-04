package com.lynx.ad.modules.billing.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import java.time.Instant;

public class BillingOrder {

    private final String id;
    private final TenantId tenantId;
    private final PlanCode planCode;
    private final PlanCode fromPlanCode;
    private final OrderType type;
    private OrderStatus status;
    private final int listPriceFen;
    private final int payableFen;
    private final String discountLabel;
    private final Instant createdAt;
    private Instant paidAt;

    public BillingOrder(
            String id,
            TenantId tenantId,
            PlanCode planCode,
            PlanCode fromPlanCode,
            OrderType type,
            OrderStatus status,
            int listPriceFen,
            int payableFen,
            String discountLabel,
            Instant createdAt,
            Instant paidAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.planCode = planCode;
        this.fromPlanCode = fromPlanCode;
        this.type = type;
        this.status = status;
        this.listPriceFen = listPriceFen;
        this.payableFen = payableFen;
        this.discountLabel = discountLabel;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
    }

    public void markPaid(Instant at) {
        this.status = OrderStatus.PAID;
        this.paidAt = at;
    }

    public void close() {
        this.status = OrderStatus.CLOSED;
    }

    public String id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PlanCode planCode() {
        return planCode;
    }

    public PlanCode fromPlanCode() {
        return fromPlanCode;
    }

    public OrderType type() {
        return type;
    }

    public OrderStatus status() {
        return status;
    }

    public int listPriceFen() {
        return listPriceFen;
    }

    public int payableFen() {
        return payableFen;
    }

    public String discountLabel() {
        return discountLabel;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant paidAt() {
        return paidAt;
    }
}
