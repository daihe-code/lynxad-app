package com.lynx.ad.application.billing;

import com.lynx.ad.modules.billing.application.GenerationCharges;
import com.lynx.ad.modules.billing.application.WorkspacePricing;
import com.lynx.ad.modules.delivery.application.MaterialPointUsage;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Material;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialPointUsageTest {
    @Test
    void settlesDeliveredSecondsAndEachCarouselImageAtItsResolution() {
        var video = new Material();
        video.type = "VIDEO";
        video.durationSec = 17.0;
        var carousel = new Material();
        carousel.type = "CAROUSEL";
        carousel.billingImageUnits = MaterialPointUsage.imageUnits(1200, 1200)
                + MaterialPointUsage.imageUnits(1440, 2560);
        var quote = new GenerationCharges.Quote(null,
                Map.of(WorkspacePricing.VIDEO_SECOND, 30, WorkspacePricing.IMAGE_UNIT, 12),
                Map.of(WorkspacePricing.VIDEO_SECOND, BigDecimal.valueOf(65), WorkspacePricing.IMAGE_UNIT, BigDecimal.valueOf(15)));
        assertEquals(Map.of(WorkspacePricing.VIDEO_SECOND, 17, WorkspacePricing.IMAGE_UNIT, 3),
                MaterialPointUsage.delivered(quote, List.of(video, carousel)));
    }

    @Test
    void keepsTheReservationUnitForPreviouslyAcceptedJobs() {
        var video = new Material();
        video.type = "VIDEO";
        video.durationSec = 17.0;
        var quote = new GenerationCharges.Quote(null, Map.of("video", 1), Map.of("video", BigDecimal.valueOf(240)));
        assertEquals(Map.of("video", 1), MaterialPointUsage.delivered(quote, List.of(video)));
    }
}
