package com.lynx.ad.modules.agentops.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.domain.Ids.ActionId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.agentops.domain.model.ActionType;
import com.lynx.ad.modules.agentops.domain.model.AgentAction;
import com.lynx.ad.modules.agentops.domain.port.AgentActionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class MpAgentActionRepository implements AgentActionRepository {

    private final AgentActionMapper mapper;
    private final ObjectMapper objectMapper;

    public MpAgentActionRepository(AgentActionMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveIgnoreDup(AgentAction action) {
        AgentActionPo po = new AgentActionPo();
        po.setId(action.id().value());
        po.setTenantId(action.tenantId().value());
        po.setAdPlanId(action.planId().value());
        po.setActionType(action.type().name());
        po.setBeforeJson(asJsonb(action.beforeJson()));
        po.setAfterJson(envelope(action.summary(), action.afterJson()));
        po.setSnapshotTs(action.snapshotTs());
        po.setResult(action.result());
        po.setIdempotencyKey(action.idempotencyKey());
        po.setRecoveredAt(action.recoveredAt());
        try {
            mapper.insert(po);
        } catch (DataIntegrityViolationException ignored) {
            // same-snapshot UK; OPEN dedup is application-layer findOpen
        }
    }

    @Override
    public List<AgentAction> listByPlan(TenantId tenantId, PlanId planId) {
        return mapper
                .selectList(new LambdaQueryWrapper<AgentActionPo>()
                        .eq(AgentActionPo::getTenantId, tenantId.value())
                        .eq(AgentActionPo::getAdPlanId, planId.value())
                        .orderByDesc(AgentActionPo::getSnapshotTs))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<AgentAction> listByTenant(TenantId tenantId) {
        return mapper
                .selectList(new LambdaQueryWrapper<AgentActionPo>()
                        .eq(AgentActionPo::getTenantId, tenantId.value())
                        .orderByDesc(AgentActionPo::getSnapshotTs))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<AgentAction> findOpen(TenantId tenantId, PlanId planId, ActionType type) {
        AgentActionPo po = mapper.selectOne(new LambdaQueryWrapper<AgentActionPo>()
                .eq(AgentActionPo::getTenantId, tenantId.value())
                .eq(AgentActionPo::getAdPlanId, planId.value())
                .eq(AgentActionPo::getActionType, type.name())
                .eq(AgentActionPo::getResult, "ALERT")
                .isNull(AgentActionPo::getRecoveredAt)
                .last("limit 1"));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void markRecovered(ActionId id, Instant recoveredAt) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AgentActionPo>()
                        .eq(AgentActionPo::getId, id.value())
                        .set(AgentActionPo::getRecoveredAt, recoveredAt));
    }

    private AgentAction toDomain(AgentActionPo po) {
        String afterRaw = po.getAfterJson();
        String summary = po.getActionType();
        String afterPayload = afterRaw;
        try {
            JsonNode node = afterRaw == null || afterRaw.isBlank() ? null : objectMapper.readTree(afterRaw);
            if (node != null && node.isObject() && node.has("summary")) {
                summary = node.path("summary").asText(summary);
                afterPayload = node.has("after") ? node.path("after").asText(null) : null;
            }
        } catch (Exception ignored) {
            // keep raw
        }
        return new AgentAction(
                new ActionId(po.getId()),
                new TenantId(po.getTenantId()),
                new PlanId(po.getAdPlanId()),
                ActionType.valueOf(po.getActionType()),
                po.getSnapshotTs(),
                summary,
                po.getBeforeJson(),
                afterPayload,
                po.getResult(),
                po.getRecoveredAt());
    }

    private String envelope(String summary, String afterPayload) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("summary", summary == null ? "" : summary);
        if (afterPayload != null && !afterPayload.isBlank()) {
            node.put("after", afterPayload);
        }
        return node.toString();
    }

    private String asJsonb(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
            return trimmed;
        }
        return objectMapper.createObjectNode().put("value", raw).toString();
    }
}
