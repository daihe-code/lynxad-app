package com.lynx.ad.modules.tenant.api.dto;

import java.time.Instant;

public record MemberVo(String userId, String phone, String role, Instant joinedAt) {}
