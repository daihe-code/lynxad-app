package com.lynx.ad.modules.creative.domain.port;

public record StorageObject(String objectKey, String storageMode, long sizeBytes, String contentType) {}
