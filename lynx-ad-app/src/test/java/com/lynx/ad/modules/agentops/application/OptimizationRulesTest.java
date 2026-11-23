package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OptimizationRulesTest {
    final ObjectMapper json=new ObjectMapper();
    final OffsetDateTime now=OffsetDateTime.parse("2026-11-05T15:30:00+08:00");
    OptimizationRules rules(String action){return new OptimizationRules(new BigDecimal("4"),new BigDecimal("400"),new BigDecimal("480"),action,5,new BigDecimal("100"),new BigDecimal("100"),new BigDecimal("200"));}
    OptimizationPolicy policy(String action){var p=OptimizationPolicy.defaults();return new OptimizationPolicy(true,"AUTO_LIMITED",p.dailySpendLimit(),p.maxLoss(),p.minSpend(),p.minOrders(),p.persistenceWindows(),p.conversionDelayMinutes(),List.of(new OptimizationPolicy.Economics("x",BigDecimal.ONE,BigDecimal.ZERO,true)),p.automation(),p.tactics(),new OptimizationPolicy.CreativeGeneration(true,true,List.of("video"),2,1,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE,1000,50,"","","owner"),rules(action));}
    ObjectNode data(String spend,String net){var d=json.createObjectNode().put("store_id","t").put("account_id","a").put("plan_id","123").put("plan_status","DELIVERING").put("observed_at",now.toString()).put("complete",true).put("daily_spend",spend);if(net!=null)d.put("daily_net_revenue",net);return d;}
    void mature(ObjectNode data,String net){var windows=data.putArray("observations");for(int i=11;i<14;i++){var w=windows.addObject().put("complete",true).put("window_start","2026-11-05T"+i+":00:00+08:00").put("window_end","2026-11-05T"+(i+1)+":00:00+08:00");w.putObject("metrics").put("spend","40").put("net_revenue",net);}}
    @Test void validatesRelationsAndDecimalPrecision(){assertDoesNotThrow(()->rules("CONFIRM").validate());for(var r:List.of(new OptimizationRules(BigDecimal.ZERO,BigDecimal.ONE,BigDecimal.ONE,"CONFIRM",5,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE),new OptimizationRules(BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ONE,"CONFIRM",5,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE),new OptimizationRules(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.TEN,"CONFIRM",0,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE),new OptimizationRules(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.TEN,"CONFIRM",5,new BigDecimal("1.001"),BigDecimal.ONE,BigDecimal.ONE),new OptimizationRules(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.TEN,"CONFIRM",5,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ONE)))assertThrows(BizException.class,r::validate);}
    @Test void newRulesCannotInheritLegacyWriteOrPaidGenerationPermissions() throws Exception {
        for(var action:List.of("CONFIRM","AUTO_PAUSE")){var p=policy(action);assertFalse(p.automation().budget());assertFalse(p.automation().roi());assertFalse(p.automation().resumeNextDay());assertFalse(p.tactics().active());assertFalse(p.creativeGeneration().enabled());assertTrue(p.economics().isEmpty());assertEquals(action.equals("AUTO_PAUSE"),p.automation().pause());assertDoesNotThrow(()->p.validate(Set.of("new-product")));assertEquals(p,json.readValue(json.writeValueAsString(p),OptimizationPolicy.class));}
        assertNull(json.readValue(json.writeValueAsString(OptimizationPolicy.defaults()),OptimizationPolicy.class).rules());
    }
    @Test void dailyProtectionDoesNotWaitForRevenueAndAutoPauseIsExplicit(){
        for(var action:List.of("CONFIRM","AUTO_PAUSE")){var r=OptimizationRulesEvaluator.evaluate(data("480",null),null,policy(action));var advice=r.path("recommendations").get(0);assertEquals("SPEND_PROTECTION",advice.path("type").asText());assertEquals(action.equals("AUTO_PAUSE"),advice.path("execution_allowed").asBoolean());assertEquals("WAITING_DATA",r.path("state").asText());assertFalse(r.has("actual_net_roi"));}
    }
    @Test void missingStaleOrPausedEvidenceNeverTriggersAnAction(){
        var invalid=data("500","0").put("complete",false);assertTrue(OptimizationRulesEvaluator.evaluate(invalid,null,policy("AUTO_PAUSE")).path("recommendations").isEmpty());
        var paused=data("500","0").put("plan_status","PAUSED");assertEquals("PAUSED",OptimizationRulesEvaluator.evaluate(paused,null,policy("AUTO_PAUSE")).path("state").asText());assertTrue(OptimizationRulesEvaluator.evaluate(paused,null,policy("AUTO_PAUSE")).path("recommendations").isEmpty());
    }
    ObjectNode deliveryData(int minute, int lastCheckMinute, String activity) {
        var d = data("0", null).put("observed_at", now.plusMinutes(minute).toString());
        d.putArray("product_ids").add("111");
        d.putObject("delivery_check").put("activity", activity).put("checked_at", now.plusMinutes(lastCheckMinute).toString());
        return d;
    }
    ObjectNode matureDelivery(ObjectNode input) {
        var at=OffsetDateTime.parse(input.path("observed_at").asText());
        input.putObject("delivery_check").put("activity","ACTIVE").put("checked_at",at.toString());
        var prior=input.deepCopy();
        prior.putObject("delivery_observation").put("origin","").put("at",at.minusMinutes(1).toString())
            .put("eligible",true).put("had_volume",true).put("effective_millis",Duration.ofHours(4).minusMinutes(1).toMillis());
        return prior;
    }
    @Test void verifiedZeroDeliveryWatchesAtOneHourAndInvestigatesAtFourAcrossMidnight() {
        var p = policy("AUTO_PAUSE");
        JsonNode previous = null;
        int start=6*60+30;
        for (int elapsed = 0; elapsed <= 240; elapsed++) {
            previous = OptimizationRulesEvaluator.evaluate(deliveryData(start+elapsed,start+elapsed/10*10,"ZERO"), previous, p);
            var recommendations=previous.path("recommendations");
            if(elapsed<60)assertTrue(recommendations.isEmpty());
            else assertEquals(elapsed<240?"DELIVERY_OBSERVING":"DELIVERY_NO_ACTIVITY",recommendations.get(0).path("type").asText());
            assertEquals(elapsed<240?"WAITING_DATA":"NO_DELIVERY",previous.path("state").asText());
        }
        var item = previous.path("recommendations").get(0);
        assertFalse(item.has("action"));
        assertFalse(item.path("execution_allowed").asBoolean());
        assertEquals("data",item.path("workflow").asText());
        assertFalse(previous.has("actual_net_roi"));
    }
    @Test void pausesAndUnverifiedGapsExcludeTimeAndBreakTheContinuousZeroRun() {
        var policy=policy("CONFIRM");
        JsonNode previous=null;
        for(int minute=0;minute<=59;minute++)previous=OptimizationRulesEvaluator.evaluate(deliveryData(minute,minute,"ZERO"),previous,policy);
        var paused=deliveryData(60,60,"ZERO").put("plan_status","PAUSED");
        previous=OptimizationRulesEvaluator.evaluate(paused,previous,policy);
        var afterGap=OptimizationRulesEvaluator.evaluate(deliveryData(225,225,"ZERO"),previous,policy);
        assertEquals(Duration.ofMinutes(59).toMillis(),afterGap.path("delivery_observation").path("effective_millis").asLong());
        assertTrue(afterGap.path("recommendations").isEmpty());
        var unknown=OptimizationRulesEvaluator.evaluate(deliveryData(226,226,"UNKNOWN"),afterGap,policy);
        var resumed=OptimizationRulesEvaluator.evaluate(deliveryData(227,227,"ZERO"),unknown,policy);
        assertEquals(Duration.ofMinutes(59).toMillis(),resumed.path("delivery_observation").path("effective_millis").asLong());
        assertTrue(resumed.path("recommendations").isEmpty());
        var changed=deliveryData(228,228,"ZERO");
        changed.putArray("product_ids").add("222");
        assertEquals(0,OptimizationRulesEvaluator.evaluate(changed,resumed,policy).path("delivery_observation").path("effective_millis").asLong());
    }
    @Test void anActivePlanDoesNotStartAnotherZeroVolumeWarningAfterMidnight() {
        JsonNode previous=null;
        var policy=policy("CONFIRM");
        for(int minute=500;minute<=800;minute++) {
            previous=OptimizationRulesEvaluator.evaluate(deliveryData(minute,minute,minute<510?"ACTIVE":"ZERO"),previous,policy);
            assertTrue(previous.path("recommendations").isEmpty());
        }
        assertTrue(previous.path("delivery_observation").path("had_volume").asBoolean());
    }
    @Test void roiRequiresThreeContinuousMatureHoursAndNeverWritesPlatformRoi(){
        var d=data("120","100");mature(d,"100");var previous=matureDelivery(d);
        assertTrue(OptimizationRulesEvaluator.evaluate(d,null,policy("CONFIRM")).path("recommendations").isEmpty());
        var r=OptimizationRulesEvaluator.evaluate(d,previous,policy("CONFIRM"));assertEquals("READY",r.path("state").asText());assertEquals("2.50000000",r.path("actual_net_roi").asText());assertEquals("ROI_PROTECTION",r.path("recommendations").get(0).path("type").asText());assertFalse(r.path("recommendations").get(0).has("action"));
        ((ObjectNode)d.path("observations").get(1)).put("window_start","2026-11-05T10:00:00+08:00");assertFalse(OptimizationRulesEvaluator.evaluate(d,previous,policy("CONFIRM")).has("actual_net_roi"));
    }
    @Test void dayLevelHistoryCanProveVolumeButCannotBackfillUnobservedHours() {
        var input = deliveryData(0, 0, "ZERO");
        var started = now.minusDays(3);
        input.put("delivery_baseline", started.toString());
        var history = input.putObject("delivery_history").put("activity", "ZERO")
            .put("baseline", started.toString()).put("through", now.toLocalDate().minusDays(1).toString());
        var report = OptimizationRulesEvaluator.evaluate(input, null, policy("CONFIRM"));
        assertEquals(0,report.path("delivery_observation").path("effective_millis").asLong());
        assertTrue(report.path("recommendations").isEmpty());
        history.put("activity", "ACTIVE");
        assertTrue(OptimizationRulesEvaluator.evaluate(input,null,policy("CONFIRM")).path("delivery_observation").path("had_volume").asBoolean());
    }
    @Test void reliableMatureRoiCanBeDiagnosedWithoutTheDailySpendTotal(){
        var input=data(null,null).put("complete",false);
        mature(input,"100");
        var report=OptimizationRulesEvaluator.evaluate(input,matureDelivery(input),policy("AUTO_PAUSE"));
        assertEquals("2.50000000",report.path("actual_net_roi").asText());
        assertEquals(1,report.path("recommendations").size());
        assertEquals("ROI_PROTECTION",report.path("recommendations").get(0).path("type").asText());
        assertTrue(report.path("rule_samples").isEmpty());
    }
    @Test void platformBudgetBottleneckIsAnAdvisoryAndDoesNotConfuseAccountBudgetWithPlanBudget(){
        var input=data("480",null).put("plan_status","PAUSED").put("platform_status","OFFLINE_BUDGET");
        var report=OptimizationRulesEvaluator.evaluate(input,null,policy("AUTO_PAUSE"));
        assertEquals(1,report.path("recommendations").size());
        var notice=report.path("recommendations").get(0);
        assertEquals("BUDGET_CONSTRAINT",notice.path("type").asText());
        assertFalse(notice.has("action"));
        var account=OptimizationRulesEvaluator.evaluate(input.put("platform_status","ADVERTISER_OFFLINE_BUDGET"),null,policy("AUTO_PAUSE"));
        assertEquals("DELIVERY_BLOCKED",account.path("recommendations").get(0).path("type").asText());
        assertEquals("账户预算已用尽",account.path("recommendations").get(0).path("title").asText());
    }
    @Test void aConfirmedAuditIssueIsNotDelayedByTheStartupWindowOrMissingStatistics() {
        var input=deliveryData(0,0,"UNKNOWN").put("complete",false);
        input.putObject("diagnostic_context").putArray("delivery_issues").addObject()
            .put("object_type","MATERIAL").put("object_id","456").put("title","素材审核未通过").put("evidence","审核拒绝");
        var report=OptimizationRulesEvaluator.evaluate(input,null,policy("CONFIRM"));
        assertEquals("DELIVERY_BLOCKED",report.path("recommendations").get(0).path("type").asText());
        assertEquals("456",report.path("recommendations").get(0).path("object_id").asText());
    }
    @Test void restartedMonitoringUsesNewWindowsButKeepsDailySpendProtection() {
        var prior=data("320","900");
        prior.putArray("rule_samples").addObject().put("at",now.minusMinutes(5).toString()).put("spend","320");
        var resumed=data("480","900").put("optimization_restarted_at",now.minusMinutes(1).toInstant().toString());
        mature(resumed,"200");
        var result=OptimizationRulesEvaluator.evaluate(resumed,prior,policy("CONFIRM"));
        assertEquals(1,result.path("rule_samples").size());
        assertTrue(result.path("roi_windows").isEmpty());
        assertEquals("480",result.path("daily_spend").asText());
        assertEquals(1,result.path("recommendations").size());
        assertEquals("SPEND_PROTECTION",result.path("recommendations").get(0).path("type").asText());

        resumed.put("optimization_restarted_at",now.minusHours(5).toInstant().toString());
        assertEquals("READY",OptimizationRulesEvaluator.evaluate(resumed,matureDelivery(resumed),policy("CONFIRM")).path("state").asText());
    }
    @Test void runawayNeedsActualContinuousMinuteSamplesNotRepeatedReadsOrHourlyInterpolation(){
        var p=policy("CONFIRM");JsonNode previous=null;for(int minute=0;minute<=5;minute++){var d=data(String.valueOf(minute*22),"0").put("observed_at",now.minusMinutes(5-minute).toString());previous=OptimizationRulesEvaluator.evaluate(d,previous,p);}
        assertTrue(previous.path("recommendations").toString().contains("RUNAWAY_CHECK"));assertFalse(previous.path("recommendations").get(0).path("execution_allowed").asBoolean());
        var gap=json.createObjectNode();gap.putArray("rule_samples").addObject().put("at",now.minusMinutes(5).toString()).put("spend","0").put("revenue","0");assertFalse(OptimizationRulesEvaluator.evaluate(data("110","0"),gap,p).path("recommendations").toString().contains("RUNAWAY_CHECK"));
        var repeated=OptimizationRulesEvaluator.evaluate(data("110","0"),null,p);for(int i=0;i<10;i++)repeated=OptimizationRulesEvaluator.evaluate(data("110","0"),repeated,p);assertEquals(1,repeated.path("rule_samples").size());
    }
    @Test void growthAdviceIsBoundedAndAlwaysRequiresConfirmation(){var d=data("150","900");mature(d,"200");var r=OptimizationRulesEvaluator.evaluate(d,matureDelivery(d),policy("AUTO_PAUSE"));var recommendation=r.path("recommendations").get(0);assertEquals("GROWTH_REVIEW",recommendation.path("type").asText());assertTrue(recommendation.path("requires_confirmation").asBoolean());assertFalse(recommendation.path("execution_allowed").asBoolean());assertEquals("100",recommendation.path("max_addition").asText());}
    @Test void executionRejectsEverythingExceptAuthorizedDailyPause(){
        var p=policy("AUTO_PAUSE");var at=now.toInstant();var facts=json.createObjectNode().put("qcId","123").put("status","DELIVERING");var report=OptimizationRulesEvaluator.evaluate(data("480",null),null,p);var state=new OptimizationStore.State("t","p",1,true,p,null,at,null,report);
        for(var kind:List.of("BUDGET","ROI","ENABLE","BOOST","MATERIAL_FOLLOW","MATERIAL_GENERATE","MATERIAL_REMOVE","PRODUCT_REMOVE")){var proposal=json.createObjectNode().put("kind",kind).put("cause","DAILY_LIMIT");var event=new OptimizationAutomationStore.Event("e","t","p","a","plan",1,kind,"title","evidence",proposal,report,"READY",null,null,at,null,null);assertThrows(BizException.class,()->OptimizationExecutionService.validate(state,event,facts,at,0));}
        var proposal=json.createObjectNode().put("kind","PAUSE").put("cause","DAILY_LIMIT").put("expected","DELIVERING").put("target","PAUSED");var event=new OptimizationAutomationStore.Event("e","t","p","a","plan",1,"PAUSE","title","evidence",proposal,report,"READY",null,null,at,null,null);assertDoesNotThrow(()->OptimizationExecutionService.validate(state,event,facts,at,0));
    }
}
