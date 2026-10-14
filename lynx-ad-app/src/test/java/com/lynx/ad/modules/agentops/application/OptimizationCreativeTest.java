package com.lynx.ad.modules.agentops.application;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class OptimizationCreativeTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void automaticPublicationDetectsRepeatedFailedSceneStyleAndAngle()throws Exception{
        var strategy=json.readTree("{\"scene_strategy\":{\"avoid_scenes\":[\"室内桌面\"],\"avoid_styles\":[\"静态讲解\"],\"avoid_angles\":[\"参数展示\"]}}");
        for(String field:List.of("scenes","visual_style","creative_angle")){
            var profile=json.createObjectNode().put("source","VISUAL_ANALYSIS");String bad=Map.of("scenes","室内桌面","visual_style","静态讲解","creative_angle","参数展示").get(field);
            if(field.equals("scenes"))profile.putArray(field).add(bad+"演示");else profile.put(field,bad);
            assertTrue(OptimizationCreativeService.directionConflict(profile,strategy));
        }
        var good=json.readTree("{\"source\":\"VISUAL_ANALYSIS\",\"scenes\":[\"户外骑行\"],\"visual_style\":\"真实体验\",\"creative_angle\":\"便携收纳\"}");assertFalse(OptimizationCreativeService.directionConflict(good,strategy));
        assertFalse(OptimizationCreativeService.directionEvidenceReady(good,strategy));((com.fasterxml.jackson.databind.node.ObjectNode)good).put("confidence",.9);assertTrue(OptimizationCreativeService.directionEvidenceReady(good,strategy));((com.fasterxml.jackson.databind.node.ObjectNode)good).remove("creative_angle");assertFalse(OptimizationCreativeService.directionEvidenceReady(good,strategy));
    }
    @Test void legacyMaterialAddAuthorizationCannotOptIntoGeneration()throws Exception{
        var old=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(OptimizationPolicy.defaults());old.remove("creativeGeneration");((com.fasterxml.jackson.databind.node.ObjectNode)old.path("tactics")).put("materialAdd",true).putArray("candidatePlanIds").add("donor");
        var policy=json.treeToValue(old,OptimizationPolicy.class);assertFalse(policy.creativeGeneration().enabled());assertFalse(policy.tactics().materialAdd());assertTrue(policy.tactics().candidatePlanIds().isEmpty());
    }
    @Test void newAuthorizationSurvivesEconomicsRefreshAndUsesServerUser(){
        var base=OptimizationPolicy.defaults();var g=OptimizationPolicy.CreativeGeneration.defaults();var enabled=new OptimizationPolicy.CreativeGeneration(true,false,g.formats(),2,2,g.dailyCostLimit(),g.perAssetCostLimit(),g.trialLossLimit(),1000,50,"真实场景","不得虚构功效","forged");
        var policy=new OptimizationPolicy(true,"AUTO_LIMITED",base.dailySpendLimit(),base.maxLoss(),base.minSpend(),3,3,60,List.of(),base.automation(),base.tactics(),enabled).authorized("real-user").withEconomics(List.of(new OptimizationPolicy.Economics("111",new BigDecimal(".4"),BigDecimal.ZERO,true)));
        assertEquals("real-user",policy.creativeGeneration().authorizedBy());assertTrue(policy.tactics().materialAdd());policy.validate(Set.of("111"));assertEquals("100",((Map<?,?>)policy.enginePolicy().get("creative_generation")).get("daily_cost_limit"));
    }
    @Test void parseOnlyOneCurrentProductAndAuthorizedFormat()throws Exception{
        var result=json.readTree("{\"groups\":[{\"group_id\":\"group-1\",\"materials\":[{\"version\":\"v1\",\"status\":\"succeeded\",\"material_id\":\"m1\",\"material_type\":\"product_card\",\"content\":{\"title\":\"真实商品标题\"},\"urls\":{\"image_urls\":[\"https://example.invalid/a.png\"]}}]}]}");
        var material=OptimizationCreativeService.parse(result,"111","product_card","job");assertNotNull(material);assertEquals("111",material.productId);assertEquals("job",material.engineJobId);assertEquals("IMAGE",material.type);assertNull(OptimizationCreativeService.parse(result,"111","video","job"));
        ((com.fasterxml.jackson.databind.node.ArrayNode)result.path("groups").get(0).path("materials")).add(result.path("groups").get(0).path("materials").get(0).deepCopy());assertNull(OptimizationCreativeService.parse(result,"111","product_card","job"));
    }
    @Test void missingTrialFieldsNeverBecomeZeroAndReservedFeeIsDeducted()throws Exception{
        var trial=json.readTree("{\"complete\":true,\"days\":[{\"metrics\":{\"spend\":\"100\",\"net_revenue\":\"400\",\"orders\":\"5\",\"impressions\":\"2000\",\"clicks\":\"100\"}}]}");var profile=new OptimizationPolicy.Economics("111",new BigDecimal(".4"),BigDecimal.ZERO,true);
        var summary=OptimizationCreativeService.evaluate(trial,profile,new BigDecimal("30"));assertEquals(new BigDecimal("30.0"),summary.profit());
        ((com.fasterxml.jackson.databind.node.ObjectNode)trial.path("days").get(0).path("metrics")).remove("impressions");assertNull(OptimizationCreativeService.evaluate(trial,profile,BigDecimal.ZERO));
    }
}
