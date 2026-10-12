package com.lynx.ad.modules.identity.api.dto;

/** Authorization identity and credential presence only; no tokens or application secret. */
public record AccountAuthorizationDetailVo(
        AccountVo account,
        String tenantId,
        String appId,
        String accountRole,
        String parentAccountId,
        String authorizedByName,
        boolean accessTokenStored,
        boolean refreshTokenStored) {}
