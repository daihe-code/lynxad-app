package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.application.PlanInsightsService;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class OptimizationDecisionTest {
    final ObjectMapper json=new ObjectMapper();final Instant now=Instant.now();
    final OptimizationStore store=mock(OptimizationStore.class);final OptimizationAutomationStore journal=mock(OptimizationAutomationStore.class);
    final OptimizationConfirmationStore confirmations=mock(OptimizationConfirmationStore.class);final OptimizationExecutionGateway gateway=mock(OptimizationExecutionGateway.class);final OptimizationExecutionService executor=mock(OptimizationExecutionService.class);
    final AdPlan p=mock(AdPlan.class);final OptimizationDecisionService service=new OptimizationDecisionService(store,journal,gateway,confirmations,executor);
    ObjectNode facts,report,action;OptimizationStore.State state;OptimizationAutomationStore.Event event;
    @BeforeEach void setup(){
        facts=json.createObjectNode().put("qcId","123").put("status","DELIVERING").put("budget","300");
        action=json.createObjectNode().put("kind","PAUSE").put("expected","DELIVERING").put("target","PAUSED").put("cause","DAILY_LIMIT");
        report=json.createObjectNode().put("rule_version","merchant-rules-v2").put("observed_at",now.toString()).put("store_id","tenant").put("account_id","account").put("plan_id","123").put("complete",true).put("daily_spend","500");report.putArray("recommendations").addObject().set("action",action);
        var policy=new OptimizationRulesTest().policy("CONFIRM");state=new OptimizationStore.State("tenant","plan",1,true,policy,now,now,null,report);
        event=new OptimizationAutomationStore.Event("event","tenant","plan","account","plan",1,"PAUSE","暂停方案","真实消耗",action,report,"RECOMMENDED",null,null,now,null,null);
        when(p.tenantId()).thenReturn(new TenantId("tenant"));when(p.id()).thenReturn(new PlanId("plan"));when(p.accountId()).thenReturn(new AccountId("account"));when(gateway.plan("tenant","plan")).thenReturn(p);when(gateway.readPlanFacts(p,false)).thenAnswer(i->new OptimizationExecutionGateway.PlanFacts(facts.deepCopy(),"DELIVERY_OK"));when(store.get(p.tenantId(),p.id())).thenReturn(Optional.of(state));when(journal.event("tenant","event")).thenReturn(Optional.of(event));when(journal.claimAccount(eq("tenant"),eq("account"),anyString())).thenReturn(true);when(confirmations.committed(anyString(),anyString(),anyString())).thenReturn(BigDecimal.ZERO);
    }
    @Test void previewRequiresTenantPlanOwnershipAndCurrentProposal(){assertThrows(BizException.class,()->service.preview("other","plan","event"));assertThrows(BizException.class,()->service.preview("tenant","other","event"));assertDoesNotThrow(()->service.preview("tenant","plan","event"));report.putArray("recommendations");state=new OptimizationStore.State("tenant","plan",1,true,state.policy(),now,now,null,report);assertThrows(BizException.class,()->OptimizationDecisionService.validate(state,event,facts,now));}
    @Test void staleNextDayOrChangedVersionRequiresFreshConfirmation(){assertThrows(BizException.class,()->OptimizationDecisionService.validate(state,event,facts,now.plusSeconds(121)));assertThrows(BizException.class,()->OptimizationDecisionService.validate(new OptimizationStore.State("tenant","plan",2,true,state.policy(),now,now,null,report),event,facts,now));assertThrows(BizException.class,()->OptimizationDecisionService.validate(state,event,facts,now.plusSeconds(86400)));}
    @Test void confirmationHashFencesChangedPlatformFacts(){var preview=service.preview("tenant","plan","event");facts.put("budget","400");assertThrows(BizException.class,()->service.confirm("tenant","plan","event","user",1,preview.hash()));verify(confirmations,never()).approve(any(),anyString(),any(),any(),any(),any(),any(),anyString());verifyNoInteractions(executor);}
    @Test void retryOfExistingApprovalCannotDispatchAgain(){var c=new OptimizationConfirmationStore.Confirmation("event","tenant","plan",1,"user",facts,action,report,now,now.plusSeconds(90),BigDecimal.ZERO,LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),"DISPATCHED");when(confirmations.find("tenant","event")).thenReturn(Optional.of(c));service.confirm("tenant","plan","event","user",1,"old");verifyNoInteractions(executor);verify(confirmations,never()).approve(any(),anyString(),any(),any(),any(),any(),any(),anyString());}
    @Test void confirmationIsDurableBeforeTheExecutorRuns(){var preview=service.preview("tenant","plan","event");service.confirm("tenant","plan","event","user",1,preview.hash());var ordered=inOrder(confirmations,executor);ordered.verify(confirmations).approve(eq(event),eq("user"),any(),eq(action),any(),eq(BigDecimal.ZERO),any(),anyString());ordered.verify(executor).process(event);}
    @Test void unverifiedLedgerAndBottleneckPreventPaidGrowth(){action.put("kind","BUDGET").put("expected","300").put("target","400");var e=new OptimizationAutomationStore.Event("event","tenant","plan","account","plan",1,"BUDGET","方案","依据",action,report,"RECOMMENDED",null,null,now,null,null);assertThrows(BizException.class,()->OptimizationDecisionService.validate(state,e,facts,now));}
    @Test void pauseIsOnlyVerifiedWhenThePlanWasExplicitlyDisabled(){
        var paused=facts.deepCopy().put("status","PAUSED");
        assertFalse(OptimizationExecutionService.matches(event,paused.put("qcStatus","OFFLINE_BALANCE")));
        assertFalse(OptimizationExecutionService.matches(event,paused.put("qcStatus","OFFLINE_BUDGET")));
        assertTrue(OptimizationExecutionService.matches(event,paused.put("qcStatus","DISABLE")));
    }
    @Test void submittedTimeoutUsesReadbackAndNeverResends(){
        var insights=mock(PlanInsightsService.class);var review=mock(OptimizationReviewService.class);var actual=new OptimizationExecutionService(store,journal,gateway,insights);actual.merchantActions(confirmations,review);
        var ready=new OptimizationAutomationStore.Event("event","tenant","plan","account","plan",1,"PAUSE","方案","依据",action,report,"READY",null,null,now,null,null);
        var c=new OptimizationConfirmationStore.Confirmation("event","tenant","plan",1,"user",facts.deepCopy(),action,report,now,now.plusSeconds(90),BigDecimal.ZERO,LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),"RESERVED");
        when(confirmations.find("tenant","event")).thenReturn(Optional.of(c));when(journal.event("tenant","event")).thenReturn(Optional.of(ready));when(journal.dispatch(eq(ready),anyString())).thenReturn(true);when(insights.data(any(),any(),any(),eq(true))).thenReturn(new PlanInsightsService.Statistics(Map.of("cost",new BigDecimal("500")),List.of(),"stat_time_hour",now,null));doThrow(new RuntimeException("timeout")).when(gateway).write(any(),any());actual.process(ready);verify(gateway,times(1)).write(p,action);verify(confirmations,never()).notSent(any());
        clearInvocations(gateway);var pending=new OptimizationAutomationStore.Event("event","tenant","plan","account","plan",1,"PAUSE","方案","依据",action,report,"VERIFY_PENDING",null,null,now,now.minusSeconds(200),null);when(journal.event("tenant","event")).thenReturn(Optional.of(pending));actual.process(pending);verify(gateway,never()).write(any(),any());verify(journal).finish(eq(pending),eq("UNKNOWN"),anyString(),any());
    }
}
