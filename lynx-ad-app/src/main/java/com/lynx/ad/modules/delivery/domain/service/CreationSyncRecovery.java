package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.model.MaterialProcessing;
import java.util.Set;

/** Sync failures belong to background workers, not the customer's generation controls. */
public final class CreationSyncRecovery {
    private CreationSyncRecovery() {}
    private static final long WINDOW=10*60_000L;
    public static boolean systemIssue(CreationState.Material m) {
        var p=m.processing;
        if(p!=null&&Set.of("REJECTED","FAILED").contains(p.reviewStatus()))return false;
        return "SYNC".equals(m.failureStage)||p!=null&&("SYNC".equals(p.stage())
                ||Set.of("FAILED","UNCERTAIN","WAITING_AUTH","PAUSED","REJECTED").contains(p.syncStatus()));
    }
    public static boolean due(CreationState.Material m,long now) {
        return !m.excludedFromDelivery&&(!"FAILED".equals(m.status)||"SYNC".equals(m.failureStage))&&m.syncRecoveryNextAt<=now;
    }
    public static void settle(CreationState.Material m,MaterialProcessing progress,boolean attemptsExhausted,long now) {
        m.processing=progress;
        if("READY".equals(m.status)){m.failureStage=null;m.failureCode=null;m.syncRecoveryStartedAt=0;m.syncRecoveryNextAt=0;m.syncRecoveryFailures=0;return;}
        // Content-policy failures retain their review workflow; technical sync recovery cannot override it.
        if(Set.of("REJECTED","FAILED").contains(progress.reviewStatus())){if("SYNC".equals(m.failureStage))m.failureStage="REVIEW";return;}
        if(!systemIssue(m))return;
        m.failureStage="SYNC";
        if(m.syncRecoveryStartedAt==0)m.syncRecoveryStartedAt=now;
        if(progress.components().stream().anyMatch(c->"CAROUSEL".equals(c.component())&&"FAILED".equals(c.syncStatus())))m.syncRecoveryFailures++;
        if(attemptsExhausted||Set.of("PAUSED","REJECTED").contains(progress.syncStatus())||m.syncRecoveryFailures>=3||now-m.syncRecoveryStartedAt>=WINDOW) {
            m.status="FAILED";m.excludedFromDelivery=true;m.syncRecoveryNextAt=0;
        }else{m.status="PROCESSING";m.syncRecoveryNextAt=now+30_000;}
    }
    public static MaterialProcessing customerProgress(CreationState.Material m) {
        var p=m.processing==null?MaterialProcessing.legacy(m):m.processing;
        if(!systemIssue(m))return p;
        return new MaterialProcessing(p.generationStatus(),p.storageStatus(),p.reviewStatus(),m.excludedFromDelivery?"FAILED":"RUNNING","SYNC",m.excludedFromDelivery?"本次素材暂未完成":"素材处理中",false,java.util.List.of());
    }
}
