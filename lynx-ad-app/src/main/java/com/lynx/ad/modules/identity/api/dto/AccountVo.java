package com.lynx.ad.modules.identity.api.dto;

import java.util.List;

public record AccountVo(
        String accountId,
        String advertiserId,
        String accountName,
        String platform,
        String status,
        List<AwemeVo> awemes,
        String authorizedByUserId) {}
