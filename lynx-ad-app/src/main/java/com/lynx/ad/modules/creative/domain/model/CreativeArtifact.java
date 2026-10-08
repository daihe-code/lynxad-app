package com.lynx.ad.modules.creative.domain.model;

import com.lynx.ad.common.domain.Ids.CoverImageId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.VideoId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record CreativeArtifact(
        QcProductId productId,
        List<AdTitle> titles,
        Optional<VideoId> videoId,
        Optional<CoverImageId> coverImageId,
        String videoObjectKey,
        String coverObjectKey,
        String storageMode,
        String filename,
        Long sizeBytes,
        Integer durationSec,
        Integer width,
        Integer height,
        String imageMode,
        Instant createdAt,
        boolean mockContent) {

    public CreativeArtifact(QcProductId productId, List<AdTitle> titles, Optional<VideoId> videoId,
            Optional<CoverImageId> coverImageId, String videoObjectKey, String coverObjectKey,
            String storageMode, String filename, Long sizeBytes, Integer durationSec,
            Integer width, Integer height, String imageMode, Instant createdAt) {
        this(productId, titles, videoId, coverImageId, videoObjectKey, coverObjectKey,
                storageMode, filename, sizeBytes, durationSec, width, height, imageMode, createdAt, false);
    }

    public CreativeArtifact(
            QcProductId productId, List<AdTitle> titles, Optional<VideoId> videoId, Optional<CoverImageId> coverImageId) {
        this(productId, titles, videoId, coverImageId, null, null, null, null, null, null, null, null, null, null);
    }

    public CreativeArtifact withStorage(
            String videoObjectKey,
            String coverObjectKey,
            String storageMode,
            String filename,
            Long sizeBytes,
            String imageMode,
            Instant createdAt) {
        return new CreativeArtifact(
                productId,
                titles,
                videoId,
                coverImageId,
                videoObjectKey,
                coverObjectKey,
                storageMode,
                filename,
                sizeBytes,
                durationSec,
                width,
                height,
                imageMode,
                createdAt,
                mockContent);
    }

    public CreativeArtifact withMockContent(boolean mock) {
        return new CreativeArtifact(productId, titles, videoId, coverImageId, videoObjectKey,
                coverObjectKey, storageMode, filename, sizeBytes, durationSec, width, height,
                imageMode, createdAt, mock);
    }
}
