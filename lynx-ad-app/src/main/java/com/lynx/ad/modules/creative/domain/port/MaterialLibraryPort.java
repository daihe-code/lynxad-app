package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.modules.creative.domain.model.MaterialType;
import java.time.LocalDate;
import java.util.List;

public interface MaterialLibraryPort {

    record MaterialLibraryQuery(String advertiserId, MaterialType type, LocalDate from, LocalDate to, int page, int pageSize, String accessToken, String objectId, String materialId) {
        public MaterialLibraryQuery(String advertiserId,MaterialType type,LocalDate from,LocalDate to,int page,int pageSize,String accessToken) { this(advertiserId,type,from,to,page,pageSize,accessToken,null,null); }
        public MaterialLibraryQuery(String advertiserId,MaterialType type,LocalDate from,LocalDate to,int page,int pageSize) { this(advertiserId,type,from,to,page,pageSize,null); }
        @Override public String toString() { return "MaterialLibraryQuery(type="+type+",page="+page+")"; }
    }

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

    record MaterialPage(List<PlatformMaterial> items, Boolean hasMore) {
        public MaterialPage(List<PlatformMaterial> items) { this(items, null); }
    }

    MaterialPage list(MaterialLibraryQuery query);

    record CarouselImage(String objectId, String materialId) {}
    /** Resolve constituent image file IDs from an exact, account-scoped carousel material ID. */
    default List<CarouselImage> carouselImages(MaterialLibraryQuery query) {
        throw new UnsupportedOperationException("Carousel image lookup requires the live library adapter");
    }
    record CarouselMediaImage(String id,String url,Integer width,Integer height) {}
    record CarouselMedia(List<CarouselMediaImage> images,String audioUrl,String description) {}
    default CarouselMedia carouselMedia(MaterialLibraryQuery query) {
        throw new UnsupportedOperationException("Carousel preview requires the live library adapter");
    }

    /** File-library deletion only; this must never remove a material from a published plan. */
    record DeleteQuery(String advertiserId, MaterialType type, List<String> objectIds, String accessToken, String accountId) {
        @Override public String toString() { return "DeleteQuery(type=" + type + ",count=" + objectIds.size() + ")"; }
    }
    default java.util.Set<String> delete(DeleteQuery query) {
        throw new UnsupportedOperationException("Material deletion requires the live library adapter");
    }
}
