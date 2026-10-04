package com.lynx.ad.modules.billing.api.dto;

public record MockCompleteRequest(Boolean success, String nonce, Boolean paid) {}
