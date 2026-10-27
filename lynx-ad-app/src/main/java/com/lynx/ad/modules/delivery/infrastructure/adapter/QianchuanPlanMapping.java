package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class QianchuanPlanMapping {
    private QianchuanPlanMapping() {}
    public static JsonNode info(JsonNode row) { return row.has("ad_info") ? row.path("ad_info") : row; }
    public static JsonNode setting(JsonNode row) { return row.has("delivery_setting") ? row.path("delivery_setting") : info(row); }
    public static String text(JsonNode node, String key) { return node.path(key).isNull() || node.path(key).isMissingNode() ? null : node.path(key).asText(); }
    /** anchor_id in the list response is a different identifier; never use it as aweme_id. */
    public static String awemeId(JsonNode row) {
        if(row==null) return null;
        String id=text(row,"aweme_id");
        if(id==null) id=text(info(row),"aweme_id");
        if(id!=null && id.matches("[1-9][0-9]{0,18}")) return id;
        var ids=new java.util.HashSet<String>();
        for(var room:row.path("room_info")) {
            String value=text(room,"aweme_id");
            if(value!=null && value.matches("[1-9][0-9]{0,18}")) ids.add(value);
        }
        return ids.size()==1?ids.iterator().next():null;
    }
    public static String anchorText(JsonNode row,String field) {
        if(row==null || !row.path("room_info").isArray() || row.path("room_info").size()!=1) return null;
        String value=text(row.path("room_info").get(0),field);
        return value==null || value.isBlank()?null:value;
    }
    public static BigDecimal number(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isObject()) value = value.has("Value") ? value.path("Value") : value.path("value");
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank() || "--".equals(value.asText())) return null;
        try { return new BigDecimal(value.asText()); } catch (NumberFormatException ignored) { return null; }
    }
    public static String id(JsonNode row) {
        JsonNode n = info(row);
        return n.hasNonNull("ad_id") ? n.path("ad_id").asText() : text(n, "id");
    }
    public static PlanStatus status(String raw) {
        if (raw == null) return PlanStatus.UNKNOWN;
        return switch (raw) {
            case "DELIVERY_OK", "DELIVERING" -> PlanStatus.DELIVERING;
            case "DISABLE", "PAUSED" -> PlanStatus.PAUSED;
            case "AUDIT", "REAUDIT", "PENDING_AUDIT" -> PlanStatus.PENDING_AUDIT;
            case "AUDIT_STATUS_ERROR", "OFFLINE_AUDIT", "REJECTED" -> PlanStatus.REJECTED;
            case "TIME_DONE", "FINISHED" -> PlanStatus.FINISHED;
            case "DELETE", "DELETED" -> PlanStatus.DELETED;
            case "DRAFT", "CREATE" -> PlanStatus.DRAFT;
            default -> PlanStatus.UNKNOWN;
        };
    }
    public static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(value.replace('T', ' '), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant(); } catch (Exception ignored) { return null; }
    }
    public static LocalDate date(String value) {
        if (value == null || value.length() < 10) return null;
        try { return LocalDate.parse(value.substring(0, 10)); } catch (Exception ignored) { return null; }
    }
}
