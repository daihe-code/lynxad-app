package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanMaterialUsageStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded, leased read-only refresh. Library requests do not fan out to native APIs. */
@Component
public class PlanMaterialUsageSyncWorker {
    private final PlanMaterialUsageStore store;
    private final QianchuanCredentialFacade credentials;
    private final AdManagementPort management;
    private final java.util.concurrent.ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean running=new AtomicBoolean();
    public PlanMaterialUsageSyncWorker(PlanMaterialUsageStore store,QianchuanCredentialFacade credentials,AdManagementPort management) {
        this.store=store;this.credentials=credentials;this.management=management;
    }
    @Scheduled(fixedDelayString="${lynx.ad.material-usage.poll-ms:30000}")
    public void poll() {
        if(!running.compareAndSet(false,true))return;
        try {
            store.discover();
            var jobs=store.due(20);
            if(jobs.isEmpty()){running.set(false);return;}
            executor.submit(()->{
                try { for(var job:jobs)if(store.claim(job))sync(job); }
                finally { running.set(false); }
            });
        } catch(RuntimeException e) { running.set(false);LoggerFactory.getLogger(getClass()).warn("Plan material usage poll failed ({})",e.getClass().getSimpleName()); }
    }
    void sync(PlanMaterialUsageStore.Job job) {
        try {
            var credential=credentials.getUsableCredential(new TenantId(job.tenantId()),new AccountId(job.accountId()));
            if(!job.advertiserId().equals(credential.advertiserId().value()))throw new IllegalStateException("Advertiser changed");
            store.success(job,management.detail(credential,job.qcPlanId()));
        } catch(Exception e) {
            store.failure(job);
            LoggerFactory.getLogger(getClass()).warn("Plan material usage sync failed for plan {} ({})",job.planId(),e.getClass().getSimpleName());
        }
    }
    @PreDestroy public void shutdown(){executor.shutdownNow();}
}
