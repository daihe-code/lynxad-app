package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanInsightsStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class PlanInsightsTest {
    final ObjectMapper json=new ObjectMapper();final TenantId tenant=new TenantId("tenant-a");final PlanId id=new PlanId("plan-a");final AccountId account=new AccountId("account-a");
    final AdPlanRepository plans=mock(AdPlanRepository.class);final AdPlan plan=mock(AdPlan.class);final OauthAccountRepository accounts=mock(OauthAccountRepository.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);final AdManagementPort port=mock(AdManagementPort.class);
    final PlanStatisticsService fallback=mock(PlanStatisticsService.class);final PlanInsightsStore store=mock(PlanInsightsStore.class);
    final PlanInsightsService service=new PlanInsightsService(plans,accounts,credentials,port,fallback,store);
    final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));final PlanStatisticsRange range=new PlanStatisticsRange(today,today);
    final Credential credential=new Credential("test-token",new AdvertiserId("123"));
    final String qcId="9007199254740993";
    final Map<String,String> fields=Map.of("cost","stat_cost","gmv","gmv","orders","orders","roi","roi");
    @BeforeEach void setup() {
        when(plans.findById(tenant,id)).thenReturn(Optional.of(plan));when(plan.status()).thenReturn(PlanStatus.PAUSED);when(plan.accountId()).thenReturn(account);
        when(plan.qcPlanId()).thenReturn(Optional.of(new QcPlanId(qcId)));
        when(accounts.findById(tenant,account)).thenReturn(Optional.of(new OauthAccount(account,tenant,Platform.QIANCHUAN,new AdvertiserId("123"),"账户",null,null,Instant.now().plusSeconds(3600),null,"ACTIVE",List.of())));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);
    }
    JsonNode row(String id,String day) {
        var row=json.createObjectNode();row.putObject("dimensions").putObject("ad_id").put("Value",id);
        if(day!=null)row.withObject("/dimensions").putObject("stat_time_day").put("value",day);
        row.putObject("metrics").put("stat_cost",0).put("orders",0);return row;
    }
    void report(JsonNode summary,List<JsonNode> trend) { when(port.insightReport(credential,qcId,today,today)).thenReturn(new AdManagementPort.InsightReport(summary,trend,"stat_time_day",fields,null)); }
    @Test void pausedPlanHasActualZeroAndMissingMetricsStayUnknown() {
        report(row(qcId,null),List.of(row(qcId,today.toString())));var data=service.data(tenant,id,range,false);
        assertEquals(BigDecimal.ZERO,data.summary().get("cost"));assertNull(data.summary().get("gmv"));assertEquals(1,data.trend().size());assertNull(data.summary().get("refundRate"));
    }
    @Test void reportCacheStillChecksOwnershipAndRefreshFetchesAgain() {
        report(row(qcId,null),List.of());assertSame(service.data(tenant,id,range,false),service.data(tenant,id,range,false));service.data(tenant,id,range,true);
        verify(port,times(2)).insightReport(credential,qcId,today,today);
        assertThrows(BizException.class,()->service.data(new TenantId("other"),id,range,false));
    }
    @Test void exposureAndClickCountsUseActualReportFieldsAndPreserveMissingValues() {
        var fieldsWithTraffic=new HashMap<>(fields);fieldsWithTraffic.put("shows","show_cnt_for_roi2");fieldsWithTraffic.put("clicks","click_cnt_for_roi2");
        var summary=row(qcId,null);summary.withObject("/metrics").put("show_cnt_for_roi2",48320).put("click_cnt_for_roi2",0);
        when(port.insightReport(credential,qcId,today,today)).thenReturn(new AdManagementPort.InsightReport(summary,List.of(),"stat_time_day",fieldsWithTraffic,null));
        var result=service.data(tenant,id,range,false);assertEquals(new BigDecimal("48320"),result.summary().get("shows"));assertEquals(BigDecimal.ZERO,result.summary().get("clicks"));
        summary.withObject("/metrics").remove("click_cnt_for_roi2");assertNull(service.data(tenant,id,range,true).summary().get("clicks"));
    }
    @Test void mismatchedSummaryCannotDisplayAnotherPlansMetrics() { report(row("456",null),List.of());assertThrows(BizException.class,()->service.data(tenant,id,range,false));verifyNoInteractions(fallback); }
    @Test void mismatchedTrendCannotDisplayAnotherPlansMetrics() { report(row(qcId,null),List.of(row("456",today.toString())));assertThrows(BizException.class,()->service.data(tenant,id,range,false)); }
    @Test void duplicateTimeAndOutOfRangeAreRejected() {
        report(row(qcId,null),List.of(row(qcId,today.toString()),row(qcId,today.toString())));assertThrows(BizException.class,()->service.data(tenant,id,range,false));
        report(row(qcId,null),List.of(row(qcId,today.minusDays(1).toString())));assertThrows(BizException.class,()->service.data(tenant,id,range,false));
    }
    @Test void nativeHourOnlyDimensionIsNormalizedUsingTheSelectedSingleDay() {
        var hour=row(qcId,null);hour.withObject("/dimensions").putObject("stat_time_hour").put("Value","18");
        when(port.insightReport(credential,qcId,today,today)).thenReturn(new AdManagementPort.InsightReport(row(qcId,null),List.of(hour),"stat_time_hour",fields,null));
        assertEquals(today+" 18:00:00",service.data(tenant,id,range,false).trend().getFirst().time());
    }
    @Test void hourOnlyDimensionIsInvalidForAMultiDayRange() {
        var multi=new PlanStatisticsRange(today.minusDays(1),today);var hour=row(qcId,null);hour.withObject("/dimensions").put("stat_time_hour","18");
        when(port.insightReport(credential,qcId,multi.startDate(),today)).thenReturn(new AdManagementPort.InsightReport(row(qcId,null),List.of(hour),"stat_time_hour",fields,null));
        assertThrows(BizException.class,()->service.data(tenant,id,multi,false));
    }
    @Test void liveUnixMillisecondsAreConvertedInShanghaiTimezone() {
        var hour=row(qcId,null);long timestamp=today.atTime(19,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();hour.withObject("/dimensions").putObject("stat_time_hour").put("Value",timestamp);
        when(port.insightReport(credential,qcId,today,today)).thenReturn(new AdManagementPort.InsightReport(row(qcId,null),List.of(hour),"stat_time_hour",fields,null));
        assertEquals(today+" 19:00:00",service.data(tenant,id,range,false).trend().getFirst().time());
    }
    @Test void liveUnixSecondsDayDimensionIsConvertedAndRangeChecked() {
        var day=row(qcId,null);long timestamp=today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond();day.withObject("/dimensions").putObject("stat_time_day").put("Value",timestamp);
        report(row(qcId,null),List.of(day));assertEquals(today.toString(),service.data(tenant,id,range,false).trend().getFirst().time());
        day.withObject("/dimensions").withObject("/stat_time_day").put("Value",timestamp-86400);report(row(qcId,null),List.of(day));assertThrows(BizException.class,()->service.data(tenant,id,range,true));
    }
    @Test void unsupportedExpandedReportUsesRealSelectedPlanAggregate() {
        when(port.insightReport(credential,qcId,today,today)).thenThrow(new BizException(1406,"权限不足"));
        when(fallback.report(tenant,account.value(),range,false)).thenReturn(new PlanStatisticsService.Snapshot(Map.of(qcId,new PlanStatisticsService.Metrics(new BigDecimal("10.46"),BigDecimal.ZERO,BigDecimal.ZERO,0L)),Instant.now()));
        var data=service.data(tenant,id,range,false);assertEquals(new BigDecimal("10.46"),data.summary().get("cost"));assertTrue(data.trend().isEmpty());assertTrue(data.warning().contains("权限不足"));
    }
    @Test void deletedPlanCannotReadAnyModule() { when(plan.status()).thenReturn(PlanStatus.DELETED);assertThrows(BizException.class,()->service.localLogs(tenant,id,range,1));verifyNoInteractions(port,store,credentials); }
    @Test void draftCannotQueryPlatformReports() { when(plan.qcPlanId()).thenReturn(Optional.empty());assertThrows(BizException.class,()->service.data(tenant,id,range,false));verifyNoInteractions(port); }
    @Test void localLogsUseExactMemberIdAndTenant() { service.localLogs(tenant,id,range,2);verify(store).logs(tenant,id.value(),range,2);verifyNoInteractions(port); }
    @Test void nativeLogsPreserveLargeIdAndHideIp() throws Exception {
        when(port.operationLogs(credential,qcId,today,today,1,20)).thenReturn(json.readTree("{\"logs\":[{\"object_id\":9007199254740993,\"log_id\":\"L1\",\"operator_name\":\"店主\",\"content_title\":\"调整预算\",\"content_log\":[\"100 → 200\"],\"opt_ip\":\"private-ip\"}],\"page_info\":{\"total_number\":21}}"));
        var logs=service.platformLogs(tenant,id,range,1);assertEquals(21,logs.total());assertEquals(List.of("100 → 200"),logs.records().getFirst().details());assertFalse(json.writeValueAsString(logs).contains("private-ip"));
    }
    @Test void nativeLogWrongIdCannotLeakAnotherPlansLogs() throws Exception {
        when(port.operationLogs(credential,qcId,today,today,1,20)).thenReturn(json.readTree("{\"logs\":[{\"object_id\":456}],\"page_info\":{\"total_number\":1}}"));assertThrows(BizException.class,()->service.platformLogs(tenant,id,range,1));
    }
    @Test void guaranteeFailureRetainsObservedHistoryWithoutFabricatingCurrentStatus() {
        when(port.guaranteeDetail(credential,qcId)).thenThrow(new BizException(1406,"未开通保障"));when(store.guarantees(tenant,id.value(),range,1)).thenReturn(new PlanInsightsStore.Page<>(List.of(),0,1,20));
        var value=service.guarantee(tenant,id,range,1);assertNull(value.status());assertEquals("未开通保障",value.warning());verify(store,never()).observeGuarantee(any(),any(),any(),any(),any());
    }
    @Test void guaranteeOnlyRecordsAnActuallyReturnedStatus() {
        when(port.guaranteeDetail(credential,qcId)).thenReturn(json.createObjectNode().put("ad_id",qcId).put("compensate_status","IN_EFFECT").put("reason","平台说明"));
        service.guarantee(tenant,id,range,1);verify(store).observeGuarantee(tenant,id.value(),"IN_EFFECT","平台说明",null);
    }
    @Test void invalidPaginationStopsBeforePlatformCall() { assertThrows(BizException.class,()->service.platformLogs(tenant,id,range,0));assertThrows(BizException.class,()->service.guarantee(tenant,id,range,10001));verifyNoInteractions(port,store); }
    @Test void dailySpendingPreservesZeroAndDoesNotInventMissingDaysOrEffectiveStatus() {
        var multi=new PlanStatisticsRange(today.minusDays(2),today);
        var paid=row(qcId,today.minusDays(2).toString());paid.withObject("/metrics").put("stat_cost",new BigDecimal("3.56"));
        var summary=row(qcId,null);summary.withObject("/metrics").put("stat_cost",new BigDecimal("3.56"));
        when(port.insightReport(credential,qcId,multi.startDate(),today,true)).thenReturn(new AdManagementPort.InsightReport(summary,List.of(paid,row(qcId,today.toString())),"stat_time_day",fields,null));
        var result=service.dailySpending(tenant,id,multi);
        assertEquals(new BigDecimal("3.56"),result.totalCost());assertEquals(3,result.days().size());
        assertEquals(new BigDecimal("3.56"),result.days().getFirst().cost());assertNull(result.days().get(1).cost());assertEquals(BigDecimal.ZERO,result.days().getLast().cost());
        verifyNoInteractions(store,fallback);
    }
    @Test void dailySpendingRejectsDuplicateDatesWrongPlansAndOutOfRange() {
        var day=row(qcId,today.toString());
        when(port.insightReport(credential,qcId,today,today,true)).thenReturn(new AdManagementPort.InsightReport(row(qcId,null),List.of(day,day),"stat_time_day",fields,null));
        assertThrows(BizException.class,()->service.dailySpending(tenant,id,range));
        when(port.insightReport(credential,qcId,today,today,true)).thenReturn(new AdManagementPort.InsightReport(row(qcId,null),List.of(row("other",today.toString())),"stat_time_day",fields,null));
        assertThrows(BizException.class,()->service.dailySpending(tenant,id,range));
        when(port.insightReport(credential,qcId,today,today,true)).thenReturn(new AdManagementPort.InsightReport(row(qcId,null),List.of(row(qcId,today.minusDays(1).toString())),"stat_time_day",fields,null));
        assertThrows(BizException.class,()->service.dailySpending(tenant,id,range));
    }
    @Test void dailySpendingDoesNotDisplayAggregateAsDayDataWhenDailyDimensionIsUnavailable() {
        when(port.insightReport(credential,qcId,today,today,true)).thenReturn(new AdManagementPort.InsightReport(row(qcId,null),List.of(),null,fields,null));
        var result=service.dailySpending(tenant,id,range);assertNull(result.days().getFirst().cost());assertTrue(result.warning().contains("未提供日维度"));
        when(plans.findById(tenant,id)).thenReturn(Optional.empty());assertThrows(BizException.class,()->service.dailySpending(tenant,id,range));
    }
}
