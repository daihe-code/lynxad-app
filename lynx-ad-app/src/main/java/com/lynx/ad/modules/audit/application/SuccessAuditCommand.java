package com.lynx.ad.modules.audit.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;

public record SuccessAuditCommand(
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
        String extra) {}
