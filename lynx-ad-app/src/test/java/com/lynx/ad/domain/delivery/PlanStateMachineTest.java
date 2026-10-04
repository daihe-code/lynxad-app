package com.lynx.ad.domain.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.AudienceJson;
import com.lynx.ad.modules.delivery.domain.model.DeliverySetting;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import com.lynx.ad.modules.delivery.domain.model.PlanTransition;
import com.lynx.ad.modules.delivery.domain.model.Schedule;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.domain.service.PlanStateMachine;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanStateMachineTest {

    private final PlanStateMachine machine = new PlanStateMachine();

    private AdPlan draft() {
        return AdPlan.draft(
                new TenantId("t1"),
                new AccountId("acc1"),
                new AwemeId("aw1"),
                new PlanName("烘焙计划"),
                new DeliverySetting(SmartBidType.CONSERVATIVE, Money.ofYuan(300), Optional.empty(), Schedule.fromNow(), AudienceJson.empty()),
                List.of(new QcProductId("p1")));
    }

    @Test
    void legalHappyPath() {
        AdPlan plan = draft();
        machine.transit(plan, PlanTransition.PUBLISH);
        assertEquals(PlanStatus.SUBMITTING, plan.status());
        plan.acceptExternalId(new QcPlanId("qc1"));
        assertEquals(PlanStatus.PENDING_AUDIT, plan.status());
        plan.markDelivering();
        assertEquals(PlanStatus.DELIVERING, plan.status());
    }

    @Test
    void illegalDraftToDelivering() {
        AdPlan plan = draft();
        assertThrows(DomainException.class, () -> machine.transit(plan, PlanTransition.AUDIT_PASS));
    }

    @Test
    void pauseResumeFinish() {
        AdPlan plan = draft();
        machine.transit(plan, PlanTransition.PUBLISH);
        plan.acceptExternalId(new QcPlanId("qc1"));
        plan.markDelivering();
        plan.pause();
        assertEquals(PlanStatus.PAUSED, plan.status());
        plan.resume();
        assertEquals(PlanStatus.DELIVERING, plan.status());
        plan.finish();
        assertEquals(PlanStatus.FINISHED, plan.status());
    }

    @Test
    void rejectThenRevert() {
        AdPlan plan = draft();
        machine.transit(plan, PlanTransition.PUBLISH);
        plan.acceptExternalId(new QcPlanId("qc1"));
        plan.markRejected("不合规");
        assertEquals(PlanStatus.REJECTED, plan.status());
        plan.revertToDraft();
        assertEquals(PlanStatus.DRAFT, plan.status());
    }
}
