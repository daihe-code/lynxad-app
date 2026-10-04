package com.lynx.ad.domain.agentops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.agentops.domain.model.ActionType;
import com.lynx.ad.modules.agentops.domain.model.WatcherFinding;
import com.lynx.ad.modules.agentops.domain.service.WatcherRuleEngine;
import com.lynx.ad.modules.dataops.domain.model.Ctr;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.model.Roi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoiDropRuleTest {

    private final WatcherRuleEngine engine = new WatcherRuleEngine();

    private MetricSnapshot snap(String roi, String cost, Instant ts) {
        return MetricSnapshot.of(
                new TenantId("t1"),
                new PlanId("p1"),
                new QcPlanId("qc1"),
                ts,
                100,
                10,
                Money.ofYuan(new BigDecimal(cost)),
                1,
                Money.ofYuan(20),
                Roi.of(new BigDecimal(roi)),
                Ctr.of(new BigDecimal("0.1")));
    }

    @Test
    void thirtyPercentBoundary() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t1 = t0.plusSeconds(300);
        List<WatcherFinding> exact = engine.evaluate(
                snap("0.70", "10", t1), Optional.of(snap("1.00", "10", t0)), Optional.of(Money.ofYuan(100)), false);
        assertFalse(hit(exact, ActionType.ROI_DROP));
        List<WatcherFinding> over = engine.evaluate(
                snap("0.69", "10", t1), Optional.of(snap("1.00", "10", t0)), Optional.of(Money.ofYuan(100)), false);
        assertTrue(hit(over, ActionType.ROI_DROP));
    }

    @Test
    void budgetNinetyPercentBoundary() {
        Instant ts = Instant.parse("2026-01-01T00:00:00Z");
        assertFalse(hit(
                engine.evaluate(snap("1", "90", ts), Optional.empty(), Optional.of(Money.ofYuan(100)), false),
                ActionType.BUDGET_EXHAUST));
        assertTrue(hit(
                engine.evaluate(snap("1", "90.01", ts), Optional.empty(), Optional.of(Money.ofYuan(100)), false),
                ActionType.BUDGET_EXHAUST));
    }

    @Test
    void pauseBoundary() {
        Instant ts = Instant.parse("2026-01-01T00:00:00Z");
        assertTrue(hit(
                engine.evaluate(snap("1", "10", ts), Optional.empty(), Optional.of(Money.ofYuan(100)), true),
                ActionType.QC_ABNORMAL_PAUSE));
        assertFalse(hit(
                engine.evaluate(snap("1", "10", ts), Optional.empty(), Optional.of(Money.ofYuan(100)), false),
                ActionType.QC_ABNORMAL_PAUSE));
    }

    private static boolean hit(List<WatcherFinding> findings, ActionType type) {
        return findings.stream().anyMatch(f -> f.type() == type && f.hit());
    }
}
