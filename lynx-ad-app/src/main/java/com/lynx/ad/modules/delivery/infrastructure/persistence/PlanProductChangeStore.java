package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.lynx.ad.common.domain.Ids.TenantId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlanProductChangeStore {
    public record Change(String tenant,String key,String root,String source,String product,String members,String status,String error) {}
    private final JdbcTemplate jdbc;
    public PlanProductChangeStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<Change> get(TenantId tenant,String key) {
        return jdbc.query("SELECT * FROM delivery_product_change WHERE tenant_id=? AND request_key=?",(r,n)->new Change(tenant.value(),key,
                r.getString("root_plan_id"),r.getString("source_plan_id"),r.getString("product_id"),r.getString("members_json"),r.getString("status"),r.getString("error_message")),tenant.value(),key).stream().findFirst();
    }
    public void insert(Change c) {
        jdbc.update("INSERT INTO delivery_product_change(tenant_id,request_key,root_plan_id,source_plan_id,product_id,members_json,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                c.tenant(),c.key(),c.root(),c.source(),c.product(),c.members(),"PENDING",now(),now());
    }
    public boolean claim(Change c) {
        return jdbc.update("UPDATE delivery_product_change SET lease_until=?,updated_at=? WHERE tenant_id=? AND request_key=? AND status='PENDING' AND (lease_until IS NULL OR lease_until<?)",
                Timestamp.from(Instant.now().plusSeconds(600)),now(),c.tenant(),c.key(),now())==1;
    }
    public void release(Change c,String error) {
        jdbc.update("UPDATE delivery_product_change SET error_message=?,lease_until=NULL,updated_at=? WHERE tenant_id=? AND request_key=? AND status='PENDING'",error==null?null:error.substring(0,Math.min(1900,error.length())),now(),c.tenant(),c.key());
    }
    public void complete(Change c) {
        jdbc.update("UPDATE delivery_product_change SET status='DONE',error_message=NULL,lease_until=NULL,updated_at=? WHERE tenant_id=? AND request_key=? AND status='PENDING'",now(),c.tenant(),c.key());
    }
    private Timestamp now() { return Timestamp.from(Instant.now()); }
}
