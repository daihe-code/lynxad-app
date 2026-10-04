package com.lynx.ad.modules.audit.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.modules.audit.domain.service.SuccessOnlyRecordPolicy;
import java.time.Instant;
import java.util.UUID;

public final class AuditRecord {

    private final String id;
    private final TenantId tenantId;
    private final UserId userId;
    private final String actorName;
    private final AuditActionType actionType;
    private final String objectType;
    private final String objectId;
    private final String objectLabel;
    private final String result;
    private final String ip;
    private final String sessionId;
    private final String userAgent;
    private final String extra;
    private final Instant createdAt;

    private AuditRecord(
            String id,
            TenantId tenantId,
            UserId userId,
            String actorName,
            AuditActionType actionType,
            String objectType,
            String objectId,
            String objectLabel,
            String result,
            String ip,
            String sessionId,
            String userAgent,
            String extra,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.actorName = actorName;
        this.actionType = actionType;
        this.objectType = objectType;
        this.objectId = objectId;
        this.objectLabel = objectLabel;
        this.result = result;
        this.ip = ip;
        this.sessionId = sessionId;
        this.userAgent = userAgent;
        this.extra = extra;
        this.createdAt = createdAt;
    }

    public static AuditRecord recordSuccess(
            TenantId tenantId,
            UserId userId,
            String actorName,
            AuditActionType actionType,
            String objectType,
            String objectId,
            String objectLabel,
            String ip,
            String sessionId,
            String userAgent,
            String extra) {
        SuccessOnlyRecordPolicy.assertAllowed(actionType, objectLabel, true);
        String name = actorName == null || actorName.isBlank() ? "系统" : actorName;
        return new AuditRecord(
                UUID.randomUUID().toString(),
                tenantId,
                userId,
                name,
                actionType,
                objectType,
                objectId,
                objectLabel,
                "SUCCESS",
                ip,
                sessionId,
                userAgent,
                extra,
                Instant.now());
    }

    public static AuditRecord restore(
            String id,
            TenantId tenantId,
            UserId userId,
            String actorName,
            AuditActionType actionType,
            String objectType,
            String objectId,
            String objectLabel,
            String result,
            String ip,
            String sessionId,
            String userAgent,
            String extra,
            Instant createdAt) {
        return new AuditRecord(
                id,
                tenantId,
                userId,
                actorName,
                actionType,
                objectType,
                objectId,
                objectLabel,
                result,
                ip,
                sessionId,
                userAgent,
                extra,
                createdAt);
    }

    public String id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId userId() {
        return userId;
    }

    public String actorName() {
        return actorName;
    }

    public AuditActionType actionType() {
        return actionType;
    }

    public String objectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
    }

    public String objectLabel() {
        return objectLabel;
    }

    public String result() {
        return result;
    }

    public String ip() {
        return ip;
    }

    public String sessionId() {
        return sessionId;
    }

    public String userAgent() {
        return userAgent;
    }

    public String extra() {
        return extra;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String ipMasked() {
        return SourceAddress.maskedForDisplay(ip, actorName);
    }
}
