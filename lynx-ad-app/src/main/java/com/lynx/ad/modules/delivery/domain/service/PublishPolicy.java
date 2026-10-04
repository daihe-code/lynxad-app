package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;

public class PublishPolicy {

    public void assertPublishable(AdPlan plan, boolean trialActive, boolean packageConfirmed, int productCount) {
        if (!trialActive) {
            throw new DomainException(1102, "试用已过期，不能发布");
        }
        if (plan.status() != PlanStatus.DRAFT && plan.status() != PlanStatus.REJECTED) {
            throw new DomainException(1403, "当前状态不可发布");
        }
        if (!packageConfirmed) {
            throw new DomainException(1403, "请先确认素材包");
        }
        if (productCount < 1 || productCount > 20) {
            throw new DomainException(1301, "发布商品数须为 1–20");
        }
        plan.setting().assertReadyToPublish();
    }

    public IdempotencyKey nextKey(AdPlan plan) {
        return IdempotencyKey.ofPublish(plan.id(), plan.publishVersion() + 1);
    }
}
