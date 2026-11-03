package com.lynx.ad.modules.creative.domain.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Persistent names for generated files; the full asset ID distinguishes identical titles and times. */
public final class GeneratedAssetName {
    private static final int MAX_BYTES = 100;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private GeneratedAssetName() {}

    public static String create(String title, String component, Instant createdAt, String assetId, String extension) {
        String role = switch (component) {
            case "VIDEO" -> "视频";
            case "COVER" -> "封面";
            case "AUDIO" -> "配音";
            case "TEXT" -> "文案";
            default -> "图片" + (Integer.parseInt(component.substring(6)) + 1);
        };
        String suffix = "_" + role + "_" + TIME.format(createdAt) + "_" + assetId + extension;
        String cleaned = clean(title);
        if (cleaned.isBlank()) cleaned = "智能素材";
        int available = MAX_BYTES - suffix.getBytes(StandardCharsets.UTF_8).length;
        var prefix = new StringBuilder();
        for (int point : cleaned.codePoints().toArray()) {
            String character = new String(Character.toChars(point));
            int bytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > available) break;
            prefix.append(character);
            available -= bytes;
        }
        return prefix + suffix;
    }

    private static String clean(String title) {
        if (title == null) return "";
        var result = new StringBuilder();
        title.codePoints().forEach(point -> {
            if (Character.isISOControl(point) || Character.getType(point) == Character.FORMAT
                    || Character.isWhitespace(point) || "/\\:*?\"<>|.".indexOf(point) >= 0) {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != '_') result.append('_');
            } else result.appendCodePoint(point);
        });
        return result.toString().replaceAll("_+$", "");
    }
}
