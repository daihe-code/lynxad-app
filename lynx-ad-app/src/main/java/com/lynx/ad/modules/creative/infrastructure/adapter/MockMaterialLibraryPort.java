package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lynx.ad.mock.qianchuan", havingValue = "true", matchIfMissing = true)
public class MockMaterialLibraryPort implements MaterialLibraryPort {

    public static final Instant SEED_CREATED = LocalDate.of(2026, 9, 15).atStartOfDay().toInstant(ZoneOffset.UTC);

    @Override
    public MaterialPage list(MaterialLibraryQuery query) {
        List<PlatformMaterial> all = switch (query.type()) {
            case VIDEO -> List.of(
                    seed("ext-vid-1", "qc_video_1", "平台竖屏种草.mp4", 2_048_000, 720, 1280, 15, "VIDEO_VERTICAL", "NONE"),
                    seed("ext-vid-2", "qc_video_2", "非灵投横屏.mp4", 3_100_000, 1280, 720, 20, "VIDEO_LARGE", "REVIEWING"));
            case IMAGE -> List.of(
                    seed("ext-img-1", "qc_img_1", "平台封面.jpg", 220_000, 800, 800, null, "SQUARE", "NONE"));
            case CAROUSEL -> List.of(
                    seed("ext-car-1", "qc_car_1", "图文种草一组", 540_000, 800, 800, null, "SQUARE", "REJECTED"));
        };
        return new MaterialPage(all);
    }

    private static PlatformMaterial seed(
            String objectId,
            String materialId,
            String filename,
            long size,
            Integer w,
            Integer h,
            Integer duration,
            String mode,
            String badge) {
        return new PlatformMaterial(
                objectId,
                materialId,
                filename,
                size,
                w,
                h,
                duration,
                mode,
                "EXTERNAL",
                SEED_CREATED,
                "/lynx-ad/api/v1/creative/media?token=mock",
                "/lynx-ad/api/v1/creative/media?token=mock",
                true,
                badge);
    }
}
