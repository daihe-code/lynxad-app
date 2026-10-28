package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.infrastructure.persistence.DraftExpiryStore;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DraftExpiryWorker {
    private final DraftExpiryStore store;
    public DraftExpiryWorker(DraftExpiryStore store) { this.store=store; }
    @Scheduled(initialDelayString="${lynx.ad.draft-expiry.initial-delay-ms:10000}",fixedDelayString="${lynx.ad.draft-expiry.poll-ms:60000}")
    public void poll() {
        try { store.expireDue(null,Instant.now(),200); }
        catch(RuntimeException e) { LoggerFactory.getLogger(getClass()).warn("Draft expiration failed ({})",e.getClass().getSimpleName()); }
    }
}
