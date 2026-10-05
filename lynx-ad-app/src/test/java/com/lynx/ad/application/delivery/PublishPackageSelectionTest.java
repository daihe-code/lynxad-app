package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.agents.plan.PlanAgent;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.idempotency.IdempotencyService;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.creative.domain.model.*;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublishPackageSelectionTest {
    private final TenantId tenant = new TenantId("tenant-1");
    private final AdPlanRepository plans = mock(AdPlanRepository.class);
    private final CreativeTaskRepository tasks = mock(CreativeTaskRepository.class);
    private final AdPlanCreativeBindingStore bindings = mock(AdPlanCreativeBindingStore.class);
    private final PlanAgent agent = mock(PlanAgent.class);
    private final PlanCommandService service = new PlanCommandService(plans, null, null,
            mock(IdempotencyService.class), agent, tasks, mock(BillingTrialService.class), null, null, null,
            new ObjectMapper(), mock(AuditRecorder.class), bindings);
    private AdPlan plan() {
        var plan = AdPlan.draft(tenant, new AccountId("account-1"), new AwemeId("aweme-1"), new PlanName("烘焙计划"),
                new DeliverySetting(SmartBidType.CONSERVATIVE, Money.ofYuan(300), Optional.empty(),
                        Schedule.fromNow(), AudienceJson.empty()), List.of(new QcProductId("product-1")));
        when(plans.findById(tenant, plan.id())).thenReturn(Optional.of(plan));
        return plan;
    }
    private CreativeTask task(PlanId plan, String packageId, int day, boolean confirmed) {
        var artifact = new CreativeArtifact(new QcProductId("product-1"),
                List.of(new AdTitle(packageId, TitleType.CUSTOM)), Optional.empty(), Optional.empty());
        var pkg = new CreativePackage(new PackageId(packageId), 1,
                confirmed ? Instant.now() : null, List.of(artifact), Instant.parse("2026-10-0"+day+"T00:00:00Z"));
        return new CreativeTask(new TaskId("task-"+packageId), tenant, Optional.of(plan), List.of(),
                CreativeMode.STANDARD, TaskStatus.SUCCEEDED, List.of(), pkg, null, null);
    }
    @Test
    void regeneratedPlanPublishesItsLatestConfirmedPackage() {
        var plan = plan();
        when(tasks.listByTenant(tenant)).thenReturn(List.of(task(plan.id(), "old-package", 1, true),
                task(new PlanId("other-plan"), "unrelated-package", 5, true), task(plan.id(), "new-package", 3, true)));
        service.publish(tenant, plan.id(), new IdempotencyKey("publish-1"));
        var captured = ArgumentCaptor.forClass(List.class);
        verify(bindings).upsert(eq(plan.id()), captured.capture());
        assertEquals("new-package", ((PlanCreativeBinding)captured.getValue().getFirst()).packageId().value());
        verify(agent).publishNow(plan.id());
    }
    @Test
    void olderConfirmationCannotConfirmANewerUnconfirmedPackage() {
        var plan = plan();
        when(tasks.listByTenant(tenant)).thenReturn(List.of(task(plan.id(), "old-package", 1, true),
                task(plan.id(), "new-package", 3, false)));
        assertThrows(com.lynx.ad.common.exception.DomainException.class,
                () -> service.publish(tenant, plan.id(), new IdempotencyKey("publish-2")));
        assertEquals(PlanStatus.DRAFT, plan.status());
        verifyNoInteractions(agent, bindings);
    }
    @Test
    void anotherPlansConfirmedPackageCannotSupplyMissingMaterials() {
        var plan = plan();
        when(tasks.listByTenant(tenant)).thenReturn(List.of(task(new PlanId("other-plan"), "other-package", 3, true)));
        assertThrows(com.lynx.ad.common.exception.BizException.class,
                () -> service.publish(tenant, plan.id(), new IdempotencyKey("publish-3")));
        verifyNoInteractions(agent, bindings);
    }
}
