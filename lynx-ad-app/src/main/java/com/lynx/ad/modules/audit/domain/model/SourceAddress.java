package com.lynx.ad.modules.audit.domain.model;

public final class SourceAddress {

    private SourceAddress() {}

    public static String maskedForDisplay(String ip, String actorName) {
        if (actorName != null && "系统".equals(actorName)) {
            return "-";
        }
        if (ip == null || ip.isBlank()) {
            return "-";
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return "-";
    }
}
