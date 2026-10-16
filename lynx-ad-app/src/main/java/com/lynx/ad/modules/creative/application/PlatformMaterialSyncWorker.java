package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refreshes the complete catalog in the background; requests never wait for the provider. */
@Component
public class PlatformMaterialSyncWorker {
    private static final Logger log = LoggerFactory.getLogger(PlatformMaterialSyncWorker.class);
    private final PlatformMaterialCatalog catalog;
    private final MaterialLibraryPort library;
    private final QianchuanCredentialFacade credentials;
    private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();

    public PlatformMaterialSyncWorker(PlatformMaterialCatalog catalog, MaterialLibraryPort library,
            QianchuanCredentialFacade credentials) {
        this.catalog = catalog; this.library = library; this.credentials = credentials;
    }

    @Scheduled(fixedDelayString = "${lynx.ad.material-catalog.poll-ms:30000}")
    public void poll() {
        if (!running.compareAndSet(false, true)) return;
        try {
            catalog.discoverAccounts();
            var due = catalog.due(1);
            if (due.isEmpty() || !catalog.claim(due.getFirst())) { running.set(false); return; }
            var account = due.getFirst();
            executor.submit(() -> {
                boolean success = false;
                try { sync(account); success = true; }
                catch (Exception ex) { log.warn("Platform material catalog sync failed for account {} ({})",
                        account.accountId(), ex.getClass().getSimpleName()); }
                finally { try { catalog.finish(account, success); } finally { running.set(false); } }
            });
        } catch (RuntimeException ex) {
            running.set(false);
            log.warn("Platform material catalog poll failed");
        }
    }

    void sync(PlatformMaterialCatalog.Account account) {
        var tenant = new TenantId(account.tenantId());
        var credential = credentials.getUsableCredential(tenant, new AccountId(account.accountId()));
        int total = 0;
        var snapshot = new EnumMap<MaterialType, java.util.List<MaterialLibraryPort.PlatformMaterial>>(MaterialType.class);
        for (var type : MaterialType.values()) {
            var all = new ArrayList<MaterialLibraryPort.PlatformMaterial>();
            var seen = new HashSet<String>();
            var pageSignatures = new HashSet<String>();
            boolean complete = false;
            for (int page = 1; page <= 1000; page++) {
                var response = library.list(new MaterialLibraryPort.MaterialLibraryQuery(
                        credential.advertiserId().value(), type, null, null, page, 20, credential.accessToken()));
                var batch = response.items();
                String signature = batch.stream().map(PlatformMaterialCatalog::entryKey)
                        .collect(java.util.stream.Collectors.joining("|"));
                if (!batch.isEmpty() && !pageSignatures.add(signature))
                    throw new IllegalStateException("Provider repeated a catalog page");
                for (var material : batch) {
                    if (material.objectId() != null && seen.add(PlatformMaterialCatalog.entryKey(material))) all.add(material);
                }
                if (Boolean.FALSE.equals(response.hasMore()) || response.hasMore() == null && batch.size() < 20) {
                    complete = true; break;
                }
            }
            if (!complete) throw new IllegalStateException("Provider catalog page limit reached");
            snapshot.put(type, all);
            total += all.size();
        }
        catalog.replaceAll(tenant.value(), account.accountId(), credential.advertiserId().value(), snapshot);
        log.info("Platform material catalog synced {} entries for account {}", total, account.accountId());
    }

    @PreDestroy public void shutdown() { executor.shutdownNow(); }
}
