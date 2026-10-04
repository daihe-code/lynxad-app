package com.lynx.ad.support;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.dataops.domain.model.Ctr;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.model.Roi;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public final class WatcherFixture {

    private WatcherFixture() {}

    public static void append(
            MetricSnapshotRepository snapshots,
            String tenantId,
            String planId,
            String qcPlanId,
            Instant ts,
            String roi,
            String costYuan) {
        snapshots.append(MetricSnapshot.of(
                new TenantId(tenantId),
                new PlanId(planId),
                qcPlanId == null ? null : new QcPlanId(qcPlanId),
                ts,
                100,
                10,
                Money.ofYuan(new BigDecimal(costYuan)),
                1,
                Money.ofYuan(20),
                Roi.of(new BigDecimal(roi)),
                Ctr.of(new BigDecimal("0.1"))));
    }

    public static void setQcStatus(JdbcTemplate jdbc, String planId, String qcStatus) {
        jdbc.update("update ad_plan set qc_status = ? where id = ?", qcStatus, planId);
    }
}
