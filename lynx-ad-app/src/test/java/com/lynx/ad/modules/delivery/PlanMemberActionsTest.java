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
import com.lynx.ad.modules.delivery.application.PlanTransferService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.*;
import org.junit.jupiter.api.*;

class PlanMemberActionsTest {
    final TenantId tenant=new TenantId("tenant");
    final AccountId account=new AccountId("account");
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    final AdStatusPort status=mock(AdStatusPort.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    final PlanTransferStore transfers=mock(PlanTransferStore.class);
    final Credential credential=new Credential("fixture-token",new AdvertiserId("999"));
    final PlanCommandService service=new PlanCommandService(plans,null,null,null,null,null,mock(BillingTrialService.class),
            status,credentials,mock(AgentActionRepository.class),new ObjectMapper(),mock(AuditRecorder.class),null);
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
        service.act(tenant,cost.id(),PlanAction.DISABLE);
        verify(status).updateStatus(credential,new QcPlanId("222"),PlanAction.DISABLE);
        assertEquals(PlanStatus.PAUSED,cost.status());assertEquals(PlanStatus.PAUSED,volume.status());verify(plans,never()).save(volume);
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
