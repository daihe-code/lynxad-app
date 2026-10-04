package com.lynx.ad.common.web;

public final class OceanIdJson {

    private OceanIdJson() {}

    public static String toOceanNumberLiteral(String id) {
        if (id == null || id.isBlank()) {
            return "0";
        }
        return id.trim();
    }

    public static String fromOcean(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof Number n) {
            return Long.toString(n.longValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.endsWith(".0") && text.chars().filter(ch -> ch == '.').count() == 1) {
            text = text.substring(0, text.length() - 2);
        }
        return text;
    }
}
