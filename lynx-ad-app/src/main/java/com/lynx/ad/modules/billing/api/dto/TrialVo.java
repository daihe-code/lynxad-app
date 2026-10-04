package com.lynx.ad.modules.billing.api.dto;

import java.time.Instant;

public record TrialVo(Instant startedAt, Instant expiresAt, boolean active) {}
