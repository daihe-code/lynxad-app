package com.lynx.ad.modules.delivery.domain.model;

public enum PlanTransition {
    PUBLISH,
    ACCEPT_QC_ID,
    AUDIT_PASS,
    AUDIT_REJECT,
    DISABLE,
    ENABLE,
    FINISH,
    REVERT_TO_DRAFT
}
