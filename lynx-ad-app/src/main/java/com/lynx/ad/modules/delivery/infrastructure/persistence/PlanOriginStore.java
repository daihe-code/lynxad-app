package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Shares only publication provenance, never another tenant's plan, materials or credentials. */
@Repository
public class PlanOriginStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PlanOriginStore(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }

    public Set<String> publishedIds(TenantId tenant,AccountId account,Collection<String> qcIds) {
        var ids=qcIds.stream().filter(Objects::nonNull).distinct().toList();
        Set<String> result=new HashSet<>();
        for(int start=0;start<ids.size();start+=500) {
            var batch=ids.subList(start,Math.min(start+500,ids.size()));
            var args=new ArrayList<Object>(List.of(tenant.value(),account.value()));args.addAll(batch);
            String scope="""
                JOIN oauth_account publisher ON publisher.id=origin.oauth_account_id AND publisher.tenant_id=origin.tenant_id
                JOIN oauth_account viewer ON viewer.platform=publisher.platform AND viewer.advertiser_id=publisher.advertiser_id
                WHERE viewer.tenant_id=? AND viewer.id=? AND viewer.platform='QIANCHUAN'
                  AND viewer.grant_id IS NOT NULL AND publisher.grant_id IS NOT NULL
                  AND origin.qc_plan_id IN (
                """+String.join(",",Collections.nCopies(batch.size(),"?"))+")";
            result.addAll(jdbc.queryForList("SELECT DISTINCT origin.qc_plan_id FROM ad_plan origin "+scope+"""
                  AND ((origin.source='LINGTOU' AND origin.publish_version>0) OR EXISTS (
                    SELECT 1 FROM delivery_publish_attempt receipt
                    WHERE receipt.tenant_id=origin.tenant_id AND receipt.plan_id=origin.id
                      AND receipt.qc_plan_id=origin.qc_plan_id AND receipt.status='CREATED'))
                """,String.class,args.toArray()));
            // A transfer can create a new plan or merely enable an existing one. Only a saved create payload is evidence.
            jdbc.query("SELECT receipt.qc_plan_id,receipt.payload_json FROM delivery_transfer_attempt receipt JOIN ad_plan origin ON origin.tenant_id=receipt.tenant_id AND origin.id=receipt.target_plan_id AND origin.qc_plan_id=receipt.qc_plan_id "+scope,
                (org.springframework.jdbc.core.RowCallbackHandler) row->{
                    String body=row.getString(2);if(body==null || body.isBlank())return;
                    try {
                        var payload=json.readTree(body);
                        if(payload!=null && payload.path("multi_product_creative_list").isArray() && !payload.path("multi_product_creative_list").isEmpty())result.add(row.getString(1));
                    } catch(com.fasterxml.jackson.core.JsonProcessingException ignored) { /* Unknown provenance stays external. */ }
                },args.toArray());
        }
        return Set.copyOf(result);
    }
}
