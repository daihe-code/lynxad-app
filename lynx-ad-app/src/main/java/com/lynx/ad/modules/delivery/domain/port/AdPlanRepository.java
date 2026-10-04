package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import java.util.List;
import java.util.Optional;

public interface AdPlanRepository {

    void save(AdPlan plan);

    Optional<AdPlan> findById(TenantId tenantId, PlanId id);

    boolean existsUnfinishedName(TenantId tenantId, AwemeId awemeId, PlanName name, Optional<PlanId> exclude);

    Optional<AdPlan> findByQcPlanId(TenantId tenantId, QcPlanId qcPlanId);

    List<AdPlan> findByTenant(TenantId tenantId);

    List<AdPlan> findPendingAudit();

    Optional<AdPlan> findByIdOnly(PlanId id);
}
