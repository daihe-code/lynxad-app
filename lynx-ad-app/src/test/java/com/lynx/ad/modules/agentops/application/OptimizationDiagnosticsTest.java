package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

class OptimizationDiagnosticsTest {
    final ObjectMapper json=new ObjectMapper();
    final OptimizationRulesTest fixture=new OptimizationRulesTest();
    MonitoringStrategy.Parameters approved(){var s=MonitoringStrategy.Parameters.shadow();return new MonitoringStrategy.Parameters("approved-test",true,3,60,s.minSpend(),s.minImpressions(),s.minClicks(),s.minOrders(),s.declineRatio(),3,72,true);}
    ObjectNode report(String net){
        var d=fixture.data("120",net);fixture.mature(d,net);
        d.putObject("metric_fields").put("netGmv","net_pay_order_gmv");
        var context=d.putObject("diagnostic_context").put("complete",true).put("ledger_verified",false).put("budget_constrained",false);context.putArray("entities");
        var observations=(ArrayNode)d.path("observations");var old=observations.deepCopy();
        for(var row:old){var v=(ObjectNode)row;v.put("window_start",OffsetDateTime.parse(v.path("window_start").asText()).minusDays(1).toString());v.put("window_end",OffsetDateTime.parse(v.path("window_end").asText()).minusDays(1).toString());((ObjectNode)v.path("metrics")).put("net_revenue","200");observations.add(v);}
        for(var row:observations)((ObjectNode)row.path("metrics")).put("impressions","1000").put("clicks",row.path("window_start").asText().startsWith("2026-11-04")?"100":"30").put("orders","10");
        return d;
    }
    JsonNode evaluate(ObjectNode d,MonitoringStrategy.Parameters p)throws Exception{return OptimizationRulesEvaluator.evaluate(json.writeValueAsString(d),null,fixture.policy("CONFIRM"),p);}
    @Test void candidateStrategyCannotCreateMerchantRoiAdviceOrEffectiveBusinessTime()throws Exception{
        var r=evaluate(report("100"),MonitoringStrategy.Parameters.shadow());assertFalse(r.path("business_ready").asBoolean());assertFalse(r.path("recommendations").toString().contains("ROI_PROTECTION"));assertFalse(r.path("recommendations").toString().contains("E4-"));assertTrue(r.path("diagnostics").size()>0);for(var f:r.path("diagnostics"))assertTrue(f.path("shadow").asBoolean());
    }
    @Test void approvedDiagnosticsExposeSameObjectEvidenceAndKeepPaidGrowthRestricted()throws Exception{
        var r=evaluate(report("100"),approved());assertTrue(r.path("diagnostics").toString().contains("E4-04"));assertTrue(r.path("diagnostics_ready").asBoolean());assertFalse(r.path("business_ready").asBoolean());assertFalse(r.path("recommendations").toString().contains("GROWTH_BUDGET"));assertTrue(r.path("recommendations").get(0).path("requires_confirmation").asBoolean());assertFalse(r.path("recommendations").get(0).path("execution_allowed").asBoolean());
    }
    @Test void highRoiDoesNotInventBudgetBottleneck()throws Exception{var d=report("240");for(var row:d.path("observations"))((ObjectNode)row.path("metrics")).put("clicks","100");var r=evaluate(d,approved());assertFalse(r.path("recommendations").toString().contains("GROWTH_BUDGET"));}
    @Test void zeroKnownNetIsDifferentFromMissingNet()throws Exception{
        var known=evaluate(report("0"),approved());assertTrue(known.path("diagnostics").toString().contains("E4-03"));
        var missing=report("0");for(var row:missing.path("observations"))((ObjectNode)row.path("metrics")).remove("net_revenue");var r=evaluate(missing,approved());assertFalse(r.path("diagnostics").toString().contains("E4-03"));assertFalse(r.path("diagnostics_ready").asBoolean());
    }
    @Test void missingHourOrNegativeCorrectionCannotBecomeMatureEvidence()throws Exception{
        for(String mode:new String[]{"gap","negative"}){var d=report("100");var row=(ObjectNode)d.path("observations").get(1);if(mode.equals("gap"))row.put("window_start","2026-11-05T10:00:00+08:00");else((ObjectNode)row.path("metrics")).put("spend","-1");var r=evaluate(d,approved());assertFalse(r.path("diagnostics_ready").asBoolean());assertFalse(r.has("actual_net_roi"));}
    }
    @Test void spendAccelerationStillCreatesALeadWithNoRevenue()throws Exception{
        JsonNode previous=null;for(int minute=0;minute<=5;minute++){var d=fixture.data(String.valueOf(minute*22),null).put("observed_at",fixture.now.minusMinutes(5-minute).toString());previous=OptimizationRulesEvaluator.evaluate(json.writeValueAsString(d),previous,fixture.policy("CONFIRM"),approved());}
        assertTrue(previous.path("recommendations").toString().contains("RUNAWAY_CHECK"));assertFalse(previous.path("recommendations").toString().contains("RUNAWAY_RISK"));
    }
    @Test void strategyConfigRejectsUnapprovedRiskModelAndOutOfBoundsValues(){
        assertThrows(IllegalArgumentException.class,()->new MonitoringStrategy(json,"{bad"));
        var s=MonitoringStrategy.Parameters.shadow();assertThrows(IllegalArgumentException.class,()->new MonitoringStrategy.Parameters("test",false,3,60,BigDecimal.TEN,1000,50,3,s.declineRatio(),3,72,true));
    }
}
