package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.modules.creative.domain.model.MaterialType;
import java.time.LocalDate;
import java.util.List;

public interface MaterialLibraryPort {

    record MaterialLibraryQuery(String advertiserId, MaterialType type, LocalDate from, LocalDate to, int page, int pageSize) {}

    record PlatformMaterial(
            String objectId,
            String materialId,
            String filename,
            long sizeBytes,
            Integer width,
            Integer height,
            Integer durationSec,
            String imageMode,
            String source,
            java.time.Instant createdAt,
            String url,
            String posterUrl,
            boolean isAiCreate,
            String reviewBadge) {}

    record MaterialPage(List<PlatformMaterial> items) {}

    MaterialPage list(MaterialLibraryQuery query);
}
