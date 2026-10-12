package com.lynx.ad.modules.identity.api.dto;

import java.util.List;
import java.time.Instant;

public record AccountVo(
        String accountId,
        String advertiserId,
        String accountName,
        String platform,
        String status,
        List<AwemeVo> awemes,
        String authorizedByUserId,
        Instant authorizedAt,
        Instant tokenExpiresAt,
        Instant refreshExpiresAt,
        String applicationId,
        String credentialSource,
        String catalogError) {}
