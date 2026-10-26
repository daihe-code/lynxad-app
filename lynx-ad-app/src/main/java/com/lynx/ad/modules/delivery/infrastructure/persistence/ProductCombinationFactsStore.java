package com.lynx.ad.modules.delivery.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Existing configured targets, never inferred product ROI or attributed performance. */
@Repository
public class ProductCombinationFactsStore {
    public record Goals(BigDecimal min, BigDecimal max) {}
    private final JdbcTemplate jdbc;
    public ProductCombinationFactsStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Map<String,Goals> goals(String tenant,String account,String aweme,Collection<String> productIds) {
        var ids=productIds.stream().filter(Objects::nonNull).distinct().toList();
        if(ids.isEmpty())return Map.of();
        var args=new ArrayList<Object>(List.of(tenant,account,aweme));args.addAll(ids);
        var result=new HashMap<String,Goals>();
        jdbc.query("SELECT b.product_id,MIN(p.roi2_goal) AS goal_min,MAX(p.roi2_goal) AS goal_max FROM ad_plan p JOIN ad_plan_creative b ON b.ad_plan_id=p.id "
                +"WHERE p.tenant_id=? AND p.oauth_account_id=? AND p.aweme_id=? AND p.qc_plan_id IS NOT NULL "
                +"AND p.status IN ('DELIVERING','PAUSED','FINISHED') AND p.roi2_goal>0 AND b.product_id IN ("
                +String.join(",",Collections.nCopies(ids.size(),"?"))+") GROUP BY b.product_id",
                (org.springframework.jdbc.core.RowCallbackHandler) row->result.put(row.getString("product_id"),new Goals(row.getBigDecimal("goal_min"),row.getBigDecimal("goal_max"))),args.toArray());
        return result;
    }
}
