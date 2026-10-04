package com.lynx.ad.modules.agentops.domain.port;

import com.lynx.ad.common.domain.Ids.ActionId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.agentops.domain.model.ActionType;
import com.lynx.ad.modules.agentops.domain.model.AgentAction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentActionRepository {

    void saveIgnoreDup(AgentAction action);

    List<AgentAction> listByPlan(TenantId tenantId, PlanId planId);

    List<AgentAction> listByTenant(TenantId tenantId);

    Optional<AgentAction> findOpen(TenantId tenantId, PlanId planId, ActionType type);

    void markRecovered(ActionId id, Instant recoveredAt);
}
