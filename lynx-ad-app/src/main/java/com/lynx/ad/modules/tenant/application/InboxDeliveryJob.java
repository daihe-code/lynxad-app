package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.audit.application.*;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.tenant.infrastructure.persistence.InboxOutboxStore;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InboxDeliveryJob {
    private static final Logger log=LoggerFactory.getLogger(InboxDeliveryJob.class);
    private final InboxOutboxStore store;private final AuditRecorder audit;
    public InboxDeliveryJob(InboxOutboxStore store,AuditRecorder audit){this.store=store;this.audit=audit;}
    @Scheduled(fixedDelayString="${lynx.ad.schedule.inbox-delivery-ms:30000}")
    public void deliverDue(){deliver(null,50);}
    /** Bounded due-event recovery before the existing inbox read on login/open. No external sends. */
    public void recover(TenantId tenant){
        java.util.Objects.requireNonNull(tenant);
        // Do not read/claim the caller's uncommitted source event in a new transaction.
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())deliver(tenant,10);
    }
    /** A successful login may advance a delayed retry, with a shared 30-second per-event cooldown. */
    public void recoverAfterLogin(TenantId tenant){
        java.util.Objects.requireNonNull(tenant);
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())deliver(tenant,10,true);
    }
    private void deliver(TenantId tenant,int limit) {
        deliver(tenant,limit,false);
    }
    private void deliver(TenantId tenant,int limit,boolean login) {
        try {
            for(String id:login?store.due(tenant,limit,true):store.due(tenant,limit)) {
                var claim=(login?store.claim(id,tenant,true):store.claim(id,tenant)).orElse(null);if(claim==null)continue;
                java.util.Optional<InboxOutboxStore.Delivered> sent;
                try {
                    sent=store.deliver(claim);
                }catch(RuntimeException failure){
                    try{store.retry(claim);}catch(RuntimeException unavailable){/* The persisted lease permits recovery after restart or DB recovery. */}
                    log.warn("inbox event delivery deferred event={}",claim.id());
                    continue;
                }
                sent.ifPresent(m->{try{audit.record(new SuccessAuditCommand(new TenantId(m.tenant()),null,"系统",AuditActionType.SYSTEM_NOTICE,"INBOX",m.notificationId(),m.title(),null,null,null,null));}catch(RuntimeException unavailable){log.warn("inbox delivery audit not recorded event={}",claim.id());}});
            }
        }catch(RuntimeException unavailable){log.warn("inbox outbox temporarily unavailable");}
    }
}
