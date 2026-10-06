package com.lynx.ad.application.delivery;

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
import com.lynx.ad.modules.delivery.domain.model.Schedule;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanQueryActionsTest {

    @Test
    void pauseResumeOnDelivering() {
        AdPlan plan = delivering();
        plan.pause();
        assertEquals(PlanStatus.PAUSED, plan.status());
        plan.resume();
        assertEquals(PlanStatus.DELIVERING, plan.status());
    }

    @Test
    void cannotPauseDraft() {
        AdPlan plan = AdPlan.draft(
                new TenantId("t1"),
                new AccountId("a1"),
                new AwemeId("aw1"),
                new PlanName("草稿"),
                new DeliverySetting(SmartBidType.CONSERVATIVE, Money.ofYuan(100), Optional.empty(), Schedule.fromNow(), AudienceJson.empty()),
                List.of(new QcProductId("p1")));
        assertThrows(DomainException.class, plan::pause);
    }

    private static AdPlan delivering() {
        AdPlan plan = AdPlan.draft(
                new TenantId("t1"),
                new AccountId("a1"),
                new AwemeId("aw1"),
                new PlanName("投放中"),
                new DeliverySetting(SmartBidType.CONSERVATIVE, Money.ofYuan(100), Optional.empty(), Schedule.fromNow(), AudienceJson.empty()),
                List.of(new QcProductId("p1")));
        var pkg = com.lynx.ad.modules.creative.domain.model.CreativePackage.of(List.of(
                new com.lynx.ad.modules.creative.domain.model.CreativeArtifact(
                        new QcProductId("p1"),
                        List.of(new com.lynx.ad.modules.creative.domain.model.AdTitle(
                                "t", com.lynx.ad.modules.creative.domain.model.TitleType.CUSTOM)),
                        Optional.of(new com.lynx.ad.common.domain.Ids.VideoId("fixture-video")),
                        Optional.empty())));
        pkg.confirm();
        plan.publish(pkg);
        plan.acceptExternalId(new QcPlanId("qc1"));
        plan.markDelivering();
        return plan;
    }
}
