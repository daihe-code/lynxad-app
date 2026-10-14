package com.lynx.ad.modules.agentops.application;

import com.lynx.ad.modules.agentops.infrastructure.OptimizationStore;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OptimizationMonitoringJob {
    private final OptimizationStore store;
    private final OptimizationMonitoringService service;
    public OptimizationMonitoringJob(OptimizationStore store,OptimizationMonitoringService service) {this.store=store;this.service=service;}
    @Scheduled(fixedDelayString="${lynx.ad.monitoring.scan-ms:10000}")
    public void scan() {
        for(var state:store.due(Instant.now())) {
            try {service.cycle(state);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("monitor cycle unavailable plan={} type={}",state.plan(),ex.getClass().getSimpleName());}
        }
    }
    @Scheduled(fixedDelayString="${lynx.ad.monitoring.retention-ms:3600000}")
    public void prune() {store.prune();}
}
