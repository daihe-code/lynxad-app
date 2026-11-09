package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class OptimizationMonitoringTest {
    final TenantId tenant=new TenantId("a");final PlanId id=new PlanId("p");
    final AdPlanRepository plans=mock(AdPlanRepository.class);final AdPlan plan=mock(AdPlan.class);
    final OptimizationStore store=mock(OptimizationStore.class);final MonitoringEngineClient engine=mock(MonitoringEngineClient.class);
    final PlanInsightsService insights=mock(PlanInsightsService.class);final PlanManagementService management=mock(PlanManagementService.class);
    final PlanTransferStore transfers=mock(PlanTransferStore.class);final LynxAdProperties props=new LynxAdProperties();
    final OptimizationMonitoringService service=new OptimizationMonitoringService(plans,store,engine,insights,management,transfers,props);
    OptimizationStore.State state() {return new OptimizationStore.State("a","p",1,true,OptimizationPolicy.defaults(),null,null,null,null);}
    @BeforeEach void setup() {
        props.getMock().setQianchuan(false);when(engine.ready()).thenReturn(true);when(plans.findById(tenant,id)).thenReturn(Optional.of(plan));
        when(plan.tenantId()).thenReturn(tenant);when(plan.id()).thenReturn(id);when(plan.accountId()).thenReturn(new AccountId("account"));when(plan.awemeId()).thenReturn(new AwemeId("aweme"));
        when(plan.qcPlanId()).thenReturn(Optional.of(new QcPlanId("1878663839175696")));when(plan.status()).thenReturn(PlanStatus.PAUSED);when(plan.source()).thenReturn(PlanSource.EXTERNAL);
        when(plan.productIds()).thenReturn(List.of(new QcProductId("item")));when(store.get(tenant,id)).thenReturn(Optional.of(state()));
        when(plan.setting()).thenReturn(new DeliverySetting(SmartBidType.CUSTOM,com.lynx.ad.common.domain.Money.ofYuan(BigDecimal.valueOf(400)),Optional.empty(),null,null));
    }
    @Test void unknownTenantCannotReadOrChangePolicy() {
        assertThrows(BizException.class,()->service.view(new TenantId("other"),id));assertThrows(BizException.class,()->service.save(new TenantId("other"),id,OptimizationPolicy.defaults(),0));verifyNoInteractions(store,management,engine);
    }
    @Test void externalPausedPlansCanBeMonitoredAndKeepTheirOwnIdentity() {var view=service.view(tenant,id);assertTrue(view.available());assertEquals(List.of("item"),view.productIds());}
    @Test void mockDataCannotBecomeRealMonitoringEvidence() {props.getMock().setQianchuan(true);assertFalse(service.view(tenant,id).available());assertThrows(BizException.class,()->service.run(tenant,id));verifyNoInteractions(insights,management);}
    @Test void noGenerationTaskIsRequired() {var view=service.view(tenant,id);assertEquals("OBSERVE",view.policy().mode());verifyNoInteractions(management,insights);}
    @Test void staleOutboxIsCancelledAndFreshDataIsCollected() {
        var s=state();when(store.claim(eq(s),anyString(),any())).thenReturn(true);
        var pending=new OptimizationStore.Pending("old",1,"{}",Instant.now().minusSeconds(601));when(store.pending(s)).thenReturn(Optional.of(pending));
        when(management.monitoringFacts(tenant,id)).thenThrow(new BizException(1801,"授权失效"));service.cycle(s);
        verify(store).discard(s,"old");verify(store).release(eq(s),anyString(),eq("广告账户授权已失效，请重新授权。"));verify(engine,never()).submit(anyString(),anyString());
    }
    OptimizationStore.State rulesState() {
        var rules=new OptimizationRules(new BigDecimal("4"),new BigDecimal("400"),new BigDecimal("480"),"CONFIRM",5,new BigDecimal("100"),new BigDecimal("100"),new BigDecimal("200"));
        var policy=new OptimizationPolicy(true,"OBSERVE",BigDecimal.valueOf(480),BigDecimal.valueOf(200),BigDecimal.valueOf(100),3,3,60,List.of(),null,null,null,rules);
        return new OptimizationStore.State("a","p",1,true,policy,null,null,null,null);
    }
    @Test void ruleDetailsDoNotFetchPlatformIdsOrCallExternalServices() {
        when(plan.productIds()).thenReturn(List.of());when(store.get(tenant,id)).thenReturn(Optional.of(rulesState()));
        assertTrue(service.rulesView(tenant,id).available());verifyNoInteractions(management,insights,engine);
    }
    @Test void restartRequiresAConfirmedDeliveringStatus() {
        var previous=new ObjectMapper().createObjectNode().put("plan_id","1878663839175696")
            .put("observed_at",Instant.now().minusSeconds(90).toString()).put("plan_status","PAUSED");
        var s=new OptimizationStore.State("a","p",1,true,rulesState().policy(),null,null,null,previous);
        when(insights.data(eq(tenant),eq(id),any(),eq(true)))
            .thenReturn(new PlanInsightsService.Statistics(Map.of("cost",new BigDecimal("320")),List.of(),"stat_time_day",Instant.now(),null));
        when(management.monitoringFacts(tenant,id)).thenReturn(
            new PlanManagementService.MonitoringFacts("1878663839175696","PAUSED","CUSTOM",BigDecimal.valueOf(400),null,List.of("item")),
            new PlanManagementService.MonitoringFacts("1878663839175696","DELIVERING","CUSTOM",BigDecimal.valueOf(400),null,List.of("item")));
        assertFalse(service.collectRules(plan,s).containsKey("optimization_restarted_at"));
        var result=service.collectRules(plan,s);
        assertEquals("320",result.get("daily_spend"));
        assertNotNull(result.get("optimization_restarted_at"));
    }
    @Test void ruleObservationOlderThanTwoMinutesIsRecollected() {
        var s=rulesState();when(store.claim(eq(s),anyString(),any())).thenReturn(true);
        when(store.pending(s)).thenReturn(Optional.of(new OptimizationStore.Pending("stale-rules",1,"{}",Instant.now().minusSeconds(121))));
        when(management.monitoringFacts(tenant,id)).thenThrow(new BizException(1801,"授权失效"));service.cycle(s);
        verify(store).discard(s,"stale-rules");verify(store).release(eq(s),anyString(),eq("广告账户授权已失效，请重新授权。"));verifyNoInteractions(engine);
    }
    @Test void networkFailureRetainsOutboxForIdenticalRetry() {
        var s=state();when(store.claim(eq(s),anyString(),any())).thenReturn(true);
        var pending=new OptimizationStore.Pending("retry",1,"payload",Instant.now());when(store.pending(s)).thenReturn(Optional.of(pending));
        when(engine.submit("a","payload")).thenThrow(new BizException(1801,"暂不可用"));service.cycle(s);
        verify(store,never()).discard(any(),anyString());verify(store,never()).complete(any(),anyString(),any(),any());verify(store).release(eq(s),anyString(),eq("暂时无法获取最新投放数据，系统会自动重试。"));verifyNoInteractions(insights,management);
    }
    @Test void collectionTimeoutDoesNotPublishAnObservation() {
        var state = rulesState();
        when(store.claim(eq(state), anyString(), any())).thenReturn(true);
        when(management.monitoringFacts(tenant, id)).thenThrow(new BizException(1406,
                "RPCError{ErrType:[RPC_FAILED] reason=request timeout}"));

        service.cycle(state);

        verify(store).release(eq(state), anyString(), eq("平台响应超时，系统会自动重试。"));
        verify(store, never()).enqueue(any(), anyString(), any());
        verifyNoInteractions(insights, engine);
    }
    @Test void ruleCollectionSkipsRedundantAutomaticExecutionRead() {
        var gateway = mock(OptimizationExecutionGateway.class);
        var journal = mock(OptimizationAutomationStore.class);
        service.execution(new OptimizationExecutionService(store, journal, gateway, insights), gateway, journal);
        when(management.monitoringFacts(tenant, id)).thenReturn(new PlanManagementService.MonitoringFacts(
                "1878663839175696", "DELIVERING", "CUSTOM", BigDecimal.valueOf(400), null, List.of("item")));
        when(insights.data(eq(tenant), eq(id), any(), eq(true)))
                .thenReturn(new PlanInsightsService.Statistics(Map.of("cost", BigDecimal.TEN), List.of(),
                        "stat_time_day", Instant.now(), null));

        assertEquals("10", service.collectRules(plan, rulesState()).get("daily_spend"));
        verify(gateway, never()).facts(any());
    }
    @Test void expiredOrConcurrentLeaseCannotCollectOrSubmit() {service.cycle(state());verifyNoInteractions(engine,insights,management);}
    @Test void cumulativeSummaryIsNotSentAsHourlyEvidence() {
        var stats=new PlanInsightsService.Statistics(Map.of("cost",new BigDecimal("999")),List.of(),"stat_time_day",Instant.now(),null);
        assertTrue(OptimizationMonitoringService.hourly(stats,ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))).isEmpty());
    }
    @Test void currentIncompleteHourExcludedAndMissingNetRevenueStaysNull() {
        var time=ZonedDateTime.of(2026,10,12,12,30,0,0,ZoneId.of("Asia/Shanghai"));
        var stats=new PlanInsightsService.Statistics(Map.of(),List.of(new PlanInsightsService.Point("2026-10-12 11:00:00",Map.of("cost",BigDecimal.ZERO)),new PlanInsightsService.Point("2026-10-12 12:00:00",Map.of("cost",BigDecimal.TEN))),"stat_time_hour",Instant.now(),null);
        var rows=OptimizationMonitoringService.hourly(stats,time);assertEquals(1,rows.size());var metrics=(Map<?,?>)rows.getFirst().get("metrics");assertEquals("0",metrics.get("spend"));assertNull(metrics.get("net_revenue"));assertEquals("window",rows.getFirst().get("metric_mode"));
    }
    @Test void partialPlatformReportCannotQualifyForProfitAdvice() {
        var stats=new PlanInsightsService.Statistics(Map.of(),List.of(new PlanInsightsService.Point("2026-10-12 10:00:00",Map.of())),"stat_time_hour",Instant.now(),"缺少指标");
        assertEquals(false,OptimizationMonitoringService.hourly(stats,ZonedDateTime.of(2026,10,12,12,0,0,0,ZoneId.of("Asia/Shanghai"))).getFirst().get("complete"));
    }
    @Test void thresholdsAndProductEconomicsAreValidated() {
        var good=new OptimizationPolicy(true,"OBSERVE",BigDecimal.valueOf(500),BigDecimal.valueOf(200),BigDecimal.valueOf(100),3,3,60,List.of(new OptimizationPolicy.Economics("item",new BigDecimal(".4"),new BigDecimal(".1"),true)));
        good.validate(Set.of("item"));assertThrows(BizException.class,()->good.validate(Set.of("other")));
        var invalid=new OptimizationPolicy(true,"AUTO",BigDecimal.ONE,BigDecimal.ONE,BigDecimal.TEN,1,2,0,List.of());assertThrows(BizException.class,()->invalid.validate(Set.of()));
    }
    @Test void collectedPayloadKeepsPhysicalIdentityAndNeverSubstitutesGmvForMissingNetRevenue() {
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));var previous=today.minusDays(1);
        when(management.monitoringFacts(tenant,id)).thenReturn(new PlanManagementService.MonitoringFacts("1878663839175696","DELIVERING","CUSTOM",BigDecimal.valueOf(400),new BigDecimal("1.6"),List.of("item")));
        when(transfers.group(tenant,id)).thenReturn(new PlanTransferStore.Group("shared-root","other","other","p",null));
        when(insights.data(eq(tenant),eq(id),any(),eq(true))).thenReturn(new PlanInsightsService.Statistics(Map.of(),List.of(new PlanInsightsService.Point(previous+" 10:00:00",Map.of("cost",BigDecimal.TEN,"gmv",BigDecimal.valueOf(200),"orders",BigDecimal.ONE))),"stat_time_hour",Instant.now(),null),new PlanInsightsService.Statistics(Map.of("cost",BigDecimal.TEN),List.of(),"stat_time_hour",Instant.now(),null));
        var body=service.collect(plan,state());assertEquals("1878663839175696",body.get("plan_id"));assertEquals("shared-root",body.get("logical_plan_id"));assertEquals("a",body.get("store_id"));assertEquals("10",body.get("daily_spend"));assertEquals(today.toString(),body.get("daily_spend_date"));
        var observations=(List<?>)body.get("observations");assertEquals(1,observations.size());var row=(Map<?,?>)observations.getFirst();assertNull(((Map<?,?>)row.get("metrics")).get("net_revenue"));
    }
}
