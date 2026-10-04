package com.lynx.ad.modules.billing.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import java.time.Instant;

public class BillingPayment {

    private final String id;
    private final TenantId tenantId;
    private final String orderId;
    private final PayChannel channel;
    private PaymentStatus status;
    private final int amountFen;
    private String channelTradeNo;
    private final String mockNonce;
    private final Instant createdAt;
    private Instant completedAt;

    public BillingPayment(
            String id,
            TenantId tenantId,
            String orderId,
            PayChannel channel,
            PaymentStatus status,
            int amountFen,
            String channelTradeNo,
            String mockNonce,
            Instant createdAt,
            Instant completedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.channel = channel;
        this.status = status;
        this.amountFen = amountFen;
        this.channelTradeNo = channelTradeNo;
        this.mockNonce = mockNonce;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public void succeed(String tradeNo, Instant at) {
        this.status = PaymentStatus.SUCCEEDED;
        this.channelTradeNo = tradeNo;
        this.completedAt = at;
    }

    public void fail(Instant at) {
        this.status = PaymentStatus.FAILED;
        this.completedAt = at;
    }

    public String id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String orderId() {
        return orderId;
    }

    public PayChannel channel() {
        return channel;
    }

    public PaymentStatus status() {
        return status;
    }

    public int amountFen() {
        return amountFen;
    }

    public String channelTradeNo() {
        return channelTradeNo;
    }

    public String mockNonce() {
        return mockNonce;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
