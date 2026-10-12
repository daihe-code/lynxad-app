package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.audit.application.SuccessAuditCommand;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.api.dto.*;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanRemoteStore;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    private void taskWithStatus(String status) {
        when(port.controls(credential,"456","SMART_BOOST")).thenReturn(List.of(json.createObjectNode().put("id","1878743575303385").put("name","起量任务").put("task_status",status)));
        when(port.detail(credential,"456")).thenReturn(json.createObjectNode().put("status","DELIVERY_OK"));
    }
    @ParameterizedTest @CsvSource({"PROCESSING,PAUSE,PAUSE","DISABLE,RESUME,ENABLE","PROCESSING,END,DISABLE","DISABLE,END,DISABLE","ROI2_DISABLE,END,DISABLE","PROCESSING,DELETE,DELETE","DISABLE,DELETE,DELETE","FROZEN,DELETE,DELETE","OFFLINE_TIME,DELETE,DELETE","OFFLINE_BUDGET,DELETE,DELETE"})
    void taskActionsMapPauseAndEndDifferentlyAndUseOnlyTheSelectedPhysicalPlan(String status,String action,String operation) {
        var pair=associatedPair(counterpart());taskWithStatus(status);
        var result=service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("一键起量",action,status));
        assertEquals("SUCCEEDED",result.status());assertEquals("1878743575303385",result.taskId());
        verify(port).controlStatus(credential,"1878743575303385",operation);verify(pair,never()).activeId(any(),any());verifyNoInteractions(query);
        verify(plans,never()).save(any());verify(audit).record(any());
    }
    @ParameterizedTest @CsvSource({"FROZEN,RESUME","FROZEN,PAUSE","FROZEN,END","OFFLINE_BUDGET,RESUME","OFFLINE_TIME,END","PROCESSING,RESUME","DISABLE,PAUSE","UNKNOWN,DELETE"})
    void taskTerminalAndUnknownStatesRejectInvalidWrites(String status,String action) {
        taskWithStatus(status);
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("一键起量",action,status)));
        verify(port,never()).controlStatus(any(),any(),any());verifyNoInteractions(audit);
    }
    @Test void staleOrForeignTaskAndWrongSceneCannotBeUpdated() {
        taskWithStatus("DISABLE");
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("一键起量","PAUSE","PROCESSING")));
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303386",new ControlTaskActionRequest("一键起量","DELETE","DISABLE")));
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("素材追投","DELETE","DISABLE")));
        assertThrows(BizException.class,()->service.controlAction(new TenantId("other"),id,"1878743575303385",new ControlTaskActionRequest("一键起量","DELETE","DISABLE")));
        verify(port,never()).controlStatus(any(),any(),any());verifyNoInteractions(audit);
    }
    @Test void resumingTaskCannotEnableAPlanPausedLocallyOrRemotely() {
        taskWithStatus("DISABLE");plan.apply(PlanStatus.PAUSED);
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("一键起量","RESUME","DISABLE")));
        plan.apply(PlanStatus.DELIVERING);when(port.detail(credential,"456")).thenReturn(json.createObjectNode().put("status","DISABLE"));
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("一键起量","RESUME","DISABLE")));
        when(port.detail(credential,"456")).thenReturn(json.createObjectNode());
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("一键起量","RESUME","DISABLE")));
        verify(port,never()).controlStatus(any(),any(),any());verifyNoInteractions(audit);
    }
    @Test void trialRestrictionAndPlatformRejectionDoNotRecordSuccessOrRepeatWrites() {
        taskWithStatus("PROCESSING");
        doThrow(new BizException(1601,"只读")).when(billing).assertWritable(any(),any(),any());
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("一键起量","PAUSE","PROCESSING")));
        verify(port,never()).controlStatus(any(),any(),any());reset(billing);
        doThrow(new BizException(1406,"结果未确认")).when(port).controlStatus(credential,"1878743575303385","PAUSE");
        assertThrows(BizException.class,()->service.controlAction(tenant,id,"1878743575303385",new ControlTaskActionRequest("一键起量","PAUSE","PROCESSING")));
        verify(port,times(1)).controlStatus(any(),any(),any());verifyNoInteractions(audit);
    }
    @Test void taskReportUsesPhysicalMemberAndSameRangeFallbackWithoutInventingTrend() {
        var pair=associatedPair(counterpart());plan.apply(PlanStatus.PAUSED);
        var day=LocalDate.now(ZoneId.of("Asia/Shanghai"));var row=json.createObjectNode().put("id","1878743575303385").put("task_status","DISABLE");row.withObject("/metrics").put("stat_cost_for_roi2_assist",0);
        when(port.controls(credential,"456","SMART_BOOST",day,day)).thenReturn(List.of(row));
        when(port.controlReport(credential,"456","1878743575303385","SMART_BOOST",day,day,"HOUR")).thenReturn(new AdManagementPort.InsightReport(json.createObjectNode(),List.of(),"stat_time_hour",Map.of("stat_cost_for_roi2_assist","stat_cost_for_roi2_assist","ctr_for_roi2_assist","ctr_for_roi2_assist"),null));
        var report=service.controlReport(tenant,id,"1878743575303385","一键起量",new PlanStatisticsRange(day,day),"HOUR");
        assertEquals(BigDecimal.ZERO,report.summary().get("stat_cost_for_roi2_assist"));assertNull(report.summary().get("ctr_for_roi2_assist"));assertTrue(report.trend().isEmpty());
        assertEquals("1878743575303385",report.task().path("id").asText());verify(pair,never()).activeId(any(),any());
        verify(port,never()).controlStatus(any(),any(),any());verifyNoInteractions(audit,billing);
    }
    @Test void taskReportRejectsWrongTaskOutOfRangeOrDuplicateTrendAndPreservesRealMetrics() throws Exception {
        var day=LocalDate.now(ZoneId.of("Asia/Shanghai"));var range=new PlanStatisticsRange(day,day);String taskId="1878743575303385";
        when(port.controls(credential,"456","SMART_BOOST",day,day)).thenReturn(List.of(json.createObjectNode().put("id",taskId).put("task_status","FROZEN")));
        var fields=Map.of("stat_cost_for_roi2_assist","stat_cost_for_roi2_assist","ctr_for_roi2_assist","ctr_for_roi2_assist");
        var summary=json.createObjectNode();summary.withObject("/dimensions").put("assist_aid",taskId);summary.withObject("/metrics").put("stat_cost_for_roi2_assist","5.25").put("ctr_for_roi2_assist","6.45");
        var point=summary.deepCopy();point.withObject("/dimensions").put("stat_time_hour",day+" 10:00:00");
        var returned=new AdManagementPort.InsightReport(summary,List.of(point),"stat_time_hour",fields,null);
        when(port.controlReport(credential,"456",taskId,"SMART_BOOST",day,day,"HOUR")).thenReturn(returned);
        var report=service.controlReport(tenant,id,taskId,"一键起量",range,"HOUR");assertEquals(new BigDecimal("5.25"),report.summary().get("stat_cost_for_roi2_assist"));assertEquals(new BigDecimal("6.45"),report.summary().get("ctr_for_roi2_assist"));assertEquals(1,report.trend().size());
        summary.withObject("/dimensions").put("assist_aid","different");assertThrows(BizException.class,()->service.controlReport(tenant,id,taskId,"一键起量",range,"HOUR"));summary.withObject("/dimensions").put("assist_aid",taskId);
        point.withObject("/dimensions").put("assist_aid","different");assertThrows(BizException.class,()->service.controlReport(tenant,id,taskId,"一键起量",range,"HOUR"));point.withObject("/dimensions").put("assist_aid",taskId);
        point.withObject("/dimensions").put("stat_time_hour",day.minusDays(1)+" 10:00:00");assertThrows(BizException.class,()->service.controlReport(tenant,id,taskId,"一键起量",range,"HOUR"));point.withObject("/dimensions").put("stat_time_hour",day+" 10:00:00");
        when(port.controlReport(credential,"456",taskId,"SMART_BOOST",day,day,"HOUR")).thenReturn(new AdManagementPort.InsightReport(summary,List.of(point,point),"stat_time_hour",fields,null));
        assertThrows(BizException.class,()->service.controlReport(tenant,id,taskId,"一键起量",range,"HOUR"));
        assertThrows(BizException.class,()->service.controlReport(tenant,id,taskId,"一键起量",new PlanStatisticsRange(day.minusDays(7),day),"HOUR"));
        verify(port,times(5)).controlReport(any(),any(),any(),any(),any(),any(),any());
    }
    private PlanTransferStore associatedPair(AdPlan other) {
        var transfers=mock(PlanTransferStore.class);
        var group=new PlanTransferStore.Group(other.id().value(),other.id().value(),other.id().value(),id.value(),null);
        when(transfers.group(tenant,id)).thenReturn(group);
        when(transfers.activeId(tenant,id)).thenReturn(other.id());
        when(plans.findById(tenant,other.id())).thenReturn(Optional.of(other));
        service.transferStore(transfers);
        return transfers;
    }
    private AdPlan counterpart() {
        return new AdPlan(new PlanId("volume"),tenant,account,plan.awemeId(),new PlanName("放量计划"),PlanStatus.DELIVERING,PlanSource.LINGTOU,
                new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(100),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),
                List.of(),new QcPlanId("789"),"DELIVERY_OK",null,0,null,List.of());
    }
    @Test void transferredDetailsMaterialsAndToolsReadOnlyTheSelectedMember() {
        plan.apply(PlanStatus.PAUSED);var transfers=associatedPair(counterpart());
        service.detail(tenant,id);service.materials(tenant,id);service.controls(tenant,id);
        verify(port).detail(credential,"456");verify(port).materials(credential,"456");verify(port).controls(credential,"456");
        verify(port,never()).detail(credential,"789");verify(port,never()).materials(credential,"789");verify(port,never()).controls(credential,"789");
        verify(transfers,never()).activeId(any(),any());
    }
    @Test void editingPausedTransferredMemberDoesNotRenameOrChangeItsCounterpart() {
        plan.apply(PlanStatus.PAUSED);var other=counterpart();var transfers=associatedPair(other);
        service.settings(tenant,id,new PlanSettingsRequest("控成本改名",new BigDecimal("350"),null));
        verify(port).settings(credential,"456","控成本改名",new BigDecimal("350"),null);
        assertEquals("控成本改名",plan.name().text());assertEquals(0,new BigDecimal("350").compareTo(plan.setting().dailyBudget().amount()));
        assertEquals("放量计划",other.name().text());assertEquals(0,new BigDecimal("100").compareTo(other.setting().dailyBudget().amount()));
        verify(plans,never()).save(other);verify(transfers).assertIdle(tenant,id);verify(transfers,never()).activeId(any(),any());
    }
    @Test void platformRejectionDoesNotMutateLocalPlanOrWriteSuccessLog() {
        doThrow(new BizException(1406,"平台拒绝")).when(port).settings(credential,"456",null,new BigDecimal("350"),null);
        assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,new BigDecimal("350"),null)));
        assertEquals(0,plan.setting().dailyBudget().amount().compareTo(new BigDecimal("300")));
        verify(plans,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void pausedBudgetChangeConfirmsFreshPlatformValueAndKeepsCounterpartAndModeUntouched() throws Exception {
        plan.apply(PlanStatus.PAUSED);var other=counterpart();associatedPair(other);
        when(port.detail(credential,"456")).thenReturn(
                json.readTree("{\"status\":\"DISABLE\",\"delivery_setting\":{\"budget\":300,\"roi2_goal\":2}}"),
                json.readTree("{\"status\":\"DISABLE\",\"delivery_setting\":{\"budget\":400,\"roi2_goal\":2}}"));
        service.settings(tenant,id,new PlanSettingsRequest(null,new BigDecimal("400"),null));
        verify(port).settings(credential,"456",null,new BigDecimal("400"),null);verify(port,times(2)).detail(credential,"456");
        assertEquals(0,new BigDecimal("400").compareTo(plan.setting().dailyBudget().amount()));assertEquals(SmartBidType.CUSTOM,plan.setting().bidType());
        assertEquals(new BigDecimal("2"),plan.setting().roi2Goal().orElseThrow().value());assertEquals(PlanStatus.PAUSED,plan.status());
        assertEquals(0,new BigDecimal("100").compareTo(other.setting().dailyBudget().amount()));verify(plans,never()).save(other);
        var command=ArgumentCaptor.forClass(SuccessAuditCommand.class);verify(audit).record(command.capture());
        JsonNode extra=json.readTree(command.getValue().extra());assertTrue(extra.isObject());
        assertEquals("修改前：原计划 / 300.0000 / 2",extra.path("detail").asText());assertEquals(id.value(),command.getValue().objectId());
    }
    @Test void volumeBudgetUsesTheSameFlowWithoutAddingAnRoiTarget() throws Exception {
        plan.updateSetting(new DeliverySetting(SmartBidType.CONSERVATIVE,plan.setting().dailyBudget(),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()));
        when(port.detail(credential,"456")).thenReturn(
                json.readTree("{\"status\":\"DELIVERY_OK\",\"delivery_setting\":{\"budget\":300}}"),
                json.readTree("{\"status\":\"DELIVERY_OK\",\"delivery_setting\":{\"budget\":400}}"));
        service.settings(tenant,id,new PlanSettingsRequest(null,new BigDecimal("400"),null));
        verify(port).settings(credential,"456",null,new BigDecimal("400"),null);assertTrue(plan.setting().roi2Goal().isEmpty());
        assertEquals(0,new BigDecimal("400").compareTo(plan.setting().dailyBudget().amount()));
    }
    @ParameterizedTest
    @CsvSource({"CONSERVATIVE,50", "CONSERVATIVE,50.01", "CONSERVATIVE,100", "CONSERVATIVE,299.99", "CONSERVATIVE,999999999.99", "CUSTOM,300", "CUSTOM,300.01", "CUSTOM,999999999.99"})
    void budgetBoundsFollowTheSelectedPlansModeAndIncludeTheMinimum(SmartBidType mode,BigDecimal amount) throws Exception {
        plan.apply(PlanStatus.PAUSED);
        plan.updateSetting(new DeliverySetting(mode,plan.setting().dailyBudget(),mode==SmartBidType.CUSTOM?plan.setting().roi2Goal():Optional.empty(),Schedule.fromNow(),AudienceJson.empty()));
        var other=counterpart();var transfers=associatedPair(other);
        var current=json.createObjectNode().put("status","DISABLE");
        current.putObject("delivery_setting").put("smart_bid_type",mode.oceanValue()).put("budget",300);
        var updated=current.deepCopy();updated.withObject("/delivery_setting").put("budget",amount);
        if(mode==SmartBidType.CUSTOM){current.withObject("/delivery_setting").put("roi2_goal",2);updated.withObject("/delivery_setting").put("roi2_goal",2);}
        when(port.detail(credential,"456")).thenReturn(current,updated);
        service.settings(tenant,id,new PlanSettingsRequest(null,amount,null));
        verify(port).settings(credential,"456",null,amount,null);
        assertEquals(0,amount.compareTo(plan.setting().dailyBudget().amount()));assertEquals(mode,plan.setting().bidType());assertEquals(PlanStatus.PAUSED,plan.status());
        assertEquals(0,new BigDecimal("100").compareTo(other.setting().dailyBudget().amount()));verify(plans,never()).save(other);verify(transfers,never()).activeId(any(),any());
    }
    @ParameterizedTest
    @CsvSource({"CONSERVATIVE,0,50", "CONSERVATIVE,49.99,50", "CONSERVATIVE,50.001,50", "CONSERVATIVE,1000000000,50", "CUSTOM,50,300", "CUSTOM,100,300", "CUSTOM,299.99,300", "CUSTOM,300.001,300", "CUSTOM,1000000000,300"})
    void budgetOutsideModeBoundsIsRejectedBeforeCallingThePlatform(SmartBidType mode,BigDecimal amount,String minimum) {
        plan.updateSetting(new DeliverySetting(mode,plan.setting().dailyBudget(),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()));
        associatedPair(counterpart());
        BizException error=assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,amount,null)));
        assertTrue(error.getMessage().contains("日预算范围为 "+minimum+"–"));
        verifyNoInteractions(port,credentials,audit);verify(plans,never()).save(any());
    }
    @Test void changedPlatformModeStopsBudgetUpdateUntilThePlanIsSynced() throws Exception {
        plan.updateSetting(new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(100),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()));
        when(port.detail(credential,"456")).thenReturn(json.readTree("{\"status\":\"DISABLE\",\"delivery_setting\":{\"smart_bid_type\":\"SMART_BID_CUSTOM\",\"budget\":300}}"));
        BizException error=assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,new BigDecimal("50"),null)));
        assertTrue(error.getMessage().contains("平台投放方式已变化"));verify(port,never()).settings(any(),any(),any(),any(),any());
        verify(plans,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void acknowledgedBudgetWithoutFreshConfirmationDoesNotReportLocalSuccessOrRetry() throws Exception {
        when(port.detail(credential,"456")).thenReturn(json.readTree("{\"status\":\"DISABLE\",\"delivery_setting\":{\"budget\":300}}"));
        BizException error=assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,new BigDecimal("400"),null)));
        assertTrue(error.getMessage().contains("勿重复提交"));verify(port).settings(credential,"456",null,new BigDecimal("400"),null);
        assertEquals(0,new BigDecimal("300").compareTo(plan.setting().dailyBudget().amount()));verify(plans,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void pausedRoiChangeConfirmsFreshPlatformValueAndDoesNotTouchItsCounterpart() throws Exception {
        plan.apply(PlanStatus.PAUSED);var other=counterpart();associatedPair(other);
        when(port.detail(credential,"456")).thenReturn(
                json.readTree("{\"status\":\"DISABLE\",\"delivery_setting\":{\"smart_bid_type\":\"SMART_BID_CUSTOM\",\"budget\":400,\"roi2_goal\":2,\"deep_external_action\":\"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI\"}}"),
                json.readTree("{\"status\":\"DISABLE\",\"delivery_setting\":{\"budget\":400,\"roi2_goal\":1.6}}"));
        service.settings(tenant,id,new PlanSettingsRequest(null,null,new BigDecimal("1.6")));
        verify(port).settings(credential,"456",null,null,new BigDecimal("1.6"),"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI");verify(port,times(2)).detail(credential,"456");
        assertEquals(new BigDecimal("1.6"),plan.setting().roi2Goal().orElseThrow().value());assertEquals(0,new BigDecimal("400").compareTo(plan.setting().dailyBudget().amount()));
        assertEquals(PlanStatus.PAUSED,plan.status());assertEquals(SmartBidType.CUSTOM,plan.setting().bidType());verify(plans,never()).save(other);
        assertEquals(0,new BigDecimal("100").compareTo(other.setting().dailyBudget().amount()));verify(audit).record(any());
    }
    @Test void platformRoiRejectionDoesNotPersistRequestedValueOrWriteSuccessAudit() throws Exception {
        when(port.detail(credential,"456")).thenReturn(json.readTree("{\"delivery_setting\":{\"deep_external_action\":\"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI\"}}"));
        doThrow(new BizException(1406,"千川ROI修改失败：ROI不可修改")).when(port).settings(credential,"456",null,null,new BigDecimal("1.6"),"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI");
        BizException error=assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,null,new BigDecimal("1.6"))));
        assertTrue(error.getMessage().contains("ROI不可修改"));assertEquals(new BigDecimal("2"),plan.setting().roi2Goal().orElseThrow().value());verify(plans,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void acknowledgedRoiWithoutFreshConfirmationDoesNotReportLocalSuccessOrRetry() throws Exception {
        when(port.detail(credential,"456")).thenReturn(json.readTree("{\"status\":\"DISABLE\",\"delivery_setting\":{\"budget\":400,\"roi2_goal\":2}}"));
        BizException error=assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,null,new BigDecimal("1.6"))));
        assertTrue(error.getMessage().contains("最新ROI尚未确认"));verify(port).settings(credential,"456",null,null,new BigDecimal("1.6"),null);
        assertEquals(new BigDecimal("2"),plan.setting().roi2Goal().orElseThrow().value());verify(plans,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void readbackFailureAfterRoiAcknowledgementDoesNotBlindlyRepeatTheWrite() {
        when(port.detail(credential,"456")).thenReturn(json.createObjectNode()).thenThrow(new BizException(1406,"读取超时"));
        BizException error=assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,null,new BigDecimal("1.6"))));
        assertTrue(error.getMessage().contains("千川已接受ROI修改"));verify(port).settings(credential,"456",null,null,new BigDecimal("1.6"),null);verify(plans,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void combinedBudgetAndRoiUpdateMustConfirmBothValues() throws Exception {
        when(port.detail(credential,"456")).thenReturn(
                json.readTree("{\"delivery_setting\":{\"budget\":300,\"roi2_goal\":2}}"),
                json.readTree("{\"delivery_setting\":{\"budget\":300,\"roi2_goal\":1.6}}"));
        BizException error=assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,new BigDecimal("400"),new BigDecimal("1.6"))));
        assertTrue(error.getMessage().contains("预算与ROI尚未确认"));verify(port).settings(credential,"456",null,new BigDecimal("400"),new BigDecimal("1.6"),null);
        verify(plans,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void readbackFailureAfterAcknowledgementExplainsThatBudgetMayAlreadyHaveChanged() {
        when(port.detail(credential,"456")).thenReturn(json.createObjectNode()).thenThrow(new BizException(1406,"读取超时"));
        BizException error=assertThrows(BizException.class,()->service.settings(tenant,id,new PlanSettingsRequest(null,new BigDecimal("400"),null)));
        assertTrue(error.getMessage().contains("千川已接受预算修改"));verify(plans,never()).save(any());verifyNoInteractions(audit);
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
    @Test void materialSourceMissingRequiresExactVideoUploadInTheSelectedTenantAndAccount() throws Exception {
        var assets=mock(com.lynx.ad.modules.creative.application.AssetUploadService.class);service.assetUploadService(assets);
        var row=json.readTree("{\"audit_status\":\"PASS\",\"material_status\":\"DELIVERY_OK\",\"material_select_type\":\"CUSTOM\",\"material_info\":{\"video_material\":{\"material_id\":7694973799313178650,\"video_id\":\"native-video\",\"cover_image\":{\"image_url\":\"https://example.test/cover.jpg\"}}}}");
        when(port.materials(credential,"456")).thenReturn(List.of(row));
        when(assets.ownsUploadedVideo(tenant,account.value(),"native-video")).thenReturn(true);
        var value=service.materials(tenant,id).getFirst();assertTrue(value.eligible());assertEquals("7694973799313178650",value.id());assertEquals("https://example.test/cover.jpg",value.coverUrl());
        verify(assets).ownsUploadedVideo(tenant,account.value(),"native-video");
        when(assets.ownsUploadedVideo(tenant,account.value(),"native-video")).thenReturn(false);assertFalse(service.materials(tenant,id).getFirst().eligible());
        ((com.fasterxml.jackson.databind.node.ObjectNode)row).put("material_select_type","AUTO");assertFalse(service.materials(tenant,id).getFirst().eligible());
    }
    @Test void taskQueryMapsTheSelectedToolToItsOwnSceneAndPhysicalPlan() {
        service.controls(tenant,id,"一键起量");verify(port).controls(credential,"456","SMART_BOOST");
        service.controls(tenant,id,"素材追投");verify(port).controls(credential,"456","MATERIAL_ADD_BUDGET");
        assertThrows(BizException.class,()->service.controls(tenant,id,"搜索抢首屏"));
        assertThrows(BizException.class,()->service.controls(new TenantId("other"),id,"一键起量"));
    }
    @Test void taskStatisticsForPausedTransferredMemberUsesItsOwnIdAndSelectedRange() {
        plan.apply(PlanStatus.PAUSED);var transfers=associatedPair(counterpart());
        var end=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));var start=end.minusDays(6);var range=new PlanStatisticsRange(start,end);
        service.controls(tenant,id,"一键起量",range);verify(port).controls(credential,"456","SMART_BOOST",start,end);
        service.controls(tenant,id,"素材追投",range);verify(port).controls(credential,"456","MATERIAL_ADD_BUDGET",start,end);
        verify(transfers,never()).activeId(any(),any());verify(port,never()).controls(any(),eq("789"),anyString(),any(),any());
        assertThrows(BizException.class,()->service.controls(tenant,id,null,range));
        assertThrows(BizException.class,()->service.controls(tenant,id,"搜索抢首屏",range));
        assertThrows(BizException.class,()->service.controls(new TenantId("other"),id,"一键起量",range));
    }
    @Test void exactlyOneHundredYuanIsValidAndLessThanOneHundredIsRejected() {
        when(port.control(eq(credential),eq("456"),eq("SMART_BOOST"),any(),eq(new BigDecimal("100")),any(),any())).thenReturn("task-123");
        var result=service.control(tenant,id,new ControlTaskRequest("一键起量","起量任务",new BigDecimal("100"),new BigDecimal("0.5"),null,List.of(),"request-100"));
        assertEquals("task-123",result.taskId());
        assertThrows(BizException.class,()->service.control(tenant,id,new ControlTaskRequest("一键起量","起量任务",new BigDecimal("99.99"),new BigDecimal("0.5"),null,List.of(),"request-099")));
        verify(port,times(1)).control(any(),any(),any(),any(),any(),any(),any());
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
    @Test void liveSyncSkipsDemoAndOtherPlatformsAndUsesTenantScopedCredentials() {
        var accounts=mock(OauthAccountRepository.class);var list=mock(AdListPort.class);var tx=mock(PlatformTransactionManager.class);
        var real=oauth(account).withAuthorizationBinding("grant-real","app-real",null);
        var demo=oauth(new AccountId("demo"));
        var marketing=new OauthAccount(new AccountId("marketing"),tenant,Platform.OCEANENGINE,new AdvertiserId("999"),"营销账户",null,null,null,null,"ACTIVE",List.of());
        when(accounts.findByTenant(tenant)).thenReturn(List.of(demo,marketing,real));when(list.list(credential,null)).thenReturn(List.of());
        var sync=new PlanSyncService(plans,accounts,credentials,list,port,store,tx);
        var properties=new com.lynx.ad.common.config.LynxAdProperties();properties.getOauth().getQianchuan().setMode("live");sync.configuration(properties);
        var result=sync.sync(tenant,null);
        assertEquals(1,result.accounts().size());assertEquals(account.value(),result.accounts().getFirst().accountId());
        verify(credentials).getUsableCredential(tenant,account);verify(credentials,never()).get(any());
        verify(list,times(1)).list(any(),any());verifyNoInteractions(port);
    }
    @Test void liveListHidesDemoPlansButRetainsRealAuthorizationHistory() {
        var accounts=mock(OauthAccountRepository.class);
        var realAccount=new AccountId("real");
        var real=oauth(realAccount).withAuthorizationBinding("grant-real","app-real",null);
        var realPlan=new AdPlan(new PlanId("real-plan"),tenant,realAccount,plan.awemeId(),new PlanName("真实计划"),PlanStatus.PAUSED,PlanSource.EXTERNAL,
                plan.setting(),List.of(),new QcPlanId("789"),"DISABLE",null,0,null,List.of());
        when(accounts.findByTenant(tenant)).thenReturn(List.of(oauth(account),real));when(plans.findByTenant(tenant)).thenReturn(List.of(plan,realPlan));
        var query=new PlanQueryService(plans,mock(AdListPort.class),accounts,credentials,
                mock(com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository.class),mock(com.lynx.ad.modules.catalog.domain.port.ProductRepository.class),
                mock(com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore.class),store);
        var properties=new com.lynx.ad.common.config.LynxAdProperties();properties.getOauth().getQianchuan().setMode("live");properties.getMock().setQianchuan(false);query.configuration(properties);
        var result=query.list(tenant,1,10);assertEquals(1,result.total());assertEquals("真实计划",result.records().getFirst().name());
        assertNull(result.records().getFirst().costYuan());assertThrows(BizException.class,()->query.get(tenant,id));
        real.unbind();assertEquals(1,query.list(tenant,1,10).total());assertFalse(query.list(tenant,1,10).records().getFirst().canEdit());
    }
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
        var stale=query.list(tenant,1,10);var historical=stale.records().stream().filter(p->p.planId().equals("plan-b")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("300"),historical.costYuan());assertTrue(historical.historicalMetrics());assertEquals(today.minusDays(1),historical.statisticsDate());assertNull(historical.comparison());
        assertEquals(2,stale.summary().covered());verifyNoInteractions(credentials);
    }
}
