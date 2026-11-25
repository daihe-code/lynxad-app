package com.lynx.ad.modules.agentops.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.modules.agentops.application.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class OptimizationRuleMetricsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final OptimizationRuleMetricsStore metrics=new OptimizationRuleMetricsStore(jdbc);
    final Instant now=Instant.parse("2026-11-05T07:00:00Z");
    OptimizationStore.State state(){var d=OptimizationPolicy.defaults();var rules=new OptimizationRules(BigDecimal.ONE,BigDecimal.TEN,new BigDecimal("100"),"CONFIRM",5,BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("20"));return new OptimizationStore.State("tenant","plan",1,true,new OptimizationPolicy(true,"OBSERVE",d.dailySpendLimit(),d.maxLoss(),d.minSpend(),3,3,60,List.of(),null,null,null,rules),null,now,null,null);}

    @Test void spendProtectionMonitoringCountsWhileWaitingForRoi(){
        priorObservation(60, true);
        var state=state();
        var report=OptimizationRulesEvaluator.evaluate(observation().put("complete",true).put("daily_spend","101"),null,state.policy());
        assertEquals("WAITING_DATA",report.path("state").asText());
        assertFalse(report.has("actual_net_roi"));
        assertEquals("SPEND_PROTECTION",report.path("recommendations").get(0).path("type").asText());
        metrics.observed(state,report);
        verifyCount(60,true);
    }

    @ParameterizedTest
    @ValueSource(strings={"ACTIVE","ZERO"})
    void verifiedDeliveryObservationCountsWithoutSpendSummary(String activity){
        priorObservation(60,true);
        var input=observation();
        input.putObject("delivery_check").put("activity",activity).put("checked_at",now.toString());
        observe(input);
        verifyCount(60,true);
    }

    @Test void aVerifiedDeliveryIssueCountsWithoutMetrics(){
        priorObservation(60,true);
        observe(observation().put("platform_status","PRE_OFFLINE_BUDGET"));
        verifyCount(60,true);
    }

    @ParameterizedTest
    @ValueSource(strings={"PAUSED","WAITING","FINISHED"})
    void nonDeliveringPlansDoNotAccumulateTimeEvenWithSpend(String status){
        priorObservation(60,true);
        observe(observation().put("plan_status",status).put("complete",true).put("daily_spend","100"));
        verifyCount(0,false);
    }

    @Test void anUnknownDeliveryCheckAndIncompleteSpendDoNotCount(){
        priorObservation(60,true);
        var input=observation().put("daily_spend","100");
        input.putObject("delivery_check").put("activity","UNKNOWN").put("checked_at",now.toString());
        observe(input);
        verifyCount(0,false);
    }

    @Test void pendingVerificationDoesNotCount(){
        priorObservation(60,true);
        observe(observation().put("complete",true).put("daily_spend","101").put("execution_pending",true));
        verifyCount(0,false);
    }

    @ParameterizedTest
    @ValueSource(strings={"gap","ineligible","interrupted"})
    void aFreshSampleStartsANewIntervalAfterAContinuityBreak(String reason){
        priorObservation(reason.equals("gap")?300:60,!reason.equals("ineligible"));
        observe(observation().put("complete",true).put("daily_spend","101")
            .put("observation_interrupted",reason.equals("interrupted")));
        verifyCount(0,true);
    }

    @Test void duplicateOrOutOfOrderObservationsCannotInflateDuration(){
        priorObservation(0,true);
        observe(observation().put("complete",true).put("daily_spend","101"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    private ObjectNode observation(){
        return new ObjectMapper().createObjectNode().put("observed_at",now.toString()).put("plan_status","DELIVERING");
    }

    private void observe(ObjectNode input){
        var state=state();
        metrics.observed(state,OptimizationRulesEvaluator.evaluate(input,null,state.policy()));
    }

    private void priorObservation(long secondsAgo, boolean eligible){
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of(
            "last_observed_at",Timestamp.from(now.minusSeconds(secondsAgo)),"last_eligible",eligible)));
    }

    private void verifyCount(long seconds, boolean eligible){
        verify(jdbc).update(startsWith("UPDATE optimization_rule_day SET effective_seconds"),eq(seconds),
            eq(Timestamp.from(now)),eq(eligible),eq("tenant"),eq("plan"),eq("2026-11-05"));
    }
}
