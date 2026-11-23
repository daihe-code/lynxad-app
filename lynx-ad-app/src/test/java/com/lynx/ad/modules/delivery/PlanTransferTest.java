package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.api.dto.PlanTransferRequest;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.PlanTransferGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore.*;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PlanTransferTest {
    final TenantId tenant=new TenantId("tenant-a");final PlanId root=new PlanId("root");
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();final AdPlanRepository plans=mock(AdPlanRepository.class);
    final PlanTransferStore store=mock(PlanTransferStore.class);final CreationStore creations=mock(CreationStore.class);
    final PlanRemoteStore remote=mock(PlanRemoteStore.class);final PlanTransferGateway gateway=mock(PlanTransferGateway.class);
    final BillingTrialService billing=mock(BillingTrialService.class);final Credential credential=mock(Credential.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final Map<String,AdPlan> physical=new HashMap<>();final Map<String,Attempt> attempts=new HashMap<>();
    final Map<String,JsonNode> platform=new HashMap<>();final List<String> calls=new ArrayList<>();
    Group group;PlanTransferService service;boolean enableRejected,detailUnavailable,updateRejected;
    @BeforeEach void setup() throws Exception {
        when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        var source=plan(root.value(),"111",SmartBidType.CONSERVATIVE,PlanStatus.DELIVERING,"100",null);physical.put(root.value(),source);platform.put("111",detail(source,"DELIVERY_OK"));
        when(plans.findById(any(),any())).thenAnswer(i->tenant.equals(i.getArgument(0))?Optional.ofNullable(physical.get(((PlanId)i.getArgument(1)).value())):Optional.empty());
        when(plans.findByTenant(tenant)).thenAnswer(i->new ArrayList<>(physical.values()));
        doAnswer(i->{var p=(AdPlan)i.getArgument(0);physical.put(p.id().value(),p);return null;}).when(plans).save(any());
        when(store.group(eq(tenant),any())).thenAnswer(i->group);
        when(store.groups(tenant)).thenAnswer(i->{if(group==null)return Map.of();Map<String,Group> groups=new HashMap<>();groups.put(group.root(),group);if(group.conservative()!=null)groups.put(group.conservative(),group);if(group.custom()!=null)groups.put(group.custom(),group);return groups;});
        when(store.activeId(eq(tenant),any())).thenAnswer(i->group==null?i.getArgument(1):new PlanId(group.active()));
        when(store.attempt(eq(tenant),anyString())).thenAnswer(i->Optional.ofNullable(attempts.get(i.getArgument(1))));
        doAnswer(i->{if(group!=null&&group.requestKey()!=null)throw new BizException(1523,"pending");return null;}).when(store).assertIdle(eq(tenant),any());
        doAnswer(i->{Attempt a=i.getArgument(0);attempts.put(a.key(),a);group=new Group(group.root(),group.active(),group.conservative(),group.custom(),a.key());return null;}).when(store).claim(any(),anyString());
        doAnswer(i->{String key=i.getArgument(1),phase=i.getArgument(2),qc=i.getArgument(3);var a=attempts.get(key);attempts.put(key,new Attempt(a.tenant(),a.key(),a.root(),a.source(),a.target(),a.request(),a.payload(),a.sourceEnabled(),qc==null?a.qcId():qc,a.status(),phase,a.error(),Instant.now()));return null;}).when(store).phase(eq(tenant),anyString(),anyString(),nullable(String.class));
        doAnswer(i->{String id=i.getArgument(3);SmartBidType mode=i.getArgument(2);group=new Group(group.root(),group.active(),mode==SmartBidType.CONSERVATIVE?id:group.conservative(),mode==SmartBidType.CUSTOM?id:group.custom(),group.requestKey());return null;}).when(store).member(eq(tenant),anyString(),any(),anyString());
        doAnswer(i->{String key=i.getArgument(1),state=i.getArgument(2),error=i.getArgument(3);var a=attempts.get(key);attempts.put(key,new Attempt(a.tenant(),a.key(),a.root(),a.source(),a.target(),a.request(),a.payload(),a.sourceEnabled(),a.qcId(),state,a.phase(),error,Instant.now()));
            if(!"UNCERTAIN".equals(state))group=new Group(group.root(),"SUCCEEDED".equals(state)?a.target():group.active(),group.conservative(),group.custom(),null);return null;}).when(store).finish(eq(tenant),anyString(),anyString(),nullable(String.class));
        when(gateway.credential(any())).thenReturn(credential);
        when(gateway.detail(any())).thenAnswer(i->{var p=(AdPlan)i.getArgument(0);String qc=p.qcPlanId().orElseThrow().value();if(detailUnavailable&&!qc.equals("111"))throw new BizException(1406,"read failed");return platform.get(qc).deepCopy();});
        doAnswer(i->{String id=i.getArgument(1);boolean enabled=i.getArgument(2);calls.add((enabled?"ENABLE ":"DISABLE ")+id);
            if(enableRejected && enabled&&!id.equals("111"))throw new BizException(1406,"enable rejected");
            ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get(id)).put("status",enabled?"DELIVERY_OK":"DISABLE");return null;}).when(gateway).status(eq(credential),anyString(),anyBoolean());
        doAnswer(i->{calls.add("UPDATE "+i.getArgument(1));if(updateRejected)throw new BizException(1406,"update rejected");var ds=(com.fasterxml.jackson.databind.node.ObjectNode)platform.get(i.getArgument(1)).path("delivery_setting");BigDecimal budget=i.getArgument(2);if(budget!=null)ds.put("budget",budget);BigDecimal roi=i.getArgument(3);if(roi!=null)ds.put("roi2_goal",roi);return null;}).when(gateway).settings(eq(credential),anyString(),any(),nullable(BigDecimal.class));
        doAnswer(i -> {
            String id = i.getArgument(1), name = i.getArgument(2), scheduleType = i.getArgument(3);
            calls.add("DETAILS " + id);
            var detail=(com.fasterxml.jackson.databind.node.ObjectNode)platform.get(id);
            if (name != null) detail.put("name", name);
            if ("DATE_RANGE".equals(scheduleType)) {
                var schedule=detail.withObject("/delivery_setting");
                schedule.put("video_schedule_type","SCHEDULE_START_END");
                schedule.put("end_time",i.<LocalDate>getArgument(4).toString());
            }
            return null;
        }).when(gateway).details(eq(credential), anyString(), nullable(String.class), nullable(String.class), nullable(LocalDate.class));
        var payload=json.readTree("""
          {"advertiser_id":123,"aweme_id":999,"name":"原计划","product_ids":[101],"marketing_goal":"VIDEO_PROM_GOODS",
           "delivery_setting":{"smart_bid_type":"SMART_BID_CONSERVATIVE","budget":100,"estimate_roi_goal":0.24,"estimate_convert":3,"video_schedule_type":"SCHEDULE_FROM_NOW"},
           "multi_product_creative_list":[{"product_id":101,"hide_in_aweme":true,"video_material":[{"video_id":"v123","video_cover_id":"i123"}],"title_material":[{"title":"测试真实素材标题"}]}]}
          """);
        ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("111")).set("multi_product_creative_list",payload.path("multi_product_creative_list").deepCopy());
        var target=plan("cost","222",SmartBidType.CUSTOM,PlanStatus.PAUSED,"300","4.18");
        target.rename(PlanName.fromPlatform("逻辑计划_转投控成本"));
        physical.put("cost",target);
        platform.put("222",detail(target,"DISABLE"));
        group=new Group("root","root","root","cost",null);
        service=new PlanTransferService(plans,store,remote,gateway,billing,json,manager);
    }
    AdPlan plan(String id,String qc,SmartBidType mode,PlanStatus status,String budget,String roi) {
        return new AdPlan(new PlanId(id),tenant,new AccountId("account"),new AwemeId("999"),new PlanName("逻辑计划"),status,PlanSource.LINGTOU,
                new DeliverySetting(mode,Money.ofYuan(new BigDecimal(budget)),roi==null?Optional.empty():Optional.of(new RoiGoal(new BigDecimal(roi))),Schedule.fromNow(),AudienceJson.empty()),List.of(new QcProductId("101")),new QcPlanId(qc),status.name(),null,1,null,List.of());
    }
    JsonNode detail(AdPlan p,String status){var d=json.createObjectNode();d.put("ad_id",p.qcPlanId().orElseThrow().value()).put("status",status).put("name",p.name().text());var s=d.putObject("delivery_setting");s.put("smart_bid_type",p.setting().bidType().oceanValue()).put("budget",p.setting().dailyBudget().amount());p.setting().roi2Goal().ifPresent(r->s.put("roi2_goal",r.value()));return d;}
    PlanTransferRequest cost(String key){return new PlanTransferRequest(key,"111","SMART_BID_CUSTOM",new BigDecimal("300"),new BigDecimal("4.18"));}
    private void removeCounterpart() {
        physical.remove("cost");
        platform.remove("222");
        group=null;
    }
    private void saveLegacyAttempt(PlanTransferRequest request,String status,String phase,String targetQcId,String error) throws Exception {
        removeCounterpart();
        var payload=json.readTree("""
            {"name":"逻辑计划_转投控成本","product_ids":[101],
             "multi_product_creative_list":[{"product_id":101,"video_material":[{"video_id":"v123"}]}]}
            """);
        var attempt=new Attempt(tenant.value(),request.requestKey(),"root","root","cost",json.writeValueAsString(request),
                payload.toString(),true,targetQcId,status,phase,error,Instant.now());
        attempts.put(attempt.key(),attempt);
        group=new Group("root","root","root",null,"FAILED".equals(status)?null:attempt.key());
    }
    @Test void unpairedPlanIsBlockedBeforeRecommendationsOrAnyPlatformWrite() {
        removeCounterpart();
        var context=service.context(tenant,root);
        assertFalse(context.available());
        assertFalse(context.reuse());
        assertEquals("暂无另一种投放方式的关联计划，无法转投。",context.reason());
        assertThrows(BizException.class,()->service.recommendations(tenant,root));
        assertEquals(context.reason(),assertThrows(BizException.class,()->service.transfer(tenant,root,cost("first-key"))).getMessage());

        group=new Group("root","root","root",null,null);
        assertEquals(context.reason(),assertThrows(BizException.class,()->service.transfer(tenant,root,cost("retry-key"))).getMessage());
        assertEquals(PlanStatus.DELIVERING,physical.get(root.value()).status());
        assertTrue(attempts.isEmpty());
        verifyNoInteractions(gateway);
        verify(store,never()).claim(any(),anyString());
    }
    @Test void transferRecommendationsUseTheExistingTargetsLiveProducts() throws Exception {
        var volume=physical.get(root.value());
        var detail=(com.fasterxml.jackson.databind.node.ObjectNode)platform.get("111");
        ((com.fasterxml.jackson.databind.node.ObjectNode)detail.path("multi_product_creative_list").get(0)).put("product_id",102);
        var quote=json.readTree("{\"budget\":{\"budget\":50}}");
        when(gateway.recommendations(volume,List.of("102"),SmartBidType.CONSERVATIVE)).thenReturn(quote);
        var source=new PlanId("cost");
        assertTrue(service.context(tenant,source).reuse());
        assertEquals(quote,service.recommendations(tenant,source));
        verify(gateway).recommendations(volume,List.of("102"),SmartBidType.CONSERVATIVE);
        assertTrue(calls.isEmpty());
    }
    @Test void legacyProductConflictReceiptIsReplayedWithoutWrites() throws Exception {
        var request=cost("legacy-failed");
        String error="千川拒绝请求：商品全域投放互斥（错误码 40000）";
        saveLegacyAttempt(request,"FAILED","ROLLING_BACK",null,error);
        var result=service.transfer(tenant,root,request);
        assertEquals("FAILED",result.status());
        assertTrue(result.platformTransferRequired());
        assertEquals("该商品已有投放计划，暂不支持切换投放方式。原计划状态已恢复。",result.message());
        verifyNoInteractions(gateway);
    }
    @Test void legacyProductConflictCanStillRestoreTheSource() throws Exception {
        var request=cost("pending-recovery");
        saveLegacyAttempt(request,"UNCERTAIN","ROLLING_BACK",null,"千川拒绝请求：商品全域投放互斥（错误码 40000）");
        physical.get(root.value()).apply(PlanStatus.PAUSED);
        ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("111")).put("status","DISABLE");

        var context=service.context(tenant,root);
        assertEquals(request.requestKey(),context.pendingKey());
        assertEquals("转投结果待核查，请稍后查询。",context.pendingError());
        assertEquals("FAILED",service.reconcile(tenant,root).status());
        assertEquals(List.of("ENABLE 111"),calls);
        assertNull(group.requestKey());
        assertEquals(PlanStatus.DELIVERING,physical.get(root.value()).status());
    }
    @Test void savingAnExistingModeMemberUpdatesItsNameAndDatesBeforePausingTheSource() {
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        var end=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(15);
        doAnswer(i->{calls.add("DETAILS "+i.getArgument(1));var detail=(com.fasterxml.jackson.databind.node.ObjectNode)platform.get(i.getArgument(1));detail.put("name",(String)i.getArgument(2));detail.withObject("/delivery_setting").put("video_schedule_type","SCHEDULE_START_END").put("end_time",end.toString());return null;}).when(gateway).details(eq(credential),eq("111"),anyString(),eq("DATE_RANGE"),eq(end));
        var request=new PlanTransferRequest("edit-existing","222","SMART_BID_CONSERVATIVE",new BigDecimal("150"),null,false,"更新放量计划","DATE_RANGE",end);
        var result=service.transfer(tenant,new PlanId(group.custom()),request);
        assertEquals("SUCCEEDED",result.status());assertEquals(List.of("UPDATE 111","DETAILS 111","DISABLE 222","ENABLE 111"),calls);
        assertEquals("更新放量计划_转投放量",physical.get("root").name().text());assertEquals(end,physical.get("root").setting().schedule().endDate());
    }
    @Test void unconfirmedExtraSettingsNeverPauseOrEnableAnotherPlanAndRecoveryDoesNotResendThem() {
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        doNothing().when(gateway).details(credential,"111","待确认的名称_转投放量",null,null);
        var request=new PlanTransferRequest("pending-fields","222","SMART_BID_CONSERVATIVE",new BigDecimal("100"),null,false,"待确认的名称",null,null);
        assertEquals("UNCERTAIN",service.transfer(tenant,new PlanId(group.custom()),request).status());assertTrue(calls.isEmpty());
        verify(gateway,times(1)).details(credential,"111","待确认的名称_转投放量",null,null);
        assertEquals("FAILED",service.reconcile(tenant,new PlanId(group.custom())).status());assertTrue(calls.isEmpty());
        verify(gateway,times(1)).details(any(),anyString(),any(),any(),any());
    }
    @Test void invalidScheduleOrDuplicateNameFailsBeforeAnyWrite() {
        var yesterday=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        var invalid=new PlanTransferRequest("invalid-date","111","SMART_BID_CUSTOM",new BigDecimal("300"),new BigDecimal("4.2"),false,null,"DATE_RANGE",yesterday);
        assertThrows(BizException.class,()->service.transfer(tenant,root,invalid));assertTrue(calls.isEmpty());
        when(plans.existsUnfinishedName(any(),any(),any(),any())).thenReturn(true);
        var duplicate=new PlanTransferRequest("duplicate-name","111","SMART_BID_CUSTOM",new BigDecimal("300"),new BigDecimal("4.2"),false,"已存在的计划",null,null);
        assertThrows(BizException.class,()->service.transfer(tenant,root,duplicate));assertTrue(calls.isEmpty());
    }
    @Test void enabledTargetWithUnconfirmedNameKeepsTheClaimUntilAllSavedFieldsMatch() {
        doAnswer(i->{calls.add("ENABLE 222");((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("222")).put("status","DELIVERY_OK").put("name","未确认的名称");return null;}).when(gateway).status(credential,"222",true);
        var request=new PlanTransferRequest("verify-name","111","SMART_BID_CUSTOM",new BigDecimal("300"),new BigDecimal("4.2"),false,"请求保存的名称",null,null);
        assertEquals("UNCERTAIN",service.transfer(tenant,root,request).status());
        assertEquals("UNCERTAIN",service.reconcile(tenant,root).status());assertNotNull(group.requestKey());
        ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("222")).put("name","请求保存的名称_转投控成本");
        assertEquals("SUCCEEDED",service.reconcile(tenant,root).status());assertNull(group.requestKey());
        verify(gateway,times(1)).status(credential,"222",true);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"300,0.01","100000.01,100","999999999.99,100"})
    void transferAcceptsPrdUpperBudgetAndRoiBoundaries(String budget,String roi) {
        var request=new PlanTransferRequest("prd-range","111","SMART_BID_CUSTOM",new BigDecimal(budget),new BigDecimal(roi));
        assertEquals("SUCCEEDED",service.transfer(tenant,root,request).status());
        var settings=platform.get("222").path("delivery_setting");
        assertEquals(0,new BigDecimal(budget).compareTo(settings.path("budget").decimalValue()));
        assertEquals(0,new BigDecimal(roi).compareTo(settings.path("roi2_goal").decimalValue()));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"300,0","300,10000.01","300,0.001","1000000000,4.7","300.001,4.7"})
    void invalidPrdRangesCannotChangeEitherPlan(String budget,String roi) {
        assertThrows(BizException.class,()->service.transfer(tenant,root,new PlanTransferRequest("prd-range","111","SMART_BID_CUSTOM",new BigDecimal(budget),new BigDecimal(roi))));
        assertTrue(calls.isEmpty());verifyNoInteractions(gateway);
    }
    @Test void transferCostBudgetBelow300IsRejectedBeforePausing() {
        for(String amount:List.of("50","99.99","100","299.99")) {
            var request=new PlanTransferRequest("budget-minimum","111","SMART_BID_CUSTOM",new BigDecimal(amount),new BigDecimal("4.18"));
            assertTrue(assertThrows(BizException.class,()->service.transfer(tenant,root,request)).getMessage().contains("不能低于 300 元"));
        }
        assertTrue(calls.isEmpty());assertEquals(PlanStatus.DELIVERING,physical.get("root").status());
    }
    @Test void transferCostBudgetAcceptsExactly300WithoutIncreasingIt() {
        var request=new PlanTransferRequest("budget-minimum","111","SMART_BID_CUSTOM",new BigDecimal("300"),new BigDecimal("4.18"));
        assertEquals("SUCCEEDED",service.transfer(tenant,root,request).status());
        assertEquals(0,new BigDecimal("300").compareTo(physical.get("cost").setting().dailyBudget().amount()));
    }
    @Test void legacyCreateReceiptRegistersTheExistingTargetDuringReconciliation() throws Exception {
        var request=cost("legacy-created");
        saveLegacyAttempt(request,"UNCERTAIN","CREATED","222",null);
        physical.get(root.value()).apply(PlanStatus.PAUSED);
        ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("111")).put("status","DISABLE");
        var target=plan("cost","222",SmartBidType.CUSTOM,PlanStatus.PAUSED,"300","4.18");
        target.rename(PlanName.fromPlatform("逻辑计划_转投控成本"));
        platform.put("222",detail(target,"DISABLE"));

        var result=service.reconcile(tenant,root);

        assertEquals("SUCCEEDED",result.status());
        assertEquals("222",result.qcPlanId());
        assertEquals(List.of("ENABLE 222"),calls);
        assertEquals(List.of(new QcProductId("101")),physical.get("cost").productIds());
        assertEquals(PlanSource.LINGTOU,physical.get("cost").source());
        assertNull(group.requestKey());
    }
    @Test void threeTransfersReuseOnlyTwoPlansAndKeepModeSettingsSeparate(){
        service.transfer(tenant,root,cost("first-key"));
        var back=new PlanTransferRequest("second-key","222","SMART_BID_CONSERVATIVE",new BigDecimal("150"),null);
        assertEquals("SUCCEEDED",service.transfer(tenant,new PlanId(group.custom()),back).status());assertEquals("root",group.active());assertTrue(physical.get("root").setting().roi2Goal().isEmpty());
        var context=service.context(tenant,root);assertTrue(context.reuse());assertEquals(new BigDecimal("4.18"),context.roi2Goal());assertEquals(0,new BigDecimal("300").compareTo(context.budget()));
        assertEquals("SUCCEEDED",service.transfer(tenant,root,cost("third-key")).status());assertEquals(2,physical.size());
        assertEquals(List.of("DISABLE 111","ENABLE 222","UPDATE 111","DETAILS 111","DISABLE 222","ENABLE 111","DISABLE 111","ENABLE 222"),calls);
    }
    @Test void repeatedKeyReplaysWithoutAnyAdditionalWrites(){var req=cost("first-key");service.transfer(tenant,root,req);var before=List.copyOf(calls);assertEquals("SUCCEEDED",service.transfer(tenant,root,req).status());assertEquals(before,calls);}
    @Test void changedParametersOnSameKeyAreRejected(){service.transfer(tenant,root,cost("first-key"));assertThrows(BizException.class,()->service.transfer(tenant,root,new PlanTransferRequest("first-key","111","SMART_BID_CUSTOM",new BigDecimal("400"),new BigDecimal("5"))));}
    @Test void rejectedEnableDisablesTargetBeforeRestoringSource(){enableRejected=true;assertEquals("FAILED",service.transfer(tenant,root,cost("first-key")).status());assertEquals(List.of("DISABLE 111","ENABLE 222","DISABLE 222","ENABLE 111"),calls);assertEquals("root",group.active());assertEquals(2,physical.size());}
    @Test void receiptAndLockSurviveFailedReadAfterEnable() {
        doAnswer(i->{
            calls.add("ENABLE 222");
            ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("222")).put("status","DELIVERY_OK");
            detailUnavailable=true;
            return null;
        }).when(gateway).status(credential,"222",true);
        assertEquals("UNCERTAIN",service.transfer(tenant,root,cost("first-key")).status());
        assertEquals("222",attempts.get("first-key").qcId());
        detailUnavailable=false;
        assertEquals("SUCCEEDED",service.reconcile(tenant,root).status());
        assertEquals(List.of("DISABLE 111","ENABLE 222"),calls);
    }
    @Test void invalidGoalAndStaleQcIdFailWithoutWrites(){assertThrows(BizException.class,()->service.transfer(tenant,root,new PlanTransferRequest("first-key","111","SMART_BID_CUSTOM",new BigDecimal("100"),BigDecimal.ZERO)));assertThrows(BizException.class,()->service.transfer(tenant,root,new PlanTransferRequest("first-key","wrong","SMART_BID_CUSTOM",new BigDecimal("100"),BigDecimal.ONE)));assertTrue(calls.isEmpty());}
    @Test void tenantCannotReadOrTransferAnothersPlan(){assertThrows(BizException.class,()->service.transfer(new TenantId("other"),root,cost("first-key")));assertTrue(calls.isEmpty());verifyNoInteractions(gateway);}
    @Test void targetUpdateRejectionKeepsSourceRunning(){service.transfer(tenant,root,cost("first-key"));calls.clear();updateRejected=true;assertEquals("FAILED",service.transfer(tenant,new PlanId(group.custom()),new PlanTransferRequest("second-key","222","SMART_BID_CONSERVATIVE",new BigDecimal("150"),null)).status());assertFalse(calls.contains("DISABLE 222"));}
    @Test void uncertainLegacyCreateWithNoSearchMatchKeepsTheLock() throws Exception {
        saveLegacyAttempt(cost("legacy-creating"),"UNCERTAIN","CREATING",null,null);
        when(gateway.findCreated(credential,SmartBidType.CUSTOM,"逻辑计划_转投控成本")).thenReturn(List.of());

        assertEquals("UNCERTAIN",service.reconcile(tenant,root).status());
        assertEquals("legacy-creating",group.requestKey());
        assertTrue(calls.isEmpty());
    }
    @Test void returningToUnchangedBudgetLabelsTheTargetOnceAndDoesNotRewriteBudget(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        assertEquals("SUCCEEDED",service.transfer(tenant,new PlanId(group.custom()),new PlanTransferRequest("second-key","222","SMART_BID_CONSERVATIVE",new BigDecimal("100"),null)).status());
        assertEquals(List.of("DETAILS 111","DISABLE 222","ENABLE 111"),calls);
        assertEquals("逻辑计划_转投放量", physical.get("root").name().text());
        verifyNoMoreInteractionsAfterSettings();
    }
    private void verifyNoMoreInteractionsAfterSettings(){verify(gateway,never()).settings(any(),anyString(),any(),any());}
    @Test void changedRoiDoesNotResubmitAnUnchangedBudget(){
        service.transfer(tenant,root,cost("first-key"));service.transfer(tenant,new PlanId(group.custom()),new PlanTransferRequest("second-key","222","SMART_BID_CONSERVATIVE",new BigDecimal("100"),null));
        assertEquals("SUCCEEDED",service.transfer(tenant,root,new PlanTransferRequest("third-key","111","SMART_BID_CUSTOM",new BigDecimal("300"),new BigDecimal("4.2"))).status());
        verify(gateway).settings(eq(credential),eq("222"),isNull(),eq(new BigDecimal("4.2")));
    }
    @Test void existingSwitchPreservesFreshSettingsAndNeverCreatesOrUpdates(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("111").path("delivery_setting")).put("budget",120);
        assertEquals("SUCCEEDED",service.switchExisting(tenant,new PlanId(group.custom()),"second-key","222").status());
        assertEquals(List.of("DISABLE 222","ENABLE 111"),calls);assertEquals(0,new BigDecimal("120").compareTo(physical.get("root").setting().dailyBudget().amount()));
        ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("222").path("delivery_setting")).put("budget",350).put("roi2_goal",1.9);calls.clear();
        assertEquals("SUCCEEDED",service.switchExisting(tenant,root,"third-key","111").status());
        assertEquals(List.of("DISABLE 111","ENABLE 222"),calls);assertEquals(2,physical.size());
        var context=service.context(tenant,new PlanId(group.custom()));assertEquals("222",context.currentQcPlanId());assertEquals(0,new BigDecimal("350").compareTo(physical.get(group.active()).setting().dailyBudget().amount()));
        assertEquals(0,new BigDecimal("1.9").compareTo(physical.get(group.active()).setting().roi2Goal().orElseThrow().value()));verify(gateway,never()).settings(any(),anyString(),any(),any());
    }
    @Test void existingSwitchRetryReplaysSameReceiptWithoutTogglingAgain(){
        service.transfer(tenant,root,cost("first-key"));service.switchExisting(tenant,new PlanId(group.custom()),"second-key","222");var before=List.copyOf(calls);
        assertEquals("SUCCEEDED",service.switchExisting(tenant,new PlanId(group.custom()),"second-key","222").status());assertEquals(before,calls);assertEquals("root",group.active());
        assertThrows(BizException.class,()->service.switchExisting(tenant,root,"second-key","111"));assertEquals(before,calls);
    }
    @Test void existingSwitchWithoutCounterpartRejectsBeforePlatformWrites(){
        removeCounterpart();
        assertThrows(BizException.class,()->service.switchExisting(tenant,root,"first-key","111"));assertTrue(calls.isEmpty());verifyNoInteractions(gateway);verify(store,never()).ensure(any());
    }
    @Test void staleExistingSwitchDoesNotToggleTheNewCurrentPlan(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        assertThrows(BizException.class,()->service.switchExisting(tenant,new PlanId(group.custom()),"second-key","111"));assertTrue(calls.isEmpty());assertEquals("222",physical.get(group.active()).qcPlanId().orElseThrow().value());
    }
    @Test void eitherRowHasItsOwnTransferContextEvenWhenBothArePaused(){
        service.transfer(tenant,root,cost("first-key"));
        var costId=new PlanId(group.custom());physical.values().forEach(p->p.apply(PlanStatus.PAUSED));
        var volumeContext=service.context(tenant,root);var costContext=service.context(tenant,costId);
        assertTrue(volumeContext.available());assertTrue(costContext.available());
        assertEquals("111",volumeContext.currentQcPlanId());assertEquals("222",costContext.currentQcPlanId());
        assertEquals("SMART_BID_CUSTOM",volumeContext.targetMode());assertEquals("SMART_BID_CONSERVATIVE",costContext.targetMode());
        assertEquals("111",costContext.counterpartQcPlanId());assertEquals(costId.value(),costContext.planId());
        assertEquals(root.value(),costContext.counterpartPlanId());assertEquals(0,new BigDecimal("100").compareTo(costContext.budget()));
    }
    @Test void pausedNonActiveRowCanInitiateSwitchAndReuseTheOtherPlan(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        physical.values().forEach(p->p.apply(PlanStatus.PAUSED));platform.values().forEach(d->((com.fasterxml.jackson.databind.node.ObjectNode)d).put("status","DISABLE"));
        assertEquals("SUCCEEDED",service.switchExisting(tenant,root,"second-key","111").status());
        assertEquals(List.of("DISABLE 111","ENABLE 222"),calls);
        assertEquals(PlanStatus.PAUSED,physical.get("root").status());assertEquals(PlanStatus.DELIVERING,physical.get(group.custom()).status());
    }
    @Test void selectingPausedRowWhoseCounterpartIsAlreadyEnabledDoesNotToggleOrCreateIt(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        assertEquals("SUCCEEDED",service.switchExisting(tenant,root,"second-key","111").status());
        assertEquals(List.of("DISABLE 111"),calls);assertEquals("222",physical.get(group.active()).qcPlanId().orElseThrow().value());
    }
    @Test void normalEnablePausesRunningCounterpartBeforeEnablingSelectedMode(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        assertEquals("SUCCEEDED",service.enableExisting(tenant,root).status());
        assertEquals(List.of("DISABLE 222","ENABLE 111"),calls);assertEquals("root",group.active());
        assertEquals(PlanStatus.PAUSED,physical.get(group.custom()).status());assertEquals(PlanStatus.DELIVERING,physical.get("root").status());
    }
    @Test void pauseAcknowledgementWithoutConfirmedPauseNeverEnablesTarget(){
        doAnswer(i->{calls.add("DISABLE 111");return null;}).when(gateway).status(credential,"111",false);
        assertEquals("UNCERTAIN",service.transfer(tenant,root,cost("first-key")).status());
        assertEquals(List.of("DISABLE 111"),calls);assertNotNull(group.requestKey());
        assertThrows(BizException.class,()->service.transfer(tenant,root,cost("second-key")));
    }
    @Test void normalEnableDoesNotOverwriteTheSelectedModesBudgetOrRoi(){
        service.transfer(tenant,root,cost("first-key"));service.enableExisting(tenant,root);calls.clear();
        ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("222").path("delivery_setting")).put("budget",350).put("roi2_goal",1.9);
        var result=service.enableExisting(tenant,new PlanId(group.custom()));assertEquals("SUCCEEDED",result.status());
        assertEquals(List.of("DISABLE 111","ENABLE 222"),calls);assertEquals(0,new BigDecimal("350").compareTo(physical.get(group.custom()).setting().dailyBudget().amount()));
        assertEquals(0,new BigDecimal("1.9").compareTo(physical.get(group.custom()).setting().roi2Goal().orElseThrow().value()));
        verify(gateway,never()).settings(any(),anyString(),any(),any());
    }
    @Test void normalEnableAlsoPausesAnEnabledCounterpartPendingAudit(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        physical.get(group.custom()).apply(PlanStatus.PENDING_AUDIT);((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("222")).put("status","PENDING_AUDIT");
        assertEquals("SUCCEEDED",service.enableExisting(tenant,root).status());assertEquals(List.of("DISABLE 222","ENABLE 111"),calls);
    }
    @Test void rejectedEnableCannotResumeSourceUntilTargetPauseIsConfirmed(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        doAnswer(i->{calls.add("ENABLE 111");((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("111")).put("status","DELIVERY_OK");throw new BizException(1406,"enable rejected");}).when(gateway).status(credential,"111",true);
        doAnswer(i->{calls.add("DISABLE 111");return null;}).when(gateway).status(credential,"111",false);
        assertEquals("UNCERTAIN",service.enableExisting(tenant,root).status());assertEquals(List.of("DISABLE 222","ENABLE 111","DISABLE 111"),calls);
        assertNotNull(group.requestKey());assertEquals(PlanStatus.PAUSED,physical.get(group.custom()).status());
    }
    @Test void rejectedExistingEnableRestoresCurrentPlan(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        doAnswer(i->{calls.add("ENABLE 111");throw new BizException(1406,"enable rejected");}).when(gateway).status(credential,"111",true);
        assertEquals("FAILED",service.switchExisting(tenant,new PlanId(group.custom()),"second-key","222").status());
        assertEquals(List.of("DISABLE 222","ENABLE 111","DISABLE 111","ENABLE 222"),calls);assertEquals("222",physical.get(group.active()).qcPlanId().orElseThrow().value());
    }
    @Test void uncertainExistingSwitchReconcilesOnlyStatusWithoutOverwritingNewBudget(){
        service.transfer(tenant,root,cost("first-key"));calls.clear();
        doAnswer(i->{calls.add("DISABLE 222");((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("222")).put("status","DISABLE");throw new BizException(1523,"response lost");}).when(gateway).status(credential,"222",false);
        assertEquals("UNCERTAIN",service.switchExisting(tenant,new PlanId(group.custom()),"second-key","222").status());assertNotNull(group.requestKey());assertEquals("111",attempts.get("second-key").qcId());
        ((com.fasterxml.jackson.databind.node.ObjectNode)platform.get("111").path("delivery_setting")).put("budget",170);
        assertEquals("SUCCEEDED",service.reconcile(tenant,root).status());assertEquals(List.of("DISABLE 222","ENABLE 111"),calls);assertEquals(0,new BigDecimal("170").compareTo(physical.get("root").setting().dailyBudget().amount()));
        assertNull(group.requestKey());verify(gateway,never()).settings(any(),anyString(),any(),any());
    }
    @Test void existingSwitchIsTenantScoped(){assertThrows(BizException.class,()->service.switchExisting(new TenantId("other"),root,"first-key","111"));assertTrue(calls.isEmpty());verifyNoInteractions(gateway);}
    @Test void listAndDetailKeepBothTransferredModesAndUnrelatedPausedPlans(){
        service.transfer(tenant,root,cost("first-key"));physical.put("unrelated",plan("unrelated","333",SmartBidType.CUSTOM,PlanStatus.PAUSED,"600","5"));
        var query=new PlanQueryService(plans,mock(AdListPort.class),mock(com.lynx.ad.modules.identity.domain.port.OauthAccountRepository.class),mock(com.lynx.ad.modules.identity.application.QianchuanCredentialFacade.class),
                mock(com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository.class),mock(com.lynx.ad.modules.catalog.domain.port.ProductRepository.class),mock(AdPlanCreativeBindingStore.class),remote);
        query.transferStore(store);query.creationStore(creations);
        var page=query.list(tenant,1,20);assertEquals(3,page.total());assertTrue(page.records().stream().anyMatch(p->p.planId().equals("unrelated")&&p.status().equals("PAUSED")));
        var volume=page.records().stream().filter(p->p.planId().equals("root")).findFirst().orElseThrow();assertEquals("111",volume.qcPlanId());assertEquals("PAUSED",volume.status());assertEquals("SMART_BID_CONSERVATIVE",volume.smartBidType());
        var cost=page.records().stream().filter(p->p.planId().equals(group.active())).findFirst().orElseThrow();assertEquals("222",cost.qcPlanId());assertEquals("SMART_BID_CUSTOM",cost.smartBidType());
        assertEquals("root",query.get(tenant,root).planId());assertEquals("111",query.get(tenant,root).qcPlanId());assertEquals(group.active(),query.get(tenant,new PlanId(group.active())).planId());
    }
}
