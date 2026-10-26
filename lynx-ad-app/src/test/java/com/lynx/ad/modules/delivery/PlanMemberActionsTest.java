package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.domain.port.AgentActionRepository;
import com.lynx.ad.modules.agentops.domain.model.AgentAction;
import com.lynx.ad.modules.agentops.domain.model.ActionType;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.application.PlanTransferService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlanMemberActionsTest {
    final TenantId tenant=new TenantId("tenant");
    final AccountId account=new AccountId("account");
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    final AdStatusPort status=mock(AdStatusPort.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    final AgentActionRepository actions=mock(AgentActionRepository.class);
    final AuditRecorder audit=mock(AuditRecorder.class);
    final PlanTransferStore transfers=mock(PlanTransferStore.class);
    final Credential credential=new Credential("fixture-token",new AdvertiserId("999"));
    final PlanCommandService service=new PlanCommandService(plans,null,null,null,null,null,mock(BillingTrialService.class),
            status,credentials,actions,new ObjectMapper(),audit,null);
    AdPlan volume,cost;
    @BeforeEach void setup() {
        volume=plan("volume","111",SmartBidType.CONSERVATIVE);
        cost=plan("cost","222",SmartBidType.CUSTOM);
        when(plans.findById(tenant,volume.id())).thenReturn(Optional.of(volume));
        when(plans.findById(tenant,cost.id())).thenReturn(Optional.of(cost));
        when(plans.findByTenant(tenant)).thenReturn(List.of(volume,cost));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);
        when(transfers.activeId(eq(tenant),any())).thenReturn(volume.id());
        service.transferStore(transfers);
    }
    private AdPlan plan(String id,String qc,SmartBidType mode) {
        return new AdPlan(new PlanId(id),tenant,account,new AwemeId("aweme"),new PlanName(id),PlanStatus.PAUSED,PlanSource.LINGTOU,
                new DeliverySetting(mode,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),
                List.of(),new QcPlanId(qc),"DISABLE",null,0,null,List.of());
    }
    @Test void enablingPausedCounterpartTargetsItsOwnPlatformId() {
        var result=service.act(tenant,cost.id(),PlanAction.ENABLE);
        verify(status).updateStatus(credential,new QcPlanId("222"),PlanAction.ENABLE);
        assertEquals(cost.id().value(),result.planId());assertEquals(PlanStatus.DELIVERING,cost.status());assertEquals(PlanStatus.PAUSED,volume.status());
        verify(plans,never()).save(volume);verify(transfers,never()).activeId(any(),any());verify(transfers).reconcileActive(tenant,List.of(volume,cost));
    }
    @Test void pausingSelectedMemberLeavesTheOtherModeUnchanged() {
        cost.apply(PlanStatus.DELIVERING);
        service.act(tenant,cost.id(),PlanAction.DISABLE,"预算消耗过快");
        verify(status).updateStatus(credential,new QcPlanId("222"),PlanAction.DISABLE);
        assertEquals(PlanStatus.PAUSED,cost.status());assertEquals(PlanStatus.PAUSED,volume.status());verify(plans,never()).save(volume);
    }
    static java.util.stream.Stream<String> pauseReasons() { return PlanPauseReason.OPTIONS.stream(); }
    @ParameterizedTest @MethodSource("pauseReasons")
    void selectedPauseReasonIsRecordedOnlyAfterSuccessfulPlatformPause(String reason) {
        cost.apply(PlanStatus.DELIVERING);
        service.act(tenant,cost.id(),PlanAction.DISABLE,reason);
        var captured=org.mockito.ArgumentCaptor.forClass(AgentAction.class);
        verify(actions).saveIgnoreDup(captured.capture());
        assertEquals(ActionType.MANUAL_DISABLE,captured.getValue().type());
        assertEquals(cost.id(),captured.getValue().planId());
        assertEquals(tenant,captured.getValue().tenantId());
        assertEquals("暂停投放："+reason,captured.getValue().summary());
        assertEquals("DELIVERING",captured.getValue().beforeJson());
        assertEquals("PAUSED",captured.getValue().afterJson());
        verify(audit).record(argThat(command -> command.objectLabel().contains(reason)));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","随便写一个原因","ROI过低"})
    void missingOrUnsupportedPauseReasonDoesNotChangeThePlanOrCallPlatform(String reason) {
        cost.apply(PlanStatus.DELIVERING);
        assertEquals(1402,assertThrows(BizException.class,()->service.act(tenant,cost.id(),PlanAction.DISABLE,reason)).code());
        assertEquals(PlanStatus.DELIVERING,cost.status());
        verifyNoInteractions(status,credentials,actions,audit);verify(plans,never()).save(any());
    }
    @Test void platformPauseFailureDoesNotSaveAReasonOrClaimThePlanIsPaused() {
        cost.apply(PlanStatus.DELIVERING);
        doThrow(new BizException(1406,"暂停失败")).when(status).updateStatus(credential,new QcPlanId("222"),PlanAction.DISABLE);
        assertThrows(BizException.class,()->service.act(tenant,cost.id(),PlanAction.DISABLE,"其他原因"));
        assertEquals(PlanStatus.DELIVERING,cost.status());
        verifyNoInteractions(actions,audit);verify(plans,never()).save(any());
    }
    @Test void staleStateOrAnotherTenantCannotRecordAPauseReason() {
        assertEquals(1404,assertThrows(BizException.class,()->service.act(tenant,cost.id(),PlanAction.DISABLE,"其他原因")).code());
        assertThrows(BizException.class,()->service.act(new TenantId("other"),cost.id(),PlanAction.DISABLE,"其他原因"));
        verifyNoInteractions(status,credentials,actions,audit);verify(plans,never()).save(any());
    }
    @Test void pairedEnableUsesExclusiveTransferWorkflowInsteadOfSinglePlanStatusPort() {
        var transfer=mock(PlanTransferService.class);service.transferService(transfer);volume.apply(PlanStatus.DELIVERING);
        when(transfers.group(tenant,cost.id())).thenReturn(new PlanTransferStore.Group("volume","volume","volume","cost",null));
        when(transfer.enableExisting(tenant,cost.id())).thenAnswer(i->{volume.apply(PlanStatus.PAUSED);cost.apply(PlanStatus.DELIVERING);return new PlanTransferService.Result("cost","222","SUCCEEDED","启用成功");});
        assertEquals("cost",service.act(tenant,cost.id(),PlanAction.ENABLE).planId());
        verify(transfer).enableExisting(tenant,cost.id());verifyNoInteractions(status,credentials);
        assertEquals(PlanStatus.PAUSED,volume.status());assertEquals(PlanStatus.DELIVERING,cost.status());
    }
    @Test void uncertainPairedEnableDoesNotReportSuccessOrFallbackToSingleEnable() {
        var transfer=mock(PlanTransferService.class);service.transferService(transfer);
        when(transfers.group(tenant,cost.id())).thenReturn(new PlanTransferStore.Group("volume","volume","volume","cost",null));
        when(transfer.enableExisting(tenant,cost.id())).thenReturn(new PlanTransferService.Result("volume","222","UNCERTAIN","暂停结果待核查"));
        assertEquals(1523,assertThrows(BizException.class,()->service.act(tenant,cost.id(),PlanAction.ENABLE)).code());
        verifyNoInteractions(status,credentials);verify(plans,never()).save(any());
    }
    @Test void pendingTransferPreventsIndependentStatusWrites() {
        doThrow(new BizException(1523,"转投待核查")).when(transfers).assertIdle(tenant,cost.id());
        assertThrows(BizException.class,()->service.act(tenant,cost.id(),PlanAction.ENABLE));
        verifyNoInteractions(status,credentials);verify(plans,never()).save(any());
    }
    @Test void crossTenantCannotOperateAVisibleMemberFromAnotherAccount() {
        assertThrows(BizException.class,()->service.act(new TenantId("other"),cost.id(),PlanAction.ENABLE));
        verifyNoInteractions(status,credentials);assertEquals(PlanStatus.PAUSED,cost.status());
    }
}
