package com.lynx.ad.modules.tenant.domain.model;

/** Immutable generation outcome and its exact source task, independent of later plan edits. */
public record InboxResult(String outcome, String planName, String creationPlanId, String jobId,
        String operationId, String materialId, String materialType, int produced) {}
