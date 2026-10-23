package com.lynx.ad.modules.delivery.domain.model;

import java.util.List;

/** Checks of the selected product snapshot, never model-generated market metrics. */
public record ProductBasicAnalysis(String status, boolean canContinue, String checkedAt,
        String fingerprint, List<ProductCheck> products, List<Issue> issues) {
    public record ProductCheck(String productId, String productName, List<Issue> issues) {}
    public record Issue(String productId, String productName, String severity, String code, String message) {}
}
