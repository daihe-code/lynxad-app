package com.lynx.ad.modules.audit.api.dto;

import java.time.Instant;

public record AuditRecordVo(
        String id,
        Instant createdAt,
        String actorName,
        String actionType,
        String objectLabel,
        String ipMasked) {}
