package com.lynx.ad.modules.dataops.application;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.lynx.ad.agents.watcher.WatcherJob;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.dataops.domain.model.Ctr;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.model.Roi;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.dataops.domain.port.ReportPullPort;
import com.lynx.ad.modules.dataops.domain.service.AmountConversion;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.identity.application.DemoLiveCredentialRules;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import com.lynx.ad.modules.identity.application.OauthQueryService.OauthAccountSnapshot;
import com.lynx.ad.modules.tenant.application.TenantQuerySupport;
import com.lynx.ad.modules.tenant.domain.model.Tenant;

@Component
public class MetricSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(MetricSnapshotJob.class);

    private final TenantQuerySupport tenantQuery;
    private final AdPlanRepository planRepository;
    private final MetricSnapshotRepository snapshotRepository;
    private final ReportPullPort reportPullPort;
    private final WatcherJob watcherJob;
    private final LynxAdProperties properties;
    private final OauthQueryService oauthQuery;
    private final AmountConversion conversion = new AmountConversion();
    private com.lynx.ad.modules.delivery.application.PlanSyncService syncService;
    @org.springframework.beans.factory.annotation.Autowired
    public void managementSync(LynxAdProperties properties,
            com.lynx.ad.modules.delivery.application.PlanSyncService syncService) {
        this.syncService = syncService;
    }

    public MetricSnapshotJob(
            TenantQuerySupport tenantQuery,
            AdPlanRepository planRepository,
            MetricSnapshotRepository snapshotRepository,
            ReportPullPort reportPullPort,
            WatcherJob watcherJob,
            LynxAdProperties properties,
            OauthQueryService oauthQuery) {
        this.tenantQuery = tenantQuery;
        this.planRepository = planRepository;
        this.snapshotRepository = snapshotRepository;
        this.reportPullPort = reportPullPort;
        this.watcherJob = watcherJob;
        this.properties = properties;
        this.oauthQuery = oauthQuery;
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
                if (!properties.getMock().isQianchuan() && skipLivePull(plan)) {
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

    private boolean skipLivePull(AdPlan plan) {
        String qcPlanId = plan.qcPlanId().map(id -> id.value()).orElse("");
        if (qcPlanId.isBlank() || qcPlanId.startsWith("mock_")) {
            return true;
        }
        if (plan.accountId() == null) {
            return true;
        }
        Optional<OauthAccountSnapshot> snapshot = oauthQuery.findSnapshot(plan.accountId());
        if (snapshot.isEmpty() || !"ACTIVE".equals(snapshot.get().status())) {
            return true;
        }
        return DemoLiveCredentialRules.isDemoAdvertiser(snapshot.get().advertiserId());
    }
}
