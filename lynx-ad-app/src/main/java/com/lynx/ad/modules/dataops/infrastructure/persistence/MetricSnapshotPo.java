package com.lynx.ad.modules.dataops.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

@TableName("plan_metric_snapshot")
public class MetricSnapshotPo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String adPlanId;
    private String qcPlanId;
    private Instant snapshotTs;
    private Long impression;
    private Long click;
    private BigDecimal cost;
    private Long convert;
    private BigDecimal gmv;
    private BigDecimal roi;
    private BigDecimal ctr;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getAdPlanId() {
        return adPlanId;
    }

    public void setAdPlanId(String adPlanId) {
        this.adPlanId = adPlanId;
    }

    public String getQcPlanId() {
        return qcPlanId;
    }

    public void setQcPlanId(String qcPlanId) {
        this.qcPlanId = qcPlanId;
    }

    public Instant getSnapshotTs() {
        return snapshotTs;
    }

    public void setSnapshotTs(Instant snapshotTs) {
        this.snapshotTs = snapshotTs;
    }

    public Long getImpression() {
        return impression;
    }

    public void setImpression(Long impression) {
        this.impression = impression;
    }

    public Long getClick() {
        return click;
    }

    public void setClick(Long click) {
        this.click = click;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public Long getConvert() {
        return convert;
    }

    public void setConvert(Long convert) {
        this.convert = convert;
    }

    public BigDecimal getGmv() {
        return gmv;
    }

    public void setGmv(BigDecimal gmv) {
        this.gmv = gmv;
    }

    public BigDecimal getRoi() {
        return roi;
    }

    public void setRoi(BigDecimal roi) {
        this.roi = roi;
    }

    public BigDecimal getCtr() {
        return ctr;
    }

    public void setCtr(BigDecimal ctr) {
        this.ctr = ctr;
    }
}
