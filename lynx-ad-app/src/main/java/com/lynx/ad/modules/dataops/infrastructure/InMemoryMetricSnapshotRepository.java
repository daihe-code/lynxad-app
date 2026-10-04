package com.lynx.ad.modules.dataops.infrastructure;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.dataops.domain.port.SnapshotPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
public class InMemoryMetricSnapshotRepository implements MetricSnapshotRepository {

    private final List<MetricSnapshot> rows = new ArrayList<>();

    @Override
    public synchronized void append(MetricSnapshot snapshot) {
        rows.add(snapshot);
    }

    @Override
    public synchronized Optional<MetricSnapshot> latest(PlanId planId) {
        return rows.stream()
                .filter(s -> s.planId() != null && s.planId().equals(planId))
                .max(Comparator.comparing(MetricSnapshot::ts));
    }

    @Override
    public synchronized Optional<SnapshotPair> latestPair(PlanId planId) {
        List<MetricSnapshot> list = rows.stream()
                .filter(s -> s.planId() != null && s.planId().equals(planId))
                .sorted(Comparator.comparing(MetricSnapshot::ts).reversed())
                .toList();
        if (list.isEmpty()) {
            return Optional.empty();
        }
        MetricSnapshot prev = list.size() > 1 ? list.get(1) : null;
        return Optional.of(new SnapshotPair(list.get(0), prev));
    }

    @Override
    public synchronized List<MetricSnapshot> listAll() {
        return List.copyOf(rows);
    }

    @Override
    public synchronized List<MetricSnapshot> listLatestByTenant(TenantId tenantId) {
        return rows.stream().filter(s -> s.tenantId().equals(tenantId)).toList();
    }
}
