package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import com.lynx.ad.modules.delivery.domain.model.PlanTransition;
import java.util.List;

public class PlanStateMachine {

    public void transit(AdPlan plan, PlanTransition cmd) {
        PlanStatus current = plan.status();
        PlanStatus next = switch (cmd) {
            case PUBLISH -> require(current, PlanStatus.DRAFT, PlanStatus.REJECTED, PlanStatus.SUBMITTING);
            case ACCEPT_QC_ID -> require(current, PlanStatus.SUBMITTING, PlanStatus.PENDING_AUDIT);
            case AUDIT_PASS -> require(current, PlanStatus.PENDING_AUDIT, PlanStatus.DELIVERING);
            case AUDIT_REJECT -> require(current, PlanStatus.PENDING_AUDIT, PlanStatus.REJECTED);
            case DISABLE -> require(current, PlanStatus.DELIVERING, PlanStatus.PAUSED);
            case ENABLE -> require(current, PlanStatus.PAUSED, PlanStatus.DELIVERING);
            case FINISH -> require(current, List.of(PlanStatus.DELIVERING, PlanStatus.PAUSED), PlanStatus.FINISHED);
            case REVERT_TO_DRAFT -> require(current, PlanStatus.REJECTED, PlanStatus.DRAFT);
        };
        plan.apply(next);
    }

    private static PlanStatus require(PlanStatus current, PlanStatus expected, PlanStatus next) {
        if (current != expected) {
            throw new DomainException(1404, "非法状态迁移: " + current + " → " + next);
        }
        return next;
    }

    private static PlanStatus require(PlanStatus current, PlanStatus a, PlanStatus b, PlanStatus next) {
        if (current != a && current != b) {
            throw new DomainException(1404, "非法状态迁移: " + current + " → " + next);
        }
        return next;
    }

    private static PlanStatus require(PlanStatus current, java.util.List<PlanStatus> allowed, PlanStatus next) {
        if (!allowed.contains(current)) {
            throw new DomainException(1404, "非法状态迁移: " + current + " → " + next);
        }
        return next;
    }
}
