package com.lynx.ad.modules.creative.api.dto;

public record LibraryItemVo(
        String id,
        String type,
        String source,
        String filename,
        long sizeBytes,
        String imageMode,
        Integer durationSec,
        String category,
        String createdAt,
        boolean used,
        String reviewBadge,
        String materialId,
        String objectId,
        String posterUrl,
        String playUrl) {}
