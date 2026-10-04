package com.lynx.ad.modules.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lynx.ad.common.persistence.JsonbStringTypeHandler;
import java.time.Instant;

@TableName(value = "audit_record", autoResultMap = true)
public class AuditRecordPo {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String userId;
    private String actorName;
    private String actionType;
    private String objectType;
    private String objectId;
    private String objectLabel;
    private String result;
    private String ip;
    private String sessionId;
    private String userAgent;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String extra;

    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getObjectLabel() { return objectLabel; }
    public void setObjectLabel(String objectLabel) { this.objectLabel = objectLabel; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
