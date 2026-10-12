package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

public interface AdPlanRepository {

    void save(AdPlan plan);

    boolean discardDraft(TenantId tenantId, PlanId id);

    Optional<AdPlan> findById(TenantId tenantId, PlanId id);

    default Optional<AdPlan> findByIdForUpdate(TenantId tenantId, PlanId id) {
        return findById(tenantId, id);
    }

    boolean existsUnfinishedName(TenantId tenantId, AwemeId awemeId, PlanName name, Optional<PlanId> exclude);

    Optional<AdPlan> findByQcPlanId(TenantId tenantId, QcPlanId qcPlanId);

    List<AdPlan> findByTenant(TenantId tenantId);

    default Map<String, ListMetadata> listMetadataByTenant(TenantId tenantId) {
        return Map.of();
    }

    record ListMetadata(Instant createdAt, String deliveryPlatform) {}

    List<AdPlan> findPendingAudit();

    Optional<AdPlan> findByIdOnly(PlanId id);
}
