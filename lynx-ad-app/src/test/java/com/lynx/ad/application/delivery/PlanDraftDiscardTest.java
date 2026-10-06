package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.agents.plan.PlanAgent;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.idempotency.IdempotencyService;
import com.lynx.ad.modules.agentops.domain.port.AgentActionRepository;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanDraftDiscardTest {
    final TenantId tenant = new TenantId("owner");
    final AdPlanRepository repository = mock(AdPlanRepository.class);
    final AdPlanCreativeBindingStore bindings = mock(AdPlanCreativeBindingStore.class);
    final PlanAgent agent = mock(PlanAgent.class);
    final PlanCommandService service = new PlanCommandService(repository, mock(SuggestBudgetPort.class),
            mock(SuggestRoiPort.class), mock(IdempotencyService.class), agent, mock(CreativeTaskRepository.class),
            mock(BillingTrialService.class), mock(AdStatusPort.class), mock(QianchuanCredentialFacade.class),
            mock(AgentActionRepository.class), new ObjectMapper(), mock(AuditRecorder.class), bindings);

    AdPlan draft() {
        return AdPlan.draft(tenant, new AccountId("account"), new AwemeId("aweme"), new PlanName("未投放草稿"),
                new DeliverySetting(SmartBidType.CONSERVATIVE, Money.ofYuan(200), Optional.empty(), Schedule.fromNow(), AudienceJson.empty()),
                List.of(new QcProductId("product")));
    }

    @Test void discardsOnlyAnOwnedLocalDraftAndClearsItsProductBindings() {
        var plan = draft();
        when(repository.findById(tenant, plan.id())).thenReturn(Optional.of(plan));
        when(repository.discardDraft(tenant, plan.id())).thenReturn(true);
        service.discardDraft(tenant, plan.id());
        verify(repository).discardDraft(tenant, plan.id());
        verify(bindings).upsert(plan.id(), List.of());
        verifyNoInteractions(agent);
    }

    @Test void refusesOtherTenantsAndDraftsThatAlreadyHaveAPlatformId() {
        var plan = draft();
        assertThrows(BizException.class, () -> service.discardDraft(new TenantId("other"), plan.id()));
        var pkg = com.lynx.ad.modules.creative.domain.model.CreativePackage.of(List.of(
                new com.lynx.ad.modules.creative.domain.model.CreativeArtifact(new QcProductId("product"),
                        List.of(new com.lynx.ad.modules.creative.domain.model.AdTitle("测试标题", com.lynx.ad.modules.creative.domain.model.TitleType.CUSTOM)), Optional.of(new VideoId("video")), Optional.empty())));
        pkg.confirm();
        plan.publish(pkg);
        plan.acceptExternalId(new QcPlanId("platform-plan"));
        when(repository.findById(tenant, plan.id())).thenReturn(Optional.of(plan));
        assertThrows(BizException.class, () -> service.discardDraft(tenant, plan.id()));
        verify(repository, never()).discardDraft(any(), any());
        verifyNoInteractions(bindings, agent);
    }

    @Test void concurrentStatusChangeDoesNotDeleteBindings() {
        var plan = draft();
        when(repository.findById(tenant, plan.id())).thenReturn(Optional.of(plan));
        assertThrows(BizException.class, () -> service.discardDraft(tenant, plan.id()));
        verifyNoInteractions(bindings);
    }
}
