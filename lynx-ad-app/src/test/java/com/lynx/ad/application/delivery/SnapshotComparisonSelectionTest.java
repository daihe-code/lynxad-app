package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.infrastructure.InMemoryMetricSnapshotRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SnapshotComparisonSelectionTest {
    @Test
    void selectsDistinctSnapshotTimesWithoutCrossingTenantBoundary() {
        var repository = new InMemoryMetricSnapshotRepository();
        var tenant = new TenantId("tenant-1");
        var plan = new PlanId("plan-1");
        var now = Instant.parse("2026-10-05T08:00:00Z");
        repository.append(snapshot(tenant, plan, now.minusSeconds(300)));
        repository.append(snapshot(new TenantId("tenant-2"), plan, now.plusSeconds(300)));
        repository.append(snapshot(tenant, plan, now));
        repository.append(snapshot(tenant, plan, now));
        var pair = repository.listLatestPairsByTenant(tenant).get(plan.value());
        assertEquals(now, pair.current().ts());
        assertEquals(now.minusSeconds(300), pair.previous().ts());
        assertEquals(1, repository.listLatestByTenant(tenant).size());
    }

    private MetricSnapshot snapshot(TenantId tenant, PlanId plan, Instant ts) {
        return MetricSnapshot.of(tenant, plan, null, ts, 0, 0, Money.ofYuan(1), 1, Money.ofYuan(2), null, null);
    }
}
