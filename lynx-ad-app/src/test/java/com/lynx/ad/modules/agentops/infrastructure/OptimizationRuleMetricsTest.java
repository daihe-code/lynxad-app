package com.lynx.ad.modules.agentops.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.agentops.application.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OptimizationRuleMetricsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final OptimizationRuleMetricsStore metrics=new OptimizationRuleMetricsStore(jdbc);
    final Instant now=Instant.parse("2026-11-05T07:00:00Z");
    OptimizationStore.State state(){var d=OptimizationPolicy.defaults();var rules=new OptimizationRules(BigDecimal.ONE,BigDecimal.TEN,new BigDecimal("100"),"CONFIRM",5,BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("20"));return new OptimizationStore.State("tenant","plan",1,true,new OptimizationPolicy(true,"OBSERVE",d.dailySpendLimit(),d.maxLoss(),d.minSpend(),3,3,60,List.of(),null,null,null,rules),null,now,null,null);}
    @Test void onlyConsecutiveEligibleIntervalsAreCounted(){
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("last_observed_at",Timestamp.from(now.minusSeconds(60)),"last_eligible",true)));
        var report=new ObjectMapper().createObjectNode().put("observed_at",now.toString()).put("state","READY").put("actual_net_roi","4.2");metrics.observed(state(),report);
        verify(jdbc).update(startsWith("UPDATE optimization_rule_day SET effective_seconds"),eq(60L),eq(Timestamp.from(now)),eq(true),eq("tenant"),eq("plan"),eq("2026-11-05"));
    }
    @Test void gapsWaitingDataAndPendingVerificationNeverAddEffectiveTime(){
        for(var scenario:List.of("gap","waiting","pending")){
            reset(jdbc);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("last_observed_at",Timestamp.from(now.minusSeconds(scenario.equals("gap")?300:60)),"last_eligible",true)));
            var report=new ObjectMapper().createObjectNode().put("observed_at",now.toString()).put("state",scenario.equals("waiting")?"WAITING_DATA":"READY").put("actual_net_roi","4.2").put("execution_pending",scenario.equals("pending"));metrics.observed(state(),report);
            verify(jdbc).update(startsWith("UPDATE optimization_rule_day SET effective_seconds"),eq(0L),eq(Timestamp.from(now)),anyBoolean(),eq("tenant"),eq("plan"),eq("2026-11-05"));
        }
    }
    @Test void duplicateOrOutOfOrderObservationsCannotInflateDuration(){
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("last_observed_at",Timestamp.from(now),"last_eligible",true)));
        metrics.observed(state(),new ObjectMapper().createObjectNode().put("observed_at",now.toString()).put("state","READY").put("actual_net_roi","4.2"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
