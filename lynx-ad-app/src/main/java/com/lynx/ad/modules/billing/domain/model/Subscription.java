package com.lynx.ad.modules.billing.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import java.time.Instant;

public class Subscription {

    private final TenantId tenantId;
    private PlanCode planCode;
    private SubStatus status;
    private Instant periodStart;
    private Instant periodEnd;
    private boolean autoRenew;
    private String payMethod;
    private int paidFenThisPeriod;
    private PlanCode nextPlanCode;
    private Instant updatedAt;

    public Subscription(
            TenantId tenantId,
            PlanCode planCode,
            SubStatus status,
            Instant periodStart,
            Instant periodEnd,
            boolean autoRenew,
            String payMethod,
            int paidFenThisPeriod,
            PlanCode nextPlanCode,
            Instant updatedAt) {
        this.tenantId = tenantId;
        this.planCode = planCode;
        this.status = status;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.autoRenew = autoRenew;
        this.payMethod = payMethod;
        this.paidFenThisPeriod = paidFenThisPeriod;
        this.nextPlanCode = nextPlanCode;
        this.updatedAt = updatedAt;
    }

    public boolean isEffective(Instant now) {
        return (status == SubStatus.ACTIVE || status == SubStatus.PENDING_DOWNGRADE)
                && now != null
                && !now.isAfter(periodEnd);
    }

    public void expire(Instant now) {
        this.status = SubStatus.EXPIRED;
        this.updatedAt = now;
    }

    public void activateNewPeriod(PlanCode code, Instant start, Instant end, int paidFen, Instant now) {
        this.planCode = code;
        this.status = SubStatus.ACTIVE;
        this.periodStart = start;
        this.periodEnd = end;
        this.paidFenThisPeriod = paidFen;
        this.nextPlanCode = null;
        this.updatedAt = now;
        this.payMethod = "WECHAT";
    }

    public void upgradeInPeriod(PlanCode code, int extraFen, Instant now) {
        this.planCode = code;
        this.status = SubStatus.ACTIVE;
        this.paidFenThisPeriod += extraFen;
        this.nextPlanCode = null;
        this.updatedAt = now;
    }

    public void scheduleDowngrade(PlanCode next, Instant now) {
        this.nextPlanCode = next;
        this.status = SubStatus.PENDING_DOWNGRADE;
        this.updatedAt = now;
    }

    public void setAutoRenew(boolean autoRenew, Instant now) {
        this.autoRenew = autoRenew;
        this.updatedAt = now;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PlanCode planCode() {
        return planCode;
    }

    public SubStatus status() {
        return status;
    }

    public Instant periodStart() {
        return periodStart;
    }

    public Instant periodEnd() {
        return periodEnd;
    }

    public boolean autoRenew() {
        return autoRenew;
    }

    public String payMethod() {
        return payMethod;
    }

    public int paidFenThisPeriod() {
        return paidFenThisPeriod;
    }

    public PlanCode nextPlanCode() {
        return nextPlanCode;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
