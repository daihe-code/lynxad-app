package com.lynx.ad.modules.delivery.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.time.Instant;
import java.util.*;

/** Application refresh intervals; these are not platform quote expiry guarantees. */
public final class CreationRecommendationPolicy {
    private CreationRecommendationPolicy() {}
    public static final long COMPLETE_TTL_MS=300_000, RETRY_DELAY_MS=30_000, LAST_BUDGET_TTL_MS=1_800_000;
    public static boolean sameScope(CreationState a,CreationState b) {
        return Objects.equals(a.accountId,b.accountId)&&Objects.equals(a.awemeId,b.awemeId)
            &&new HashSet<>(a.products.stream().map(CreationState.Product::qcProductId).toList()).equals(new HashSet<>(b.products.stream().map(CreationState.Product::qcProductId).toList()));
    }
    public static boolean matches(JsonNode r,CreationState s) {
        if(r==null||!s.accountId.equals(r.path("accountId").asText())||!s.awemeId.equals(r.path("awemeId").asText())||!r.path("productIds").isArray())return false;
        var ids=new HashSet<String>();r.path("productIds").forEach(id->ids.add(id.asText()));
        return ids.size()==s.products.size()&&s.products.stream().allMatch(p->ids.contains(p.qcProductId()));
    }
    public static boolean reusable(JsonNode r,CreationState s,Instant now,boolean force) {
        if(!matches(r,s))return false;
        long age=age(r.path("fetchedAt").asText(),now);
        if(age<0)return false;
        boolean failed=r.hasNonNull("budgetError")||r.hasNonNull("roiError");
        if(force)return r.hasNonNull("budgetError")&&age<RETRY_DELAY_MS;
        boolean complete=!failed&&positive(r.path("budget").path("budget"))&&r.path("budget").path("estimate_trans_cost_list").isArray()
            &&!r.path("budget").path("estimate_trans_cost_list").isEmpty()&&positive(r.path("roi").path("ecp_roi2_goal"));
        return age<(complete?COMPLETE_TTL_MS:RETRY_DELAY_MS);
    }
    public static JsonNode withLastBudget(JsonNode previous,JsonNode response,CreationState s,Instant now) {
        if(!matches(previous,s)||response==null||!response.isObject()||!response.hasNonNull("budgetError")
            ||"AUTH_REQUIRED".equals(response.path("budgetStatus").asText())||!positive(previous.path("budget").path("budget")))return response;
        String fetched=previous.path("budgetFetchedAt").asText(previous.path("fetchedAt").asText());
        long age=age(fetched,now);if(age<0||age>=LAST_BUDGET_TTL_MS)return response;
        ObjectNode result=response.deepCopy();
        // Keep only the last budget amount. Old estimate rows must never become a new platform quote.
        result.putObject("budget").set("budget",previous.path("budget").path("budget").deepCopy());
        result.put("budgetFromCache",true);result.put("budgetFetchedAt",fetched);
        return result;
    }
    private static boolean positive(JsonNode n) {try{return n!=null&&(n.isNumber()||n.isTextual())&&new java.math.BigDecimal(n.asText()).signum()>0;}catch(RuntimeException invalid){return false;}}
    private static long age(String timestamp,Instant now) {try{return java.time.Duration.between(Instant.parse(timestamp),now).toMillis();}catch(RuntimeException invalid){return -1;}}
}
