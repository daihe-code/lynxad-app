package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.billing.application.GenerationCharges;
import com.lynx.ad.modules.billing.application.WorkspacePricing;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Material;
import java.util.HashMap;
import java.util.Map;

public final class MaterialPointUsage {
    private static final int MAX_VIDEO_SECONDS = 30;
    private static final int DEFAULT_VIDEO_SECONDS = 15;
    private static final int MAX_CAROUSEL_IMAGES = 6;
    private static final long STANDARD_IMAGE_PIXELS = 2_610_000L;

    private MaterialPointUsage() {}

    public static Map<String, Integer> reserve(String type, int quantity) {
        return switch (GenerationCharges.kind(type)) {
            case "video" -> Map.of(WorkspacePricing.VIDEO_SECOND, MAX_VIDEO_SECONDS * quantity);
            case "image" -> Map.of(WorkspacePricing.IMAGE_UNIT, 2 * quantity);
            case "native_image_text" -> Map.of(WorkspacePricing.IMAGE_UNIT, 2 * MAX_CAROUSEL_IMAGES * quantity);
            default -> throw new IllegalArgumentException("Unsupported material charge: " + type);
        };
    }

    public static void readVideoDuration(Material material, JsonNode item) {
        if (!"VIDEO".equals(material.type)) return;
        double seconds = item.path("detail").path("duration_seconds")
                .asDouble(item.path("content").path("duration_seconds").asDouble());
        if (Double.isFinite(seconds) && seconds > 0) material.durationSec = seconds;
    }

    public static int imageUnits(Integer width, Integer height) {
        return width != null && height != null && (long) width * height > STANDARD_IMAGE_PIXELS ? 2 : 1;
    }

    public static Map<String, Integer> delivered(GenerationCharges.Quote quote, Iterable<Material> materials) {
        var totals = new HashMap<String, Integer>();
        if (quote == null) return totals;
        for (var material : materials) {
            String legacyKind = GenerationCharges.kind(material.type);
            // Previously accepted jobs retain their per-material rate and quantities.
            if (quote.requested().containsKey(legacyKind)) {
                totals.merge(legacyKind, 1, Integer::sum);
            } else if ("VIDEO".equals(material.type)) {
                int seconds = material.durationSec == null ? DEFAULT_VIDEO_SECONDS
                        : Math.clamp((int) Math.round(material.durationSec), 1, MAX_VIDEO_SECONDS);
                totals.merge(WorkspacePricing.VIDEO_SECOND, seconds, Integer::sum);
            } else if (!"product_reference".equals(material.deliverySource)) {
                int units = material.billingImageUnits != null ? material.billingImageUnits
                        : Math.max(1, material.imageAssetIds.size()) * imageUnits(material.width, material.height);
                totals.merge(WorkspacePricing.IMAGE_UNIT, units, Integer::sum);
            }
        }
        return totals;
    }
}
