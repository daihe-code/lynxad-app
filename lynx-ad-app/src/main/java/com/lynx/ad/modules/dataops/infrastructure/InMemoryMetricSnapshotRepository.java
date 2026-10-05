package com.lynx.ad.modules.dataops.infrastructure;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.dataops.domain.port.SnapshotPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
        return listLatestPairsByTenant(tenantId).values().stream().map(SnapshotPair::current).toList();
    }

    @Override
    public synchronized Map<String, SnapshotPair> listLatestPairsByTenant(TenantId tenantId) {
        Map<String, SnapshotPair> pairs = new LinkedHashMap<>();
        rows.stream().filter(s -> s.tenantId().equals(tenantId) && s.planId() != null)
                .sorted(Comparator.comparing(MetricSnapshot::ts).reversed())
                .forEach(snapshot -> {
                    String id = snapshot.planId().value();
                    SnapshotPair pair = pairs.get(id);
                    if (pair == null) pairs.put(id, new SnapshotPair(snapshot, null));
                    else if (pair.previous() == null && snapshot.ts().isBefore(pair.current().ts()))
                        pairs.put(id, new SnapshotPair(pair.current(), snapshot));
                });
        return pairs;
    }
}
