package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.CreationRecommendationPolicy;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreationRecommendationPolicyTest {
    final ObjectMapper json=new ObjectMapper();final Instant now=Instant.parse("2026-10-24T06:00:00Z");
    CreationState state(){var s=new CreationState();s.accountId="a";s.awemeId="w";s.products.add(new CreationState.Product("123","123","商品",null,null,null,null,null,List.of(),List.of()));return s;}
    ObjectNode result(long age,boolean estimates)throws Exception {
        var r=(ObjectNode)json.readTree("{\"accountId\":\"a\",\"awemeId\":\"w\",\"productIds\":[\"123\"],\"budget\":{\"budget\":50},\"roi\":{\"ecp_roi2_goal\":4.82}}");r.put("fetchedAt",now.minusMillis(age).toString());
        if(estimates)((ObjectNode)r.path("budget")).putArray("estimate_trans_cost_list").addObject().put("budget",50).put("estimate_roi",0.38).put("estimate_convert",8);
        return r;
    }
    @Test void completeResultsAreReusedForFiveMinutesButAnExplicitReviewCanRefresh()throws Exception {
        var s=state();assertTrue(CreationRecommendationPolicy.reusable(result(299999,true),s,now,false));assertFalse(CreationRecommendationPolicy.reusable(result(300000,true),s,now,false));
        assertFalse(CreationRecommendationPolicy.reusable(result(1000,true),s,now,true));
        assertFalse(CreationRecommendationPolicy.reusable(result(1000,true).put("roiError","ROI暂不可用"),s,now,true));
    }
    @Test void emptyEstimatesAndErrorsHaveABoundedRetryDelayInsteadOfBeingCachedForever()throws Exception {
        var s=state();assertTrue(CreationRecommendationPolicy.reusable(result(29999,false),s,now,false));assertFalse(CreationRecommendationPolicy.reusable(result(30000,false),s,now,false));
        var r=result(1000,true).put("budgetError","请求频率受限");assertTrue(CreationRecommendationPolicy.reusable(r,s,now,true));r.put("fetchedAt",now.minusSeconds(31).toString());assertFalse(CreationRecommendationPolicy.reusable(r,s,now,true));
    }
    @Test void accountAwemeProductAndMissingTimestampCannotBorrowAnotherCachedResult()throws Exception {
        var s=state();for(var field:List.of("accountId","awemeId","fetchedAt")){var r=result(0,true);r.remove(field);assertFalse(CreationRecommendationPolicy.reusable(r,s,now,false));}
        var r=result(0,true);r.putArray("productIds").add("456");assertFalse(CreationRecommendationPolicy.reusable(r,s,now,false));
        assertFalse(CreationRecommendationPolicy.reusable(result(-1000,true),s,now,false));
    }
    @Test void aFailedRefreshKeepsOnlyTheSameSelectionsRecentBudgetAmountNotItsForecast()throws Exception {
        var s=state();var old=result(600000,true);var failure=result(0,false);failure.remove("budget");failure.put("budgetError","请求频率受限");failure.put("budgetStatus","RATE_LIMITED");
        var merged=CreationRecommendationPolicy.withLastBudget(old,failure,s,now);
        assertEquals(50,merged.path("budget").path("budget").asInt());assertTrue(merged.path("budgetFromCache").asBoolean());assertEquals(old.path("fetchedAt"),merged.path("budgetFetchedAt"));
        assertFalse(merged.path("budget").has("estimate_trans_cost_list"));assertFalse(failure.has("budgetFromCache"));assertEquals(0.38,old.path("budget").path("estimate_trans_cost_list").get(0).path("estimate_roi").asDouble());
        var next=CreationRecommendationPolicy.withLastBudget(merged,failure,s,now.plusSeconds(30));assertEquals(old.path("fetchedAt"),next.path("budgetFetchedAt"));
    }
    @Test void aSuccessfulEmptyResponseIsAuthoritativeAndCannotResurrectEarlierForecasts()throws Exception {
        var s=state();var latest=result(0,false);assertSame(latest,CreationRecommendationPolicy.withLastBudget(result(600000,true),latest,s,now));
    }
    @Test void invalidOldExpiredOrUnauthorizedBudgetCannotBeReused()throws Exception {
        var s=state();var failure=result(0,false).put("budgetError","获取失败");failure.remove("budget");
        assertSame(failure,CreationRecommendationPolicy.withLastBudget(result(1800000,true),failure,s,now));
        var other=result(1000,true).put("awemeId","other");assertSame(failure,CreationRecommendationPolicy.withLastBudget(other,failure,s,now));
        var unauthorized=failure.deepCopy().put("budgetStatus","AUTH_REQUIRED");assertSame(unauthorized,CreationRecommendationPolicy.withLastBudget(result(1000,true),unauthorized,s,now));
    }
}
