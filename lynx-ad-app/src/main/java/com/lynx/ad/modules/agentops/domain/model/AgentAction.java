package com.lynx.ad.modules.agentops.domain.model;

import com.lynx.ad.common.domain.Ids.ActionId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import java.time.Instant;
import java.util.UUID;

public class AgentAction {

    private static final java.util.Set<ActionType> WATCHER_TYPES = java.util.Set.of(
            ActionType.ROI_DROP, ActionType.BUDGET_EXHAUST, ActionType.QC_ABNORMAL_PAUSE);

    private final ActionId id;
    private final TenantId tenantId;
    private final PlanId planId;
    private final ActionType type;
    private final Instant snapshotTs;
    private final String summary;
    private final String beforeJson;
    private final String afterJson;
    private final String result;
    private final Instant recoveredAt;

    public AgentAction(
            ActionId id,
            TenantId tenantId,
            PlanId planId,
            ActionType type,
            Instant snapshotTs,
            String summary,
            String beforeJson,
            String afterJson,
            String result,
            Instant recoveredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.planId = planId;
        this.type = type;
        this.snapshotTs = snapshotTs;
        this.summary = summary;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.result = result;
        this.recoveredAt = recoveredAt;
    }

    public static AgentAction recordAlert(
            TenantId tenantId, PlanId planId, ActionType type, Instant snapshotTs, String summary, String beforeJson) {
        return new AgentAction(
                new ActionId(UUID.randomUUID().toString()),
                tenantId,
                planId,
                type,
                snapshotTs,
                summary,
                beforeJson,
                null,
                "ALERT",
                null);
    }

    public static AgentAction recordManual(
            TenantId tenantId, PlanId planId, ActionType type, String beforeJson, String afterJson) {
        return recordManual(tenantId, planId, type, beforeJson, afterJson, type.name());
    }

    public static AgentAction recordManual(
            TenantId tenantId, PlanId planId, ActionType type, String beforeJson, String afterJson, String summary) {
        return new AgentAction(
                new ActionId(UUID.randomUUID().toString()),
                tenantId,
                planId,
                type,
                Instant.now(),
                summary,
                beforeJson,
                afterJson,
                "DONE",
                null);
    }

    public ActionId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PlanId planId() {
        return planId;
    }

    public ActionType type() {
        return type;
    }

    public Instant snapshotTs() {
        return snapshotTs;
    }

    public String summary() {
        return summary;
    }

    public String beforeJson() {
        return beforeJson;
    }

    public String afterJson() {
        return afterJson;
    }

    public String result() {
        return result;
    }

    public Instant recoveredAt() {
        return recoveredAt;
    }

    public boolean open() {
        return recoveredAt == null && WATCHER_TYPES.contains(type);
    }

    public String idempotencyKey() {
        return planId.value() + ":" + type.name() + ":" + snapshotTs.toEpochMilli();
    }
}
