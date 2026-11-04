package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.application.PlanSyncService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanRemoteStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.*;

class PlanAwemeSyncTest {
    final TenantId tenant=new TenantId("tenant-a");
    final AccountId account=new AccountId("account-a");
    final ObjectMapper json=new ObjectMapper();
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    final OauthAccountRepository accounts=mock(OauthAccountRepository.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    final AdListPort list=mock(AdListPort.class);
    final AdManagementPort management=mock(AdManagementPort.class);
    final PlanRemoteStore store=mock(PlanRemoteStore.class);
    final PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    final Credential credential=new Credential("test-only",new AdvertiserId("999"));
    final PlanSyncService sync=new PlanSyncService(plans,accounts,credentials,list,management,store,tx);
    AdPlan plan;
    @BeforeEach void setup() throws Exception {
        var oauth=new OauthAccount(account,tenant,Platform.QIANCHUAN,new AdvertiserId("999"),"授权账户",null,null,
                Instant.now().plusSeconds(3600),null,"ACTIVE",List.of()).withAuthorizationBinding("grant-a","app-a",null);
        when(accounts.findByTenant(tenant)).thenReturn(List.of(oauth));when(accounts.findById(tenant,account)).thenReturn(Optional.of(oauth));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var payload=json.readTree("{\"ad_info\":{\"id\":123,\"budget\":300},\"room_info\":[{\"anchor_id\":62134167826,\"anchor_name\":\"SherrySherry\"}]}");
        when(list.listIncludingDeleted(credential,null)).thenReturn(List.of(new OceanPlanRow("123","历史计划","DISABLE",SmartBidType.CUSTOM,Money.ofYuan(300),null,null,payload)));
        plan=new AdPlan(new PlanId("plan-a"),tenant,account,new AwemeId("unknown"),new PlanName("历史计划"),PlanStatus.PAUSED,PlanSource.EXTERNAL,
                new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),
                List.of(),new QcPlanId("123"),"DISABLE",null,0,null,List.of());
        when(plans.findByTenant(tenant)).thenReturn(List.of(plan));
    }
    @Test void repairsExistingHistoryUsingSameAccountNativeDetailOnce() throws Exception {
        var detail=json.readTree("{\"ad_id\":123,\"aweme_id\":4034544334551504}");
        when(management.detail(credential,"123")).thenReturn(detail);
        assertEquals(1,sync.sync(tenant,account.value()).accounts().getFirst().plans());
        assertEquals("4034544334551504",plan.awemeId().value());verify(store).detail(tenant,"plan-a",detail);
        sync.sync(tenant,account.value());verify(management,times(1)).detail(credential,"123");
        verify(credentials,times(2)).getUsableCredential(tenant,account);
    }
    @Test void newImportsAlsoReadDetailInsteadOfUsingAnchorIdOrSoleAuthorizedAweme() throws Exception {
        when(plans.findByTenant(tenant)).thenReturn(List.of());when(store.findId(tenant,account,"123")).thenReturn(Optional.empty());
        when(management.detail(credential,"123")).thenReturn(json.readTree("{\"ad_id\":123,\"aweme_id\":4034544334551504}"));
        sync.sync(tenant,account.value());var capture=ArgumentCaptor.forClass(AdPlan.class);verify(plans).save(capture.capture());
        assertEquals("4034544334551504",capture.getValue().awemeId().value());assertEquals(account,capture.getValue().accountId());
    }
    @Test void wrongPlanDetailCannotRepairBindingAndFailureDoesNotDiscardListData() throws Exception {
        when(management.detail(credential,"123")).thenReturn(json.readTree("{\"ad_id\":456,\"aweme_id\":4034544334551504}"));
        var result=sync.sync(tenant,account.value());assertEquals(1,result.accounts().getFirst().plans());
        assertFalse(result.accounts().getFirst().warnings().isEmpty());assertEquals("unknown",plan.awemeId().value());
        verify(store,never()).detail(any(),any(),any());
        when(management.detail(credential,"123")).thenThrow(new IllegalStateException("unavailable"));
        assertEquals(1,sync.sync(tenant,account.value()).accounts().getFirst().plans());assertEquals("unknown",plan.awemeId().value());
    }
    @Test void knownBindingsAreNotOverwrittenAndOtherAccountsCannotBeSelected() {
        plan.restorePlatformAweme("4034544334551504");sync.sync(tenant,account.value());verify(management,never()).detail(any(),any());
        assertThrows(com.lynx.ad.common.exception.BizException.class,()->sync.sync(tenant,"account-other"));
        verify(credentials,never()).getUsableCredential(tenant,new AccountId("account-other"));
    }
    @Test void mappingRejectsPlaceholderAndAnchorIdAndAmbiguousRoomBindings() throws Exception {
        assertNull(QianchuanPlanMapping.awemeId(json.readTree("{\"aweme_id\":\"unknown\",\"room_info\":[{\"anchor_id\":62134167826}]}")));
        assertNull(QianchuanPlanMapping.awemeId(json.readTree("{\"room_info\":[{\"aweme_id\":123},{\"aweme_id\":456}]}")));
        assertEquals("123",QianchuanPlanMapping.awemeId(json.readTree("{\"room_info\":[{\"aweme_id\":123}]}")));
    }
    @Test void statisticsFailurePreservesPlansAndSnapshotsWithoutExposingPlatformDiagnostics() {
        plan.restorePlatformAweme("4034544334551504");
        when(management.report(eq(credential),any(java.time.LocalDate.class)))
                .thenThrow(new com.lynx.ad.common.exception.BizException(1406,"请求参数设置错误，请先参考help_message自查"));
        var result=sync.sync(tenant,account.value()).accounts().getFirst();
        assertEquals(1,result.plans());assertEquals(List.of("统计数据暂未更新，请稍后刷新"),result.warnings());
        verify(plans).save(plan);verify(store,never()).report(any(),any(),any(),any(),any(),any());
        when(management.report(eq(credential),any(java.time.LocalDate.class))).thenReturn(List.of());
        assertTrue(sync.sync(tenant,account.value()).accounts().getFirst().warnings().isEmpty());
        when(management.report(eq(credential),any(java.time.LocalDate.class)))
                .thenThrow(new com.lynx.ad.common.exception.BizException(1211,"授权已失效"));
        assertEquals(List.of("广告账户授权已失效，请在账户管理中重新授权"),sync.sync(tenant,account.value()).accounts().getFirst().warnings());
    }
}
