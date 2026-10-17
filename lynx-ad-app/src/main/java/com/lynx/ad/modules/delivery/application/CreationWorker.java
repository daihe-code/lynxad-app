package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CreationWorker {
    private final CreationStore store;
    private final CreationWorkflowService service;
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final Semaphore slots=new Semaphore(2);
    private final ConcurrentMap<String,CreationState> running=new ConcurrentHashMap<>();
    public CreationWorker(CreationStore store,CreationWorkflowService service){this.store=store;this.service=service;}
    @Scheduled(fixedDelayString="${lynx.ad.creation.poll-ms:5000}")
    public void poll(){
        for(var a:store.receipts())service.accepted(a,a.qcId());
        for(var a:store.queued())if(slots.tryAcquire())executor.submit(()->{try{service.send(a);}finally{slots.release();}});
        for(var a:store.staleSending())service.failed(a,"UNCERTAIN","创建请求中断，平台结果待确认，请勿重复发布");
        for(var s:store.due()) {
            if(!Set.of("NONE","FAILED").contains(s.publishStatus))continue;
            boolean work=Set.of("QUEUED","RUNNING").contains(s.analysisStatus)||Set.of("QUEUED","SUBMITTING","REVISING","UNCERTAIN","RUNNING","IMPORTING").contains(s.generationStatus)||s.needsGenerationReconciliation()||s.materials.stream().anyMatch(m->"PROCESSING".equals(m.status));
            if(!work||!slots.tryAcquire())continue;
            if(!store.claim(s)){slots.release();continue;}
            running.put(s.planId,s);
            executor.submit(()->{try{service.process(s);}catch(RuntimeException ignored){/* Persisted task IDs allow the next poll to resume a read, never a blind create. */}finally{running.remove(s.planId);store.release(s);slots.release();}});
        }
    }
    @Scheduled(fixedDelay=30000) public void heartbeat(){running.values().forEach(store::heartbeat);}
    @PreDestroy public void stop(){executor.shutdownNow();}
}
