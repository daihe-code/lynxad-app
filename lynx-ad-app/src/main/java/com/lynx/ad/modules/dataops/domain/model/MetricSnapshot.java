package com.lynx.ad.modules.dataops.domain.model;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import java.time.Instant;

public class MetricSnapshot {

    private final TenantId tenantId;
    private final PlanId planId;
    private final QcPlanId qcPlanId;
    private final Instant ts;
    private final long impression;
    private final long click;
    private final Money cost;
    private final long convert;
    private final Money gmv;
    private final Roi roi;
    private final Ctr ctr;

    public MetricSnapshot(
            TenantId tenantId,
            PlanId planId,
            QcPlanId qcPlanId,
            Instant ts,
            long impression,
            long click,
            Money cost,
            long convert,
            Money gmv,
            Roi roi,
            Ctr ctr) {
        this.tenantId = tenantId;
        this.planId = planId;
        this.qcPlanId = qcPlanId;
        this.ts = ts;
        this.impression = impression;
        this.click = click;
        this.cost = cost;
        this.convert = convert;
        this.gmv = gmv;
        this.roi = roi;
        this.ctr = ctr;
    }

    public static MetricSnapshot of(
            TenantId tenantId,
            PlanId planId,
            QcPlanId qcPlanId,
            Instant ts,
            long impression,
            long click,
            Money cost,
            long convert,
            Money gmv,
            Roi roi,
            Ctr ctr) {
        return new MetricSnapshot(tenantId, planId, qcPlanId, ts, impression, click, cost, convert, gmv, roi, ctr);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PlanId planId() {
        return planId;
    }

    public QcPlanId qcPlanId() {
        return qcPlanId;
    }

    public Instant ts() {
        return ts;
    }

    public long impression() {
        return impression;
    }

    public long click() {
        return click;
    }

    public Money cost() {
        return cost;
    }

    public long convert() {
        return convert;
    }

    public Money gmv() {
        return gmv;
    }

    public Roi roi() {
        return roi;
    }

    public Ctr ctr() {
        return ctr;
    }
}
