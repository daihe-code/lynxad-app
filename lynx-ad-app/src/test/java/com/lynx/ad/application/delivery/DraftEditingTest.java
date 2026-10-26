package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.api.dto.PlanDraftRequest;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DraftEditingTest {
    @Test void legacyCreateAlsoChecksProductOccupancyBeforeAnyPlanIsSaved() {
        var guard=mock(com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore.class);service.draftProducts(guard);
        doThrow(new BizException(1404,"商品已在其他草稿中")).when(guard).assertAvailable("tenant-1","account-1",List.of("new-product"),null);
        assertEquals(1404,assertThrows(BizException.class,()->service.createDraft(tenant,request("account-1"))).code());
        verifyNoInteractions(plans,bindings);
    }
    @Test void legacyUpdateChecksProductsExcludingOnlyTheCurrentDraft() {
        var plan=plan();var guard=mock(com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore.class);service.draftProducts(guard);
        doThrow(new BizException(1404,"商品已在其他草稿中")).when(guard).assertAvailable("tenant-1","account-1",List.of("new-product"),plan.id().value());
        assertEquals(1404,assertThrows(BizException.class,()->service.updateDraft(tenant,plan.id(),request("account-1"))).code());
        verify(plans,never()).save(any());verifyNoInteractions(bindings);assertEquals("原计划",plan.name().text());
    }
    private final TenantId tenant = new TenantId("tenant-1");
    private final AdPlanRepository plans = mock(AdPlanRepository.class);
    private final AdPlanCreativeBindingStore bindings = mock(AdPlanCreativeBindingStore.class);
    private final PlanCommandService service = new PlanCommandService(plans, null, null, null, null, null,
            mock(BillingTrialService.class), null, null, null, new ObjectMapper(), mock(AuditRecorder.class), bindings);

    private AdPlan plan() {
        var plan = AdPlan.draft(tenant, new AccountId("account-1"), new AwemeId("aweme-1"), new PlanName("原计划"),
                new DeliverySetting(SmartBidType.CONSERVATIVE, Money.ofYuan(300), Optional.empty(),
                        Schedule.fromNow(), AudienceJson.empty()), List.of(new QcProductId("old-product")));
        when(plans.findById(tenant, plan.id())).thenReturn(Optional.of(plan));
        return plan;
    }

    private PlanDraftRequest request(String account) {
        return new PlanDraftRequest(account, "aweme-1", "新计划", "SMART_BID_CONSERVATIVE",
                new BigDecimal("600"), null, List.of("new-product"), null);
    }
    @Test void legacyDraftUpdateCannotBypassActiveMaterialGeneration() {
        var plan=plan();var creations=mock(com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore.class);service.creationStore(creations);
        var state=new com.lynx.ad.modules.delivery.domain.model.CreationState();state.generationStatus="RUNNING";
        when(creations.get(tenant.value(),plan.id().value())).thenReturn(state);
        when(creations.mutate(eq(tenant.value()),eq(plan.id().value()),isNull(),any())).thenAnswer(call->{
            java.util.function.Consumer<com.lynx.ad.modules.delivery.domain.model.CreationState> change=call.getArgument(3);change.accept(state);return state;
        });
        assertThrows(BizException.class,()->service.updateDraft(tenant,plan.id(),request("account-1")));
        assertEquals(List.of(new QcProductId("old-product")),plan.productIds());verify(plans,never()).save(any());verifyNoInteractions(bindings);
    }

    @Test
    void draftReselectionReplacesPersistedProductBindingsWithoutClaimingMedia() {
        var plan = plan();
        service.updateDraft(tenant, plan.id(), request("account-1"));
        assertEquals(List.of(new QcProductId("new-product")), plan.productIds());
        var captured = ArgumentCaptor.forClass(List.class);
        verify(bindings).upsert(eq(plan.id()), captured.capture());
        var binding = (PlanCreativeBinding) captured.getValue().getFirst();
        assertEquals("new-product", binding.productId().value());
        assertNull(binding.packageId());
        assertNull(binding.videoId());
        verify(plans).save(plan);
    }

    @Test
    void submittedPlanRejectsEditsBeforeChangingAnyLocalSettings() {
        var plan = plan();
        new com.lynx.ad.modules.delivery.domain.service.PlanStateMachine().transit(plan, PlanTransition.PUBLISH);
        plan.acceptExternalId(new QcPlanId("qc-1"));
        plan.markDelivering();
        assertThrows(BizException.class, () -> service.updateDraft(tenant, plan.id(), request("account-1")));
        assertEquals("原计划", plan.name().text());
        assertEquals(List.of(new QcProductId("old-product")), plan.productIds());
        verify(plans, never()).save(any());
        verifyNoInteractions(bindings);
    }

    @Test
    void changingAccountCannotMoveAnExistingDraftAcrossAccounts() {
        var plan = plan();
        assertThrows(BizException.class, () -> service.updateDraft(tenant, plan.id(), request("another-account")));
        assertEquals("原计划", plan.name().text());
        verify(plans, never()).save(any());
        verifyNoInteractions(bindings);
    }
}
