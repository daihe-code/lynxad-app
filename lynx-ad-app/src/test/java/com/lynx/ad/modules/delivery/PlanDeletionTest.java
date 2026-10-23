package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.domain.port.AgentActionRepository;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PlanDeletionTest {
    final TenantId tenant=new TenantId("tenant");
    final AccountId account=new AccountId("account");
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    final AdStatusPort status=mock(AdStatusPort.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    final PlanTransferStore transfers=mock(PlanTransferStore.class);
    final AdPlanCreativeBindingStore bindings=mock(AdPlanCreativeBindingStore.class);
    final AuditRecorder audit=mock(AuditRecorder.class);
    final Credential credential=new Credential("fixture-token",new AdvertiserId("999"));
    final PlanCommandService service=new PlanCommandService(plans,null,null,null,null,null,mock(BillingTrialService.class),
            status,credentials,mock(AgentActionRepository.class),new ObjectMapper(),audit,bindings);
    AdPlan volume,cost;
    @BeforeEach void setup() {
        volume=plan("volume","111",SmartBidType.CONSERVATIVE,PlanStatus.DELIVERING);
        cost=plan("cost","222",SmartBidType.CUSTOM,PlanStatus.PAUSED);
        when(plans.findById(tenant,volume.id())).thenReturn(Optional.of(volume));
        when(plans.findById(tenant,cost.id())).thenReturn(Optional.of(cost));
        when(plans.findByIdForUpdate(tenant,volume.id())).thenReturn(Optional.of(volume));
        when(plans.findByIdForUpdate(tenant,cost.id())).thenReturn(Optional.of(cost));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);
        service.transferStore(transfers);
    }
    private AdPlan plan(String id,String qc,SmartBidType mode,PlanStatus state) {
        return new AdPlan(new PlanId(id),tenant,account,new AwemeId("aweme"),new PlanName(id),state,PlanSource.LINGTOU,
                new DeliverySetting(mode,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),
                List.of(new QcProductId("product")),qc==null?null:new QcPlanId(qc),"DISABLE",null,0,null,List.of());
    }
    @ParameterizedTest @EnumSource(SmartBidType.class)
    void deletingEitherModeTargetsOnlyThatPhysicalPlanAndRetainsSharedBindings(SmartBidType mode) {
        var selected=mode==SmartBidType.CUSTOM?cost:volume;var other=selected==cost?volume:cost;
        var before=other.status();
        service.deletePlan(tenant,selected.id());
        verify(transfers).lockForDeletion(selected);
        verify(status).updateStatus(credential,selected.qcPlanId().orElseThrow(),PlanAction.DELETE);
        verifyNoMoreInteractions(status);
        assertEquals(PlanStatus.DELETED,selected.status());assertEquals("DELETE",selected.qcStatus());
        assertEquals(before,other.status());assertEquals(List.of(new QcProductId("product")),selected.productIds());
        verify(plans).save(selected);verify(plans,never()).save(other);verifyNoInteractions(bindings);
        verify(transfers).detachDeleted(tenant,selected.id());verify(audit).record(any());
    }
    @Test void platformRejectionDoesNotHidePlanOrDetachItsSharedRoot() {
        doThrow(new BizException(1406,"平台拒绝删除")).when(status).updateStatus(credential,new QcPlanId("222"),PlanAction.DELETE);
        assertEquals("平台拒绝删除",assertThrows(BizException.class,()->service.deletePlan(tenant,cost.id())).getMessage());
        assertEquals(PlanStatus.PAUSED,cost.status());verify(plans,never()).save(any());
        verify(transfers,never()).detachDeleted(any(),any());verifyNoInteractions(audit,bindings);
    }
    @Test void pendingTransferBlocksDeleteBeforeAnyRemoteRequest() {
        doThrow(new BizException(1523,"转投待核查")).when(transfers).lockForDeletion(cost);
        assertThrows(BizException.class,()->service.deletePlan(tenant,cost.id()));
        verifyNoInteractions(status,credentials,audit);verify(plans,never()).save(any());
    }
    @Test void repeatedDeletionDoesNotPostAgain() {
        service.deletePlan(tenant,cost.id());service.deletePlan(tenant,cost.id());
        verify(status,times(1)).updateStatus(any(),any(),eq(PlanAction.DELETE));verify(plans,times(1)).save(cost);
    }
    @Test void concurrentDeletionUsesLockedCurrentStateInsteadOfStaleInitialRead() {
        var latest=plan("cost","222",SmartBidType.CUSTOM,PlanStatus.DELETED);
        when(plans.findByIdForUpdate(tenant,cost.id())).thenReturn(Optional.of(latest));
        service.deletePlan(tenant,cost.id());
        verifyNoInteractions(status,credentials,audit);verify(plans,never()).save(any());
    }
    @Test void crossTenantCannotDeleteAnotherTenantsPlan() {
        assertThrows(BizException.class,()->service.deletePlan(new TenantId("other"),cost.id()));
        verifyNoInteractions(status,credentials,transfers,audit);
    }
    @Test void unsubmittedDraftUsesGuardedLocalDiscardWithoutPlatformCall() {
        var draft=plan("draft",null,SmartBidType.CONSERVATIVE,PlanStatus.DRAFT);
        when(plans.findById(tenant,draft.id())).thenReturn(Optional.of(draft));
        when(plans.discardDraft(tenant,draft.id())).thenReturn(true);
        service.deletePlan(tenant,draft.id());
        verify(plans).discardDraft(tenant,draft.id());verify(bindings).upsert(draft.id(),List.of());
        verifyNoInteractions(status,credentials,transfers);
    }
    @Test void submittingPlanWithoutPlatformIdCannotBeDiscarded() {
        var submitting=plan("submitting",null,SmartBidType.CONSERVATIVE,PlanStatus.SUBMITTING);
        when(plans.findById(tenant,submitting.id())).thenReturn(Optional.of(submitting));
        assertThrows(BizException.class,()->service.deletePlan(tenant,submitting.id()));
        verify(plans,never()).discardDraft(any(),any());verifyNoInteractions(status,credentials,bindings);
    }
    @Test void staleStatusResponsesCannotReviveDeletedPlan() {
        cost.markDeleted();cost.apply(PlanStatus.DELIVERING);assertEquals(PlanStatus.DELETED,cost.status());
        cost.apply(PlanStatus.PAUSED);assertEquals(PlanStatus.DELETED,cost.status());
    }
    @Test void localFailureAfterAcknowledgementReportsUncertainty() {
        doThrow(new RuntimeException("db unavailable")).when(plans).save(cost);
        var error=assertThrows(BizException.class,()->service.deletePlan(tenant,cost.id()));
        assertTrue(error.getMessage().contains("千川已接受删除"));assertTrue(error.getMessage().contains("勿重复提交"));
        verify(transfers,never()).detachDeleted(any(),any());
    }
    @Test void staleRemoteListCannotReimportDeletedPhysicalPlan() {
        cost.markDeleted();when(plans.findByTenant(tenant)).thenReturn(List.of(cost));
        var accounts=mock(com.lynx.ad.modules.identity.domain.port.OauthAccountRepository.class);
        when(accounts.findByTenant(tenant)).thenReturn(List.of(new com.lynx.ad.modules.identity.domain.model.OauthAccount(account,tenant,
                com.lynx.ad.modules.identity.domain.model.Platform.QIANCHUAN,new AdvertiserId("999"),"账户",null,null,
                java.time.Instant.now().plusSeconds(3600),null,"ACTIVE",List.of())));
        var list=mock(AdListPort.class);var remote=mock(PlanRemoteStore.class);var management=mock(AdManagementPort.class);
        when(list.listIncludingDeleted(credential,null)).thenReturn(List.of(new OceanPlanRow("222","cost","DELIVERY_OK",SmartBidType.CUSTOM,
                Money.ofYuan(300),null,null,new ObjectMapper().createObjectNode())));
        var sync=new com.lynx.ad.modules.delivery.application.PlanSyncService(plans,accounts,credentials,list,management,remote,
                mock(org.springframework.transaction.PlatformTransactionManager.class));sync.transferStore(transfers);
        assertEquals(0,sync.sync(tenant,null).accounts().getFirst().plans());
        assertEquals(PlanStatus.DELETED,cost.status());verify(plans,never()).save(any());verifyNoInteractions(management,remote);
    }
}
