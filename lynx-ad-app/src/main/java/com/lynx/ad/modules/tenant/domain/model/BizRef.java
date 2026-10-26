package com.lynx.ad.modules.tenant.domain.model;

public record BizRef(String bizType, String bizId, InboxResult result) {
    public BizRef(String bizType, String bizId) { this(bizType, bizId, null); }
}
