package com.lynx.ad.modules.delivery.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.List;

/** A material thumbnail must come from visual material metadata, never a product image. */
public final class PlanMaterialCover {
    private PlanMaterialCover() {}

    public static String localAsset(CreationState state) {
        if (state.materials == null) return null;
        for (var material : state.materials) {
            if (!"READY".equals(material.status) || "TITLE".equals(material.type)
                    || state.removedMaterialIds != null && state.removedMaterialIds.contains(material.id)) continue;
            if (material.coverAssetId != null && !material.coverAssetId.isBlank()) return material.coverAssetId;
            if (("IMAGE".equals(material.type) || "CAROUSEL".equals(material.type)) && material.imageAssetIds != null)
                for (String id : material.imageAssetIds) if (id != null && !id.isBlank()) return id;
        }
        return null;
    }

    public static String remote(JsonNode metadata) {
        if (metadata == null) return null;
        for (var group : metadata.path("multi_product_creative_list")) {
            for (String key : List.of("video_material", "image_material", "carousel_material")) {
                var materials = group.path(key);
                if (materials.isObject()) {
                    String cover = cover(materials);
                    if (cover != null) return cover;
                } else for (var material : materials) {
                    String cover = cover(material);
                    if (cover != null) return cover;
                }
            }
        }
        return null;
    }

    private static String cover(JsonNode material) {
        for (String key : List.of("poster_url", "cover_url", "video_cover_url", "image_url")) {
            String value = image(material.path(key).asText(null));
            if (value != null) return value;
        }
        for (String key : List.of("cover_image", "image")) {
            String value = image(material.path(key).path("image_url").asText(null));
            if (value != null) return value;
        }
        for (var node : material.path("image_urls")) {
            String value = image(node.asText(null));
            if (value != null) return value;
        }
        return null;
    }

    private static String image(String value) {
        if (value == null) return null;
        try {
            var uri = java.net.URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }
}
