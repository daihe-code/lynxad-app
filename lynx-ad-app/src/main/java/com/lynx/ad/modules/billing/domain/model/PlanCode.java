package com.lynx.ad.modules.billing.domain.model;

public enum PlanCode {
    BASIC(1, "基础版"),
    PROFESSIONAL(2, "专业版"),
    FLAGSHIP(3, "旗舰版");

    private final int rank;
    private final String displayName;

    PlanCode(int rank, String displayName) {
        this.rank = rank;
        this.displayName = displayName;
    }

    public int rank() {
        return rank;
    }

    public String displayName() {
        return displayName;
    }

    public boolean higherThan(PlanCode other) {
        return other != null && rank > other.rank;
    }

    public boolean lowerThan(PlanCode other) {
        return other != null && rank < other.rank;
    }

    public static PlanCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return PlanCode.valueOf(raw.trim().toUpperCase());
    }
}
