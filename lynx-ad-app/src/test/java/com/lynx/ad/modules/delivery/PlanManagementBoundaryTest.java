package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.api.dto.*;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanRemoteStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;

class PlanManagementBoundaryTest {
    final TenantId tenant=new TenantId("tenant-a"); final AccountId account=new AccountId("account-a"); final PlanId id=new PlanId("plan-a");
    final ObjectMapper json=new ObjectMapper(); final AdPlanRepository plans=mock(AdPlanRepository.class);
    final AdManagementPort port=mock(AdManagementPort.class);final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    final PlanRemoteStore store=mock(PlanRemoteStore.class);final PlanQueryService query=mock(PlanQueryService.class);
    final BillingTrialService billing=mock(BillingTrialService.class);final AuditRecorder audit=mock(AuditRecorder.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final Credential credential=new Credential("token",new AdvertiserId("123"));
    final PlanManagementService service=new PlanManagementService(plans,port,credentials,store,query,billing,audit,jdbc,json);
    AdPlan plan;
    @BeforeEach void setup() {
        plan=new AdPlan(id,tenant,account,new AwemeId("aweme"),new PlanName("原计划"),PlanStatus.DELIVERING,PlanSource.LINGTOU,
                new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(new BigDecimal("300")),Optional.of(new RoiGoal(new BigDecimal("2"))),Schedule.fromNow(),AudienceJson.empty()),List.of(),new QcPlanId("456"),"DELIVERY_OK",null,0,null,List.of());
        when(plans.findById(tenant,id)).thenReturn(Optional.of(plan));when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);
        when(port.detail(credential,"456")).thenReturn(json.createObjectNode());
        TenantContext.set(new TenantPrincipal(new UserId("user-a"),Optional.of(tenant),Optional.empty(),SysRole.USER));
    }
    @AfterEach void clear() { TenantContext.clear(); }
    @Test void platformRejectionDoesNotMutateLocalPlanOrWriteSuccessLog() {
        doThrow(new BizException(1406,"平台拒绝")).when(port).settings(credential,"456",null,new BigDecimal("350"),null);
        assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,new BigDecimal("350"),null)));
        assertEquals(0,plan.setting().dailyBudget().amount().compareTo(new BigDecimal("300")));
        verify(plans,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void nameOnlyChangeUsesFreshPlatformBudgetLocallyWithoutResendingIt() throws Exception {
        when(port.detail(credential,"456")).thenReturn(json.readTree("{\"status\":\"DELIVERY_OK\",\"delivery_setting\":{\"budget\":500,\"roi2_goal\":3}}"));
        service.settings(tenant,id,new PlanSettingsRequest("新计划",null,null));
        verify(port).settings(credential,"456","新计划",null,null);
        assertEquals(0,plan.setting().dailyBudget().amount().compareTo(new BigDecimal("500")));
        assertEquals(new BigDecimal("3"),plan.setting().roi2Goal().orElseThrow().value());verify(plans).save(plan);verify(audit).record(any());
    }
    @Test void conservativeModeCannotReceiveCostControlRoi() {
        plan.updateSetting(new DeliverySetting(SmartBidType.CONSERVATIVE,plan.setting().dailyBudget(),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()));
        assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,null,new BigDecimal("2"))));
        verifyNoInteractions(port,credentials);
    }
    @Test void searchScreenNeverUsesAnotherToolApiAsSubstitute() {
        assertThrows(BizException.class,()->service.control(tenant,id,new ControlTaskRequest("搜索抢首屏","搜索",new BigDecimal("300"),null,new BigDecimal("2"),List.of(),"request-123")));
        verifyNoInteractions(port,credentials,jdbc);
    }
    @Test void materialEligibilityRequiresEveryPlatformCondition() throws Exception {
        var valid=json.readTree("{\"audit_status\":\"PASS\",\"material_status\":\"DELIVERY_OK\",\"is_delete\":false,\"material_info\":{\"video_material\":{\"material_id\":123,\"source\":\"AD\",\"title\":\"视频\"}}}");
        var invalid=valid.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid).put("audit_status","REJECT");
        var unknown=valid.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)unknown).remove("material_status");
        var result=PlanManagementService.materialRows(List.of(valid,invalid,unknown));
        assertTrue(result.get(0).eligible());assertFalse(result.get(1).eligible());assertFalse(result.get(2).eligible());assertNull(result.get(0).impressions());
    }
    @Test void uncertainSubmissionBlocksDuplicateTaskCreation() {
        var hash=new java.util.concurrent.atomic.AtomicReference<String>();
        doAnswer(inv->{hash.set(inv.getArgument(4));return 1;}).when(jdbc).update(startsWith("INSERT"),any(),any(),any(),any(),any(),any());
        when(port.control(eq(credential),eq("456"),any(),any(),any(),any(),any())).thenThrow(new BizException(500,"提交超时"));
        var request=new ControlTaskRequest("一键起量","起量任务",new BigDecimal("150"),new BigDecimal("0.5"),null,List.of(),"request-123");
        assertThrows(BizException.class,()->service.control(tenant,id,request));
        doThrow(new org.springframework.dao.DuplicateKeyException("重复")).when(jdbc).update(startsWith("INSERT"),any(),any(),any(),any(),any(),any());
        when(jdbc.queryForList(startsWith("SELECT"),anyString(),anyString())).thenReturn(List.of(Map.of("request_hash",hash.get(),"status","UNKNOWN")));
        var error=assertThrows(BizException.class,()->service.control(tenant,id,request));assertTrue(error.getMessage().contains("核查任务列表"));
        verify(port,times(1)).control(eq(credential),eq("456"),any(),any(),any(),any(),any());
    }
    @Test void samePlatformIdInTwoAuthorizedAccountsRemainsTwoPlansAndFailuresRetainData() throws Exception {
        var accounts=mock(OauthAccountRepository.class);var list=mock(AdListPort.class);var tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var second=new AccountId("account-b");
        when(accounts.findByTenant(tenant)).thenReturn(List.of(oauth(account),oauth(second)));
        var secondCredential=new Credential("token-b",new AdvertiserId("124"));when(credentials.getUsableCredential(tenant,second)).thenReturn(secondCredential);
        JsonNode payload=json.readTree("{\"ad_info\":{\"id\":456,\"name\":\"平台计划\",\"status\":\"DISABLE\",\"budget\":300,\"roi2_goal\":2,\"create_time\":\"2026-10-06 10:00:00\"}}");
        var row=new OceanPlanRow("456","平台计划","DISABLE",SmartBidType.CUSTOM,Money.ofYuan(new BigDecimal("300")),null,null,payload);
        when(list.list(credential,null)).thenReturn(List.of(row));when(list.list(secondCredential,null)).thenReturn(List.of(row));
        when(plans.findByTenant(tenant)).thenReturn(List.of());when(store.findId(eq(tenant),any(),eq("456"))).thenReturn(Optional.empty());
        when(port.report(any(),any())).thenThrow(new BizException(1406,"统计失败"));
        var sync=new PlanSyncService(plans,accounts,credentials,list,port,store,tx);var result=sync.sync(tenant,null);
        assertEquals(2,result.accounts().size());assertTrue(result.accounts().stream().allMatch(r->r.plans()==1 && !r.warnings().isEmpty()));
        var capture=ArgumentCaptor.forClass(AdPlan.class);verify(plans,times(2)).save(capture.capture());
        assertNotEquals(capture.getAllValues().get(0).id(),capture.getAllValues().get(1).id());
        assertTrue(capture.getAllValues().stream().allMatch(p->p.status()==PlanStatus.PAUSED && p.source()==PlanSource.EXTERNAL && p.bindings().isEmpty()));
        verify(list).list(credential,null);verify(list).list(secondCredential,null);verify(store,never()).report(any(),any(),any(),any(),any(),any());
    }
    private OauthAccount oauth(AccountId id) { return new OauthAccount(id,tenant,Platform.QIANCHUAN,new AdvertiserId("123"),"账户",null,null,Instant.now().plusSeconds(3600),null,"ACTIVE",List.of()); }
    @Test void overviewCoversAllFilteredPagesUsesWeightedRoiAndNeverTreatsStaleReportsAsToday() throws Exception {
        var second=new AdPlan(new PlanId("plan-b"),tenant,account,plan.awemeId(),new PlanName("另一个计划"),PlanStatus.DELIVERING,PlanSource.EXTERNAL,
                plan.setting(),List.of(),new QcPlanId("457"),"DELIVERY_OK",null,0,null,List.of());
        when(plans.findByTenant(tenant)).thenReturn(List.of(plan,second));
        var snapshots=mock(com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository.class);
        var products=mock(com.lynx.ad.modules.catalog.domain.port.ProductRepository.class);
        var bindings=mock(com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore.class);
        var accounts=mock(OauthAccountRepository.class);when(accounts.findByTenant(tenant)).thenReturn(List.of(oauth(account)));
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));var now=Instant.now();
        var raw=json.readTree("{\"ad_info\":{\"budget\":300,\"create_time\":\"2026-10-06 12:00:00\"}}");
        var one=json.readTree("{\"stat_cost\":{\"Value\":\"100\"},\"total_pay_order_gmv_include_coupon_for_roi2\":{\"Value\":\"100\"},\"total_pay_order_count_for_roi2\":{\"Value\":\"1\"},\"total_prepay_and_pay_order_roi2\":{\"Value\":\"1\"}}");
        var two=json.readTree("{\"stat_cost\":{\"Value\":\"300\"},\"total_pay_order_gmv_include_coupon_for_roi2\":{\"Value\":\"600\"},\"total_pay_order_count_for_roi2\":{\"Value\":\"2\"},\"total_prepay_and_pay_order_roi2\":{\"Value\":\"2\"}}");
        Map<String,PlanRemoteStore.Remote> cache=new HashMap<>();
        cache.put(id.value(),new PlanRemoteStore.Remote(id.value(),raw,null,null,now,one,one,today,now));
        cache.put("plan-b",new PlanRemoteStore.Remote("plan-b",raw,null,null,now,two,two,today,now));when(store.list(tenant)).thenReturn(cache);
        var query=new PlanQueryService(plans,mock(AdListPort.class),accounts,credentials,snapshots,products,bindings,store);
        var result=query.list(tenant,1,1);assertEquals(1,result.records().size());assertEquals(2,result.total());
        assertEquals(0,result.summary().roi().compareTo(new BigDecimal("1.75")));assertEquals(3,result.summary().orders());
        cache.put("plan-b",new PlanRemoteStore.Remote("plan-b",raw,null,null,now,two,two,today.minusDays(1),now));
        var stale=query.list(tenant,1,10);assertNull(stale.records().stream().filter(p->p.planId().equals("plan-b")).findFirst().orElseThrow().costYuan());
        assertEquals(1,stale.summary().covered());verifyNoInteractions(credentials);
    }
}
