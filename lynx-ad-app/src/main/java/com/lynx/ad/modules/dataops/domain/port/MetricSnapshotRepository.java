package com.lynx.ad.modules.dataops.domain.port;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import java.util.List;
import java.util.Optional;

public interface MetricSnapshotRepository {

    void append(MetricSnapshot snapshot);

    Optional<MetricSnapshot> latest(PlanId planId);

    Optional<SnapshotPair> latestPair(PlanId planId);

    List<MetricSnapshot> listAll();

    List<MetricSnapshot> listLatestByTenant(TenantId tenantId);
}
