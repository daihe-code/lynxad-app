package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.api.dto.TrialVo;
import com.lynx.ad.modules.billing.domain.model.Trial;
import com.lynx.ad.modules.billing.domain.model.TrialWindow;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.billing.domain.port.TrialRepository;
import com.lynx.ad.modules.billing.domain.service.TrialGuardPolicy;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class BillingTrialService {

    private final TrialRepository trialRepository;
    private final SubscriptionSupport subscriptionSupport;
    private final TrialGuardPolicy guardPolicy = new TrialGuardPolicy();

    public BillingTrialService(TrialRepository trialRepository, SubscriptionSupport subscriptionSupport) {
        this.trialRepository = trialRepository;
        this.subscriptionSupport = subscriptionSupport;
    }

    public TrialVo start(TenantId tenantId, Instant now) {
        return trialRepository
                .findByTenant(tenantId)
                .map(t -> toVo(t, now))
                .orElseGet(() -> {
                    Trial created = Trial.start(tenantId, now, TrialWindow.threeDays());
                    trialRepository.save(created);
                    return toVo(created, now);
                });
    }

    /** Dev seed only. Does not renew an active trial and does not call assertNotRestarted. */
    public void reopenExpiredForDevSeed(TenantId tenantId, Instant now) {
        if (trialRepository.findByTenant(tenantId).filter(trial -> trial.isActive(now)).isPresent()) {
            return;
        }
        trialRepository.save(Trial.start(tenantId, now, TrialWindow.threeDays()));
    }

    public TrialVo current(TenantId tenantId, Instant now) {
        return trialRepository.findByTenant(tenantId).map(t -> toVo(t, now)).orElse(null);
    }

    public void assertWritable(TenantId tenantId, Instant now, WriteAction action) {
        Trial trial = trialRepository.findByTenant(tenantId).orElse(null);
        var sub = subscriptionSupport.effective(tenantId, now).orElse(null);
        guardPolicy.assertWritable(trial, sub, now, action);
    }

    private static TrialVo toVo(Trial trial, Instant now) {
        return new TrialVo(trial.startedAt(), trial.expiresAt(), trial.isActive(now));
    }
}
