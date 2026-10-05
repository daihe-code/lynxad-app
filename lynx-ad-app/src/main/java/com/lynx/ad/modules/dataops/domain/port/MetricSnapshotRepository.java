package com.lynx.ad.modules.dataops.domain.port;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public interface MetricSnapshotRepository {

    void append(MetricSnapshot snapshot);

    Optional<MetricSnapshot> latest(PlanId planId);

    Optional<SnapshotPair> latestPair(PlanId planId);

    List<MetricSnapshot> listAll();

    List<MetricSnapshot> listLatestByTenant(TenantId tenantId);

    default Map<String, SnapshotPair> listLatestPairsByTenant(TenantId tenantId) {
        Map<String, SnapshotPair> pairs = new LinkedHashMap<>();
        for (MetricSnapshot snapshot : listLatestByTenant(tenantId)) {
            if (snapshot.planId() != null) {
                latestPair(snapshot.planId()).ifPresent(pair -> pairs.put(snapshot.planId().value(), pair));
            }
        }
        return pairs;
    }
}
