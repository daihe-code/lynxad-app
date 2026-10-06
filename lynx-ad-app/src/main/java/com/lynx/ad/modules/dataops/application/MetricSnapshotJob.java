package com.lynx.ad.modules.dataops.application;

import com.lynx.ad.agents.watcher.WatcherJob;
import com.lynx.ad.modules.dataops.domain.model.Ctr;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.model.Roi;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.dataops.domain.port.ReportPullPort;
import com.lynx.ad.modules.dataops.domain.service.AmountConversion;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.tenant.application.TenantQuerySupport;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MetricSnapshotJob {

    private final TenantQuerySupport tenantQuery;
    private final AdPlanRepository planRepository;
    private final MetricSnapshotRepository snapshotRepository;
    private final ReportPullPort reportPullPort;
    private final WatcherJob watcherJob;
    private final AmountConversion conversion = new AmountConversion();
    private com.lynx.ad.common.config.LynxAdProperties properties;
    private com.lynx.ad.modules.delivery.application.PlanSyncService syncService;
    @org.springframework.beans.factory.annotation.Autowired
    public void managementSync(com.lynx.ad.common.config.LynxAdProperties properties,
            com.lynx.ad.modules.delivery.application.PlanSyncService syncService) {
        this.properties = properties; this.syncService = syncService;
    }

    public MetricSnapshotJob(
            TenantQuerySupport tenantQuery,
            AdPlanRepository planRepository,
            MetricSnapshotRepository snapshotRepository,
            ReportPullPort reportPullPort,
            WatcherJob watcherJob) {
        this.tenantQuery = tenantQuery;
        this.planRepository = planRepository;
        this.snapshotRepository = snapshotRepository;
        this.reportPullPort = reportPullPort;
        this.watcherJob = watcherJob;
    }

    @Scheduled(fixedDelayString = "${lynx.ad.schedule.metric-snapshot-ms:300000}")
    public void snapshot() {
        Instant now = Instant.now();
        for (Tenant tenant : tenantQuery.findAll()) {
            if (properties != null && !properties.getMock().isQianchuan()) {
                syncService.sync(tenant.id(), null);
            }
            for (AdPlan plan : planRepository.findByTenant(tenant.id())) {
                if (plan.status() != PlanStatus.DELIVERING || plan.source() == com.lynx.ad.modules.delivery.domain.model.PlanSource.EXTERNAL) {
                    continue;
                }
                try {
                ReportPullPort.RawOceanReport raw = reportPullPort.pull(plan);
                snapshotRepository.append(MetricSnapshot.of(
                        plan.tenantId(),
                        plan.id(),
                        plan.qcPlanId().orElse(null),
                        now,
                        raw.impression(),
                        raw.click(),
                        conversion.toYuan(raw.costOcean()),
                        raw.convert(),
                        conversion.toYuan(raw.gmvOcean()),
                        Roi.of(raw.roi()),
                        Ctr.of(raw.ctr())));
                watcherJob.afterSnapshot(plan.id());
                } catch (RuntimeException ex) {
                    org.slf4j.LoggerFactory.getLogger(MetricSnapshotJob.class).warn("snapshot unavailable plan={} reason={}",plan.id().value(),ex.getMessage());
                }
            }
        }
    }
}
