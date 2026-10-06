package com.lynx.ad.modules.creative.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lynx.ad.common.persistence.JsonbStringTypeHandler;
import java.time.Instant;

@TableName(value = "creative_task", autoResultMap = true)
public class CreativeTaskPo {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String planId;
    private String status;
    private String mode;
    private String errorMsg;
    private Instant createdAt;
    private Instant finishedAt;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String productIdsJson;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String stepsJson;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String feedbackJson;

    public String getFeedbackJson() { return feedbackJson; }
    public void setFeedbackJson(String value) { feedbackJson = value; }

    private String packageId;
    private String regenerationPackageId;
    private String regenerationType;
    private Integer regenerationVersion;

    public String getRegenerationPackageId() { return regenerationPackageId; }
    public void setRegenerationPackageId(String value) { regenerationPackageId = value; }
    public String getRegenerationType() { return regenerationType; }
    public void setRegenerationType(String value) { regenerationType = value; }
    public Integer getRegenerationVersion() { return regenerationVersion; }
    public void setRegenerationVersion(Integer value) { regenerationVersion = value; }

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

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getProductIdsJson() {
        return productIdsJson;
    }

    public void setProductIdsJson(String productIdsJson) {
        this.productIdsJson = productIdsJson;
    }

    public String getStepsJson() {
        return stepsJson;
    }

    public void setStepsJson(String stepsJson) {
        this.stepsJson = stepsJson;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }
}
