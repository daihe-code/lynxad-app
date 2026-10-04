package com.lynx.ad.agents.watcher;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.modules.agentops.domain.model.AgentAction;
import com.lynx.ad.modules.agentops.domain.model.WatcherFinding;
import com.lynx.ad.modules.agentops.domain.port.AgentActionRepository;
import com.lynx.ad.modules.agentops.domain.service.WatcherRuleEngine;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WatcherJob {

    private final MetricSnapshotRepository snapshotRepository;
    private final AdPlanRepository planRepository;
    private final AgentActionRepository actionRepository;
    private final InboxCommandService inboxCommandService;
    private final WatcherRuleEngine engine = new WatcherRuleEngine();

    public WatcherJob(
            MetricSnapshotRepository snapshotRepository,
            AdPlanRepository planRepository,
            AgentActionRepository actionRepository,
            InboxCommandService inboxCommandService) {
        this.snapshotRepository = snapshotRepository;
        this.planRepository = planRepository;
        this.actionRepository = actionRepository;
        this.inboxCommandService = inboxCommandService;
    }

    public void afterSnapshot(PlanId planId) {
        var pair = snapshotRepository.latestPair(planId).orElse(null);
        if (pair == null) {
            return;
        }
        var plan = planRepository.findByIdOnly(planId).orElse(null);
        if (plan == null) {
            return;
        }
        boolean pause = "ABNORMAL_PAUSE".equals(plan.qcStatus());
        for (WatcherFinding f : engine.evaluate(
                pair.current(),
                Optional.ofNullable(pair.previous()),
                Optional.ofNullable(plan.setting().dailyBudget()),
                pause)) {
            var open = actionRepository.findOpen(plan.tenantId(), plan.id(), f.type());
            if (f.hit()) {
                if (open.isEmpty()) {
                    AgentAction action = AgentAction.recordAlert(
                            plan.tenantId(), plan.id(), f.type(), pair.current().ts(), f.summary(), null);
                    actionRepository.saveIgnoreDup(action);
                    inboxCommandService.post(
                            plan.tenantId(), "盯盘告警", f.summary(), new BizRef(f.type().name(), plan.id().value()));
                }
            } else if (open.isPresent()) {
                actionRepository.markRecovered(open.get().id(), Instant.now());
            }
        }
    }
}
