package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class OptimizationEffectComparisonTest {
    final MonitoringStrategy.Parameters strategy=new OptimizationDiagnosticsTest().approved();
    ObjectNode window(int start,int revenue){var n=JsonNodeFactory.instance.objectNode().put("complete",true);n.putObject("metric_fields").put("netGmv","net_pay_order_gmv");var rows=n.putArray("rows");for(int i=0;i<3;i++){var at=OffsetDateTime.parse("2026-11-05T00:00:00+08:00").plusHours(start+i);var row=rows.addObject().put("window_start",at.toString()).put("window_end",at.plusHours(1).toString()).put("complete",true);row.putObject("metrics").put("spend","50").put("net_revenue",String.valueOf(revenue));}return n;}
    @Test void onlyEqualCompleteSameDefinitionWindowsGiveObservedChange(){var r=OptimizationEffectComparison.compare(window(1,200),window(6,220),strategy);assertEquals("COMPARED",r.path("status").asText());assertEquals("0.40000000",r.path("roi_change").asText());assertEquals("OBSERVED_CHANGE",r.path("attribution").asText());}
    @Test void metadataChangeOrMissingDefinitionCannotBeCompared(){var after=window(6,220);after.putObject("metric_fields").put("netGmv","different");assertEquals("INCOMPARABLE",OptimizationEffectComparison.compare(window(1,200),after,strategy).path("status").asText());var before=window(1,200);before.putObject("metric_fields");assertEquals("INCOMPARABLE",OptimizationEffectComparison.compare(before,after,strategy).path("status").asText());}
    @Test void missingRowsNegativeCorrectionsAndZeroSpendNeverShowImprovement(){for(String scenario:new String[]{"missing","gap","negative","zero","partial"}){var after=window(6,220);switch(scenario){case "missing"->((ArrayNode)after.path("rows")).remove(1);case "gap"->((ObjectNode)after.path("rows").get(1)).put("window_start","2026-11-05T09:00:00+08:00");case "negative"->((ObjectNode)after.path("rows").get(0).path("metrics")).put("net_revenue","-1");case "zero"->{for(var row:after.path("rows"))((ObjectNode)row.path("metrics")).put("spend","0");}default->after.put("complete",false);}assertEquals("WAITING_DATA",OptimizationEffectComparison.compare(window(1,200),after,strategy).path("status").asText(),scenario);}}
}
