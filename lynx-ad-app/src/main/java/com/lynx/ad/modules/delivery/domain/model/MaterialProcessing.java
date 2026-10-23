package com.lynx.ad.modules.delivery.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Public processing facts, independent of preview URL lifetime and of the legacy aggregate status. */
public record MaterialProcessing(String generationStatus,String storageStatus,String reviewStatus,String syncStatus,
                                 String stage,String message,boolean canRetrySync,List<Component> components) {
    public record Component(String component,String storageStatus,String reviewStatus,String syncStatus,String message) {}
    public static MaterialProcessing legacy(CreationState.Material material) {
        boolean ready="READY".equals(material.status);
        return new MaterialProcessing(material.engineMaterialId==null?"NOT_REQUIRED":"SUCCEEDED",
                ready?"SUCCEEDED":"UNKNOWN",ready?"SUCCEEDED":"UNKNOWN",ready?"SUCCEEDED":"UNKNOWN",
                ready?"READY":"UNKNOWN",ready?null:"历史处理信息待核验，请刷新状态。",false,List.of());
    }
    public static String generationResult(CreationState state) {
        if(state.regeneration!=null&&"UNCERTAIN".equals(state.regeneration.status))return "UNCERTAIN";
        if(Set.of("QUEUED","SUBMITTING","RUNNING","REVISING","UNCERTAIN").contains(state.generationStatus))return state.generationStatus;
        if("SUCCEEDED".equals(state.generationStatus))return "SUCCEEDED";
        if(state.regeneration!=null&&"FAILED".equals(state.regeneration.status))return "FAILED";
        if("FAILED".equals(state.generationStatus)&&state.engineJobId==null)return "FAILED";
        if(state.pendingAdditionId!=null&&state.generationAdditions.stream().anyMatch(r->r.id.equals(state.pendingAdditionId)&&"FAILED".equals(r.status)&&r.engineJobId==null&&r.materialId==null))return "FAILED";
        // Old addition rows may still contain the preceding batch's terminal summary.
        if(state.pendingAdditionId!=null&&!Objects.equals(state.engineJobId,state.engineTerminalJobId))return "UNKNOWN";
        return switch(Objects.toString(state.engineTerminalStatus,"")) {
            case "succeeded" -> state.generationCounts.stream().anyMatch(c->c.total()!=null&&c.completed()<c.total())?"UNKNOWN":"SUCCEEDED";
            case "partial_failed" -> "PARTIAL_FAILED";
            case "failed" -> "FAILED";
            case "cancelled" -> "CANCELLED";
            default -> "SUCCEEDED".equals(state.generationStatus)?"SUCCEEDED":"NONE".equals(state.generationStatus)?"NONE":"UNKNOWN";
        };
    }
}
