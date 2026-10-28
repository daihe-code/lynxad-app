package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.service.DraftExpiryPolicy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Expiration removes local drafts from normal lists while retaining their recovery/audit data. */
@Repository
public class DraftExpiryStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public DraftExpiryStore(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.json = json; this.tx = new TransactionTemplate(manager);
    }
    private record Key(String tenant, String id) {}
    private record Draft(String status, String qcId, String source, Instant createdAt) {}
    private record Creation(String body, Instant leaseUntil) {}
    private static boolean localDraft(Draft draft) {
        return "DRAFT".equals(draft.status) && draft.qcId == null && "LINGTOU".equals(draft.source);
    }
    private JsonNode state(String body) {
        try { return json.readTree(body); }
        catch (Exception e) { throw new IllegalStateException("Invalid draft state", e); }
    }
    private boolean publishPending(Key key, JsonNode state) {
        if (state != null && (!Set.of("NONE", "FAILED").contains(state.path("publishStatus").asText("NONE"))
                || state.path("qcPlanId").isTextual() && !state.path("qcPlanId").asText().isBlank())) return true;
        return jdbc.queryForObject("SELECT COUNT(*) FROM delivery_publish_attempt WHERE tenant_id=? AND plan_id=? AND status IN ('QUEUED','SENDING','UNCERTAIN','CREATED')",
                Integer.class, key.tenant, key.id) > 0;
    }
    private List<Draft> drafts(Key key, boolean lock) {
        return jdbc.query("SELECT status,qc_plan_id,source,created_at FROM ad_plan WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE SKIP LOCKED" : ""),
                (row,n) -> new Draft(row.getString(1),row.getString(2),row.getString(3),row.getTimestamp(4).toInstant()),key.tenant,key.id);
    }
    public void assertAvailable(String tenant, String id, Instant now) {
        var key = new Key(tenant,id); var rows = drafts(key,false);
        if (rows.isEmpty()) return; // Private published-plan working copies have no ad_plan row.
        var draft = rows.getFirst();
        if ("DELETED".equals(draft.status)) throw new BizException(1405,"计划已删除或草稿已过期，请返回计划列表");
        if (!localDraft(draft) || !DraftExpiryPolicy.expired(draft.createdAt,now)) return;
        var states=jdbc.query("SELECT state_json FROM delivery_creation WHERE tenant_id=? AND plan_id=?",(r,n)->state(r.getString(1)),tenant,id);
        if (!publishPending(key,states.isEmpty()?null:states.getFirst()))
            throw new BizException(1405,"草稿已超过7天有效期，已从计划列表移除，请重新创建计划");
    }
    public int expireDue(String tenant, Instant now, int limit) {
        var args=new ArrayList<Object>();args.add(Timestamp.from(now.minus(DraftExpiryPolicy.LIFETIME)));
        if (tenant!=null) args.add(tenant);
        var keys=jdbc.query("SELECT tenant_id,id FROM ad_plan WHERE status='DRAFT' AND qc_plan_id IS NULL AND source='LINGTOU' AND created_at<=?"
                +(tenant==null?"":" AND tenant_id=?")+" ORDER BY created_at,id LIMIT "+Math.max(1,Math.min(200,limit)),
                (row,n)->new Key(row.getString(1),row.getString(2)),args.toArray());
        int count=0;
        for (var key:keys) if (Boolean.TRUE.equals(tx.execute(status->expire(key,now)))) count++;
        return count;
    }
    private boolean expire(Key key, Instant now) {
        // Match the wizard's lock order. SKIP LOCKED avoids waiting on a concurrent edit/publish.
        var creations=jdbc.query("SELECT state_json,lease_until FROM delivery_creation WHERE tenant_id=? AND plan_id=? FOR UPDATE SKIP LOCKED",
                (row,n)->new Creation(row.getString(1),row.getTimestamp(2)==null?null:row.getTimestamp(2).toInstant()),key.tenant,key.id);
        if (creations.isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM delivery_creation WHERE tenant_id=? AND plan_id=?",Integer.class,key.tenant,key.id)>0) return false;
        var creation=creations.isEmpty()?null:creations.getFirst();
        if (creation!=null && creation.leaseUntil!=null && creation.leaseUntil.isAfter(now)) return false;
        var rows=drafts(key,true);if(rows.isEmpty())return false;
        var draft=rows.getFirst();if(!localDraft(draft)||!DraftExpiryPolicy.expired(draft.createdAt,now))return false;
        var saved=creation==null?null:state(creation.body);
        if(publishPending(key,saved))return false;
        if(jdbc.update("UPDATE ad_plan SET status='DELETED' WHERE tenant_id=? AND id=? AND status='DRAFT' AND qc_plan_id IS NULL",key.tenant,key.id)!=1)return false;
        jdbc.update("DELETE FROM ad_plan_creative WHERE ad_plan_id=?",key.id);
        if(saved instanceof ObjectNode body) {
            int revision=body.path("revision").asInt(1)+1;
            body.put("revision",revision);body.put("analysisStatus","CANCELLED");body.put("generationStatus","CANCELLED");
            jdbc.update("UPDATE delivery_creation SET revision=?,state_json=?,work_pending=0,lease_until=NULL,updated_at=? WHERE tenant_id=? AND plan_id=?",
                    revision,body.toString(),Timestamp.from(now),key.tenant,key.id);
        }
        return true;
    }
}
