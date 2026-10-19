package com.lynx.ad.modules.identity.api.dto;

import java.time.Instant;
import java.util.List;

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
