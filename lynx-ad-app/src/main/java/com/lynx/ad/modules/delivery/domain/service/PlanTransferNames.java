package com.lynx.ad.modules.delivery.domain.service;

import java.util.regex.Pattern;

public final class PlanTransferNames {
    public static final String SUFFIX = "_转投";
    private static final int DISPLAY_NAME_MAX = 120; // delivery_transfer_rebuild.target_name
    private static final String LEGACY_VOLUME_SUFFIX = "_转投放量";
    private static final String LEGACY_COST_SUFFIX = "_转投控成本";
    private static final Pattern INSTANCE_SUFFIX = Pattern.compile("_[0-9a-f]{12}$");

    private PlanTransferNames() {}

    public static String defaultName(String source) {
        return appendSuffix(source, SUFFIX, DISPLAY_NAME_MAX);
    }

    public static String defaultPlatformName(String source) {
        return appendSuffix(source, SUFFIX, DeliveryInputPolicy.RULES.nameMaxLength());
    }

    public static String editedPlatformName(String name) {
        return name.contains("转投") ? name : defaultPlatformName(name);
    }

    public static String editedName(String name) {
        return name.contains("转投") ? name : defaultName(name);
    }

    public static boolean mayNeedRecovery(String name) {
        return INSTANCE_SUFFIX.matcher(name).find()
                || name.endsWith(LEGACY_VOLUME_SUFFIX) || name.endsWith(LEGACY_COST_SUFFIX);
    }

    public static boolean isLegacyDefault(String source, String name) {
        int limit = DeliveryInputPolicy.RULES.nameMaxLength();
        return name.equals(appendSuffix(source, LEGACY_VOLUME_SUFFIX, limit))
                || name.equals(appendSuffix(source, LEGACY_COST_SUFFIX, limit));
    }

    private static String appendSuffix(String name, String suffix, int limit) {
        int end = Math.min(name.length(), limit - suffix.length());
        if (end < name.length() && Character.isHighSurrogate(name.charAt(end - 1))) end--;
        return name.substring(0, end).stripTrailing() + suffix;
    }
}
