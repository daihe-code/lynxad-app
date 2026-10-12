package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.api.dto.PlanListVo;
import com.lynx.ad.modules.delivery.application.PlanQueryService;
import com.lynx.ad.modules.delivery.application.PlanStatisticsService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class PlanListDisplayTest {
    final TenantId tenant=new TenantId("tenant-a");
    final PlanId id=new PlanId("plan-a");
    final ObjectMapper json=new ObjectMapper();
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    final CreationStore creation=mock(CreationStore.class);
    final PlanRemoteStore remote=mock(PlanRemoteStore.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    final OauthAccountRepository accounts=mock(OauthAccountRepository.class);
    final PlanTransferStore transfers=mock(PlanTransferStore.class);
    final PlanQueryService query=new PlanQueryService(plans,mock(AdListPort.class),accounts,credentials,
            mock(com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository.class),
            mock(com.lynx.ad.modules.catalog.domain.port.ProductRepository.class),mock(AdPlanCreativeBindingStore.class),remote);
    AdPlan plan;
    @BeforeEach void setup() throws Exception {
        plan=new AdPlan(id,tenant,new AccountId("account-a"),new AwemeId("aweme-a"),new PlanName("真实放量计划"),PlanStatus.DELIVERING,PlanSource.LINGTOU,
                new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(new BigDecimal("100")),Optional.empty(),
                    new Schedule(ScheduleType.DATE_RANGE,LocalDate.of(2026,10,10),LocalDate.of(2036,10,10)),AudienceJson.empty()),
                List.of(),new QcPlanId("123"),"DELIVERY_OK",null,0,null,List.of());
        when(plans.findByTenant(tenant)).thenReturn(List.of(plan));query.creationStore(creation);
        when(accounts.findByTenant(tenant)).thenReturn(List.of(new com.lynx.ad.modules.identity.domain.model.OauthAccount(
                plan.accountId(),tenant,com.lynx.ad.modules.identity.domain.model.Platform.QIANCHUAN,new AdvertiserId("999"),
                "授权账户",null,null,Instant.now().plusSeconds(3600),null,"ACTIVE",List.of())));
        when(creation.summariesByPlan(tenant.value(),List.of(id.value()))).thenReturn(Map.of(id.value(),new CreationStore.PlanSummary(List.of(),json.readTree("""
            {"budget":{"estimate_trans_cost_list":[
              {"budget":50,"estimate_roi":0.33,"min_estimate_roi":0.26,"max_estimate_roi":0.4},
              {"budget":100,"estimate_roi":0.24,"min_estimate_roi":0.19,"max_estimate_roi":0.29}]},
             "roi":{"ecp_roi2_goal":4.66}}
            """))));
    }
    PlanListVo row() {return query.list(tenant,1,20).records().getFirst();}
    @Test void selectedRangeReplacesHistoricalSnapshotAndSummaryUsesAllFilteredPlansBeforePagination() throws Exception {
        var other=transferred(PlanStatus.PAUSED);
        var statistics=mock(PlanStatisticsService.class);query.statisticsService(statistics);
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));var range=new PlanStatisticsRange(today.minusDays(6),today);
        cache(metrics("999"),null,today,null,null,null);
        when(statistics.report(tenant,plan.accountId().value(),range,false)).thenReturn(new PlanStatisticsService.Snapshot(Map.of(
                "123",new PlanStatisticsService.Metrics(new BigDecimal("10"),new BigDecimal("100"),new BigDecimal("10"),2L),
                "456",new PlanStatisticsService.Metrics(new BigDecimal("30"),new BigDecimal("60"),new BigDecimal("2"),1L)),Instant.now()));
        var filter=new PlanQueryService.Filters(null,null,null,null,null,null,"costYuan",true,range,false);
        var page=query.list(tenant,1,1,filter);assertEquals(2,page.total());assertEquals(1,page.records().size());
        assertEquals(other.id().value(),page.records().getFirst().planId());assertEquals(new BigDecimal("30"),page.records().getFirst().costYuan());
        assertEquals(new BigDecimal("40"),page.summary().cost());assertEquals(new BigDecimal("160"),page.summary().gmv());assertEquals(3L,page.summary().orders());
        assertEquals(new BigDecimal("4.0000"),page.summary().roi());assertEquals(range,page.statisticsRange());assertNull(page.records().getFirst().comparison());
        verify(statistics,times(1)).report(tenant,plan.accountId().value(),range,false);
    }
    @Test void noDataInSelectedRangeDoesNotUsePausedPlansLatestHistoricalData() throws Exception {
        plan.apply(PlanStatus.PAUSED);var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));cache(null,metrics("3.56"),today,null,null,null);
        ((com.fasterxml.jackson.databind.node.ObjectNode)remote.list(tenant).get(id.value()).payload()).putObject("delivery_setting").put("budget",100);
        var statistics=mock(PlanStatisticsService.class);query.statisticsService(statistics);var range=new PlanStatisticsRange(today,today);
        when(statistics.report(tenant,plan.accountId().value(),range,false)).thenReturn(new PlanStatisticsService.Snapshot(Map.of(),Instant.now()));
        var page=query.list(tenant,1,20,new PlanQueryService.Filters(null,null,null,null,null,null,null,true,range,false));
        assertNull(page.records().getFirst().costYuan());assertNull(page.summary().cost());assertEquals(0,page.summary().covered());
        assertFalse(page.records().getFirst().historicalMetrics());assertNull(page.records().getFirst().statisticsDate());
        assertEquals("PAUSED",page.records().getFirst().status());assertEquals(0,new BigDecimal("100").compareTo(page.records().getFirst().budget()));
    }
    @Test void volumePlanKeepsForecastSeparateFromFixedGoalAndActualRoi() {
        var row=row();assertNull(row.roi2Goal());assertNull(row.roi());
        assertEquals(new BigDecimal("0.24"),row.roiEstimate().value());
        assertEquals(new BigDecimal("0.19"),row.roiEstimate().min());assertEquals(new BigDecimal("0.29"),row.roiEstimate().max());
        verify(creation).summariesByPlan(tenant.value(),List.of(id.value()));verifyNoInteractions(credentials);
    }
    @Test void changedPlatformBudgetCannotReuseAnotherTiersForecast() throws Exception {
        var payload=json.readTree("{\"ad_info\":{\"budget\":600}}");
        when(remote.list(tenant)).thenReturn(Map.of(id.value(),new PlanRemoteStore.Remote(id.value(),payload,null,null,Instant.now(),null,null,null,null)));
        var row=row();assertEquals(new BigDecimal("600"),row.budget());assertNull(row.roiEstimate());assertNull(row.roi2Goal());
    }
    @Test void controlCostPlanUsesActualGoalWithoutDisplayingBudgetForecast() {
        plan.updateSetting(new DeliverySetting(SmartBidType.CUSTOM,plan.setting().dailyBudget(),Optional.of(new RoiGoal(new BigDecimal("5.12"))),plan.setting().schedule(),AudienceJson.empty()));
        var row=row();assertEquals(new BigDecimal("5.12"),row.roi2Goal());assertNull(row.roiEstimate());
    }
    @Test void platformForecastTakesPrecedenceAndAlsoWorksWithoutCreationRecord() throws Exception {
        var payload=json.readTree("{\"delivery_setting\":{\"budget\":100,\"estimate_roi_goal\":0.25,\"min_estimate_roi_goal\":0.20,\"max_estimate_roi_goal\":0.30}}");
        when(remote.list(tenant)).thenReturn(Map.of(id.value(),new PlanRemoteStore.Remote(id.value(),payload,null,null,Instant.now(),null,null,null,null)));
        assertEquals(new BigDecimal("0.25"),row().roiEstimate().value());
        when(creation.summariesByPlan(tenant.value(),List.of(id.value()))).thenReturn(Map.of());
        assertEquals(0,new BigDecimal("0.30").compareTo(row().roiEstimate().max()));assertNull(row().roi2Goal());
    }
    @Test void noStoredForecastDoesNotCreateOneFromRecommendedCostControlRoi() throws Exception {
        when(creation.summariesByPlan(tenant.value(),List.of(id.value()))).thenReturn(Map.of(id.value(),new CreationStore.PlanSummary(List.of(),json.readTree("{\"roi\":{\"ecp_roi2_goal\":4.66}}"))));
        assertNull(row().roiEstimate());
    }
    @Test void crossYearScheduleRetainsBothFullDates() {
        var row=row();assertEquals("2026-10-10",row.startDate().toString());assertEquals("2036-10-10",row.endDate().toString());
    }
    private AdPlan transferred(PlanStatus status) {
        plan.apply(PlanStatus.PAUSED);
        var current=new AdPlan(new PlanId("transferred"),tenant,plan.accountId(),plan.awemeId(),new PlanName("真实放量计划_转投"),status,PlanSource.EXTERNAL,
                new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(300),Optional.of(new RoiGoal(new BigDecimal("1.5"))),plan.setting().schedule(),AudienceJson.empty()),
                List.of(),new QcPlanId("456"),"DELIVERY_OK",null,0,null,List.of());
        when(plans.findByTenant(tenant)).thenReturn(List.of(plan,current));
        var group=new PlanTransferStore.Group(id.value(),current.id().value(),id.value(),current.id().value(),null);
        when(transfers.groups(tenant)).thenReturn(Map.of(id.value(),group,current.id().value(),group));query.transferStore(transfers);
        when(plans.findById(tenant,id)).thenReturn(Optional.of(plan));when(plans.findById(tenant,current.id())).thenReturn(Optional.of(current));
        return current;
    }
    @Test void nativeTransferShowsBothModesWithTheirOwnIdsNamesSourcesAndSettings() {
        var current=transferred(PlanStatus.DELIVERING);
        var page=query.list(tenant,1,20);assertEquals(2,page.total());assertEquals(2,page.summary().plans());
        var volume=page.records().stream().filter(p->p.planId().equals(id.value())).findFirst().orElseThrow();
        var cost=page.records().stream().filter(p->p.planId().equals(current.id().value())).findFirst().orElseThrow();
        assertEquals("123",volume.qcPlanId());assertEquals("PAUSED",volume.status());assertEquals("SMART_BID_CONSERVATIVE",volume.smartBidType());
        assertEquals(plan.name().text(),volume.name());assertEquals("LINGTOU",volume.source());assertTrue(volume.lingtou());
        assertEquals(0,new BigDecimal("100").compareTo(volume.budget()));assertNull(volume.roi2Goal());assertNotNull(volume.roiEstimate());
        assertEquals("456",cost.qcPlanId());assertEquals("DELIVERING",cost.status());assertEquals("SMART_BID_CUSTOM",cost.smartBidType());
        assertEquals(current.name().text(),cost.name());assertEquals("EXTERNAL",cost.source());assertFalse(cost.lingtou());
        assertEquals(0,new BigDecimal("300").compareTo(cost.budget()));assertEquals(new BigDecimal("1.5"),cost.roi2Goal());assertNull(cost.roiEstimate());
        assertTrue(volume.canEdit());assertTrue(cost.canEdit());assertTrue(volume.canTransfer());assertTrue(cost.canTransfer());
    }
    @Test void deletedRootIsHiddenButSurvivingModeKeepsSharedProductsAndOwnStatistics() {
        var cost=transferred(PlanStatus.PAUSED);plan.markDeleted();
        var page=query.list(tenant,1,20);
        assertEquals(1,page.total());assertEquals(1,page.summary().plans());assertEquals(cost.id().value(),page.records().getFirst().planId());
        assertThrows(com.lynx.ad.common.exception.BizException.class,()->query.get(tenant,id));
        verify(creation,atLeastOnce()).summariesByPlan(tenant.value(),List.of(id.value()));
    }
    @Test void bothPausedVariantsRemainVisibleAndDetailDoesNotRedirectToTheOtherMode() {
        var current=transferred(PlanStatus.PAUSED);
        assertEquals(2,query.list(tenant,1,20).total());
        assertEquals("123",query.get(tenant,id).qcPlanId());assertEquals(id.value(),query.get(tenant,id).planId());
        assertEquals("456",query.get(tenant,current.id()).qcPlanId());assertEquals(current.id().value(),query.get(tenant,current.id()).planId());
        verify(transfers,never()).activeId(any(),any());
    }
    @Test void filtersAndPaginationAddressEachVariantIndependently() {
        var current=transferred(PlanStatus.DELIVERING);
        var filter=new PlanQueryService.Filters(null,"PAUSED",null,null,null,null,null,true);
        var paused=query.list(tenant,1,20,filter);assertEquals(1,paused.total());assertEquals(id.value(),paused.records().getFirst().planId());
        filter=new PlanQueryService.Filters("456",null,null,"SMART_BID_CUSTOM",null,null,null,true);
        var cost=query.list(tenant,1,20,filter);assertEquals(1,cost.total());assertEquals(current.id().value(),cost.records().getFirst().planId());
        var first=query.list(tenant,1,1);var second=query.list(tenant,2,1);
        assertEquals(2,first.total());assertEquals(2,second.total());assertNotEquals(first.records().getFirst().planId(),second.records().getFirst().planId());
    }
    @Test void realZeroForecastIsPreservedAndNegativeSentinelIsIgnored() throws Exception {
        when(creation.summariesByPlan(tenant.value(),List.of(id.value()))).thenReturn(Map.of(id.value(),new CreationStore.PlanSummary(List.of(),json.readTree("{\"budget\":{\"estimate_trans_cost_list\":[{\"budget\":100,\"estimate_roi\":0,\"min_estimate_roi\":-1}]}}"))));
        assertEquals(BigDecimal.ZERO,row().roiEstimate().value());assertNull(row().roiEstimate().min());
    }
    private com.fasterxml.jackson.databind.JsonNode metrics(String cost) throws Exception {
        return json.readTree("{\"stat_cost\":{\"Value\":"+cost+"},\"total_pay_order_count_for_roi2\":{\"Value\":0},\"total_pay_order_gmv_include_coupon_for_roi2\":{\"Value\":0},\"total_prepay_and_pay_order_roi2\":{\"Value\":0}}");
    }
    private void cache(com.fasterxml.jackson.databind.JsonNode today,com.fasterxml.jackson.databind.JsonNode yesterday,
                       LocalDate day,com.fasterxml.jackson.databind.JsonNode retained,LocalDate retainedDay,Instant retainedTs) {
        when(remote.list(tenant)).thenReturn(Map.of(id.value(),new PlanRemoteStore.Remote(id.value(),json.createObjectNode(),null,null,Instant.now(),today,yesterday,day,Instant.now(),retained,retainedDay,retainedTs)));
    }
    @Test void pausedPlanWithNoTodayReportDisplaysYesterdayStatisticsAndDate() throws Exception {
        plan.apply(PlanStatus.PAUSED);var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));cache(null,metrics("3.56"),today,null,null,null);
        var row=row();assertEquals(new BigDecimal("3.56"),row.costYuan());assertEquals(0L,row.convert());assertEquals(BigDecimal.ZERO,row.gmvYuan());assertEquals(BigDecimal.ZERO,row.roi());
        assertEquals(today.minusDays(1),row.statisticsDate());assertTrue(row.historicalMetrics());assertNull(row.comparison());assertNotNull(row.snapshotTs());
        var page=query.list(tenant,1,20);assertEquals(new BigDecimal("3.56"),page.summary().cost());assertEquals(1,page.summary().covered());
    }
    @Test void emptyReportsAfterSeveralDaysStillDisplayTheRetainedSnapshot() throws Exception {
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));var saved=Instant.now().minusSeconds(5*86400);
        cache(null,null,today,metrics("3.56"),today.minusDays(5),saved);
        assertEquals(new BigDecimal("3.56"),row().costYuan());assertEquals(saved,row().snapshotTs());assertEquals(today.minusDays(5),row().statisticsDate());assertTrue(row().historicalMetrics());
    }
    @Test void realZeroTodayIsShownWithoutSubstitutingOlderNonzeroData() throws Exception {
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));cache(metrics("0"),metrics("3.56"),today,metrics("3.56"),today.minusDays(1),Instant.now());
        assertEquals(BigDecimal.ZERO,row().costYuan());assertEquals(today,row().statisticsDate());assertFalse(row().historicalMetrics());assertNotNull(row().comparison());
    }
    @Test void EmptyObjectDoesNotHideValidPreviousReport() throws Exception {
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));cache(json.createObjectNode(),metrics("3.56"),today,null,null,null);
        assertEquals(new BigDecimal("3.56"),row().costYuan());assertTrue(row().historicalMetrics());
    }
    @Test void unknownMetricsRemainUnknownRatherThanBeingFilledWithZero() throws Exception {
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));cache(json.readTree("{\"stat_cost\":{\"Value\":3.56}}"),null,today,null,null,null);
        assertEquals(new BigDecimal("3.56"),row().costYuan());assertNull(row().gmvYuan());assertNull(row().convert());assertNull(row().roi());
    }
    @Test void bothTransferModesKeepTheirOwnStatisticsEvenThoughTheyShareMaterials() throws Exception {
        var cost=transferred(PlanStatus.PAUSED);var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        when(remote.list(tenant)).thenReturn(Map.of(id.value(),new PlanRemoteStore.Remote(id.value(),json.createObjectNode(),null,null,Instant.now(),null,metrics("3.56"),today,Instant.now()),
                cost.id().value(),new PlanRemoteStore.Remote(cost.id().value(),json.createObjectNode(),null,null,Instant.now(),null,null,today,Instant.now())));
        var rows=query.list(tenant,1,20).records();assertEquals(new BigDecimal("3.56"),rows.stream().filter(r->r.planId().equals(id.value())).findFirst().orElseThrow().costYuan());
        assertNull(rows.stream().filter(r->r.planId().equals(cost.id().value())).findFirst().orElseThrow().costYuan());
    }
    @Test void noReportsDoesNotInventStatisticsOrAStatisticsDate() {
        cache(null,null,LocalDate.now(),null,null,null);assertNull(row().costYuan());assertNull(row().statisticsDate());assertFalse(row().historicalMetrics());
    }
}
