package com.lynx.ad.modules.tenant.api.dto;

import java.time.Instant;

public record InboxMessageVo(
        String id, String title, String body, String bizType, String bizId, boolean unread, Instant sentAt) {}
