package com.lynx.ad.modules.creative.infrastructure.persistence;

import com.fasterxml.jackson.databind.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Asset versions and removed bindings remain available as creative experiment history. */
@Repository
public class MaterialCreativeContextStore {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MaterialCreativeContextStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public record Entry(String id,String tenant,String plan,String account,String product,String material,String assetKey,
        String status,JsonNode body,JsonNode profile,boolean removed,String removalReason,int version,int attempts){}
    private JsonNode parse(String text){try{return text==null?null:json.readTree(text);}catch(Exception e){throw new IllegalStateException("Invalid creative context",e);}}
    private org.springframework.jdbc.core.RowMapper<Entry> mapper(){return (r,n)->new Entry(r.getString("id"),r.getString("tenant_id"),r.getString("plan_id"),r.getString("account_id"),r.getString("product_id"),r.getString("material_id"),r.getString("asset_key"),r.getString("status"),parse(r.getString("body_json")),parse(r.getString("profile_json")),r.getBoolean("removed"),r.getString("removal_reason"),r.getInt("request_version"),r.getInt("attempts"));}
    public Optional<Entry> get(String tenant,String id){return jdbc.query("SELECT * FROM material_creative_context WHERE tenant_id=? AND id=?",mapper(),tenant,id).stream().findFirst();}
    public Optional<Entry> reusable(String tenant,String product,String asset){return jdbc.query("SELECT * FROM material_creative_context WHERE tenant_id=? AND product_id=? AND asset_key=? AND status='READY' ORDER BY updated_at DESC LIMIT 1",mapper(),tenant,product,asset).stream().findFirst();}
    public void insert(Entry e){var now=Timestamp.from(Instant.now());try{jdbc.update("INSERT INTO material_creative_context(id,tenant_id,plan_id,account_id,product_id,material_id,asset_key,status,body_json,profile_json,next_attempt_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",e.id,e.tenant,e.plan,e.account,e.product,e.material,e.assetKey,e.status,e.body.toString(),e.profile==null?null:e.profile.toString(),now,now,now);}catch(DuplicateKeyException ignored){/* One immutable asset version per binding. */}}
    public List<Entry> pending(){return jdbc.query("SELECT * FROM material_creative_context WHERE status='PENDING' AND next_attempt_at<=? AND (lease_until IS NULL OR lease_until<?) ORDER BY created_at LIMIT 10",mapper(),Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));}
    public boolean claim(Entry e){var now=Instant.now();return jdbc.update("UPDATE material_creative_context SET lease_until=? WHERE tenant_id=? AND id=? AND status='PENDING' AND request_version=? AND (lease_until IS NULL OR lease_until<?)",Timestamp.from(now.plusSeconds(600)),e.tenant,e.id,e.version,Timestamp.from(now))==1;}
    public void finish(Entry e,String status,JsonNode profile){jdbc.update("UPDATE material_creative_context SET status=?,profile_json=?,lease_until=NULL,updated_at=? WHERE tenant_id=? AND id=? AND request_version=?",status,profile==null?null:profile.toString(),Timestamp.from(Instant.now()),e.tenant,e.id,e.version);}
    public void defer(Entry e){jdbc.update("UPDATE material_creative_context SET attempts=attempts+1,next_attempt_at=?,lease_until=NULL,updated_at=? WHERE tenant_id=? AND id=? AND request_version=?",Timestamp.from(Instant.now().plusSeconds(60)),Timestamp.from(Instant.now()),e.tenant,e.id,e.version);}
    public void assessment(String tenant,String account,String id,String product,JsonNode result){get(tenant,id).filter(e->e.account.equals(account)&&e.product.equals(product)).ifPresent(e->{var previous=e.body.path("assessment").path("observed_at").asText();if(!previous.isBlank()&&Instant.parse(previous).isAfter(Instant.parse(result.path("observed_at").asText())))return;var body=(com.fasterxml.jackson.databind.node.ObjectNode)e.body.deepCopy();body.set("assessment",result);jdbc.update("UPDATE material_creative_context SET body_json=?,updated_at=?,assessment_observed_at=? WHERE tenant_id=? AND account_id=? AND product_id=? AND id=? AND (assessment_observed_at IS NULL OR assessment_observed_at<=?)",body.toString(),Timestamp.from(Instant.now()),Timestamp.from(Instant.parse(result.path("observed_at").asText())),tenant,account,product,id,Timestamp.from(Instant.parse(result.path("observed_at").asText())));});}
    public void retry(String tenant,String id){jdbc.update("UPDATE material_creative_context SET status='PENDING',request_version=request_version+1,attempts=0,next_attempt_at=?,lease_until=NULL,updated_at=? WHERE tenant_id=? AND id=? AND status='FAILED'",Timestamp.from(Instant.now()),Timestamp.from(Instant.now()),tenant,id);}
    public void removed(String tenant,String plan,String material,String reason){jdbc.update("UPDATE material_creative_context SET removed=TRUE,removal_reason=?,updated_at=? WHERE tenant_id=? AND plan_id=? AND material_id=?",reason,Timestamp.from(Instant.now()),tenant,plan,material);}
    public List<Entry> history(String tenant,String account){return jdbc.query("SELECT * FROM material_creative_context WHERE tenant_id=? AND account_id=? ORDER BY created_at DESC LIMIT 200",mapper(),tenant,account);}
}
