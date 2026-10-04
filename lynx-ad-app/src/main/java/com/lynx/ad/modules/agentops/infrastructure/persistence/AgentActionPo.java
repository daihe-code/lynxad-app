package com.lynx.ad.modules.agentops.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lynx.ad.common.persistence.JsonbStringTypeHandler;
import java.time.Instant;

@TableName(value = "agent_action", autoResultMap = true)
public class AgentActionPo {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String adPlanId;
    private String actionType;
    private String riskLevel;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String beforeJson;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String afterJson;

    private Instant snapshotTs;
    private String result;
    private String idempotencyKey;
    private Instant recoveredAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public Instant getSnapshotTs() {
        return snapshotTs;
    }

    public void setSnapshotTs(Instant snapshotTs) {
        this.snapshotTs = snapshotTs;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getRecoveredAt() {
        return recoveredAt;
    }

    public void setRecoveredAt(Instant recoveredAt) {
        this.recoveredAt = recoveredAt;
    }
}
