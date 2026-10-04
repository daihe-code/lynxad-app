package com.lynx.ad.modules.billing.domain.service;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.model.Trial;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import java.time.Instant;

public class TrialGuardPolicy {

    public void assertWritable(Trial trial, Instant now, WriteAction action) {
        assertWritable(trial, null, now, action);
    }

    public void assertWritable(Trial trial, Subscription subscription, Instant now, WriteAction action) {
        boolean trialOk = trial != null && !trial.isExpired(now);
        boolean subOk = subscription != null && subscription.isEffective(now);
        if (trialOk || subOk) {
            return;
        }
        throw new DomainException(1102, "试用已过期且无有效订阅，不能" + label(action));
    }

    private static String label(WriteAction action) {
        return switch (action) {
            case SELECT_PRODUCT -> "提交选品";
            case GENERATE_CREATIVE -> "生成素材";
            case SAVE_PLAN -> "保存计划";
            case PUBLISH -> "发布";
        };
    }
}
