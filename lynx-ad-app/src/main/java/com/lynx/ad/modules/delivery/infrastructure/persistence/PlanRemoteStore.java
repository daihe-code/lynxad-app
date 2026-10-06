package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Remote metadata is separate from confirmed AI creative bindings. Contains no credentials. */
@Repository
public class PlanRemoteStore {
    public record Remote(String planId, JsonNode payload, JsonNode detail, String guarantee, Instant syncedAt,
                         JsonNode todayReport, JsonNode yesterdayReport, java.time.LocalDate reportDate, Instant reportTs) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PlanRemoteStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public Map<String, Remote> list(TenantId tenant) {
        Map<String, Remote> result = new HashMap<>();
        jdbc.query("SELECT plan_id,payload,detail,guarantee,synced_at,today_report,yesterday_report,report_date,report_ts FROM qc_plan_metadata WHERE tenant_id=?",
                rs -> { result.put(rs.getString(1), new Remote(rs.getString(1), parse(rs.getString(2)),
                        parse(rs.getString(3)), rs.getString(4), rs.getTimestamp(5).toInstant(), parse(rs.getString(6)),
                        parse(rs.getString(7)), rs.getDate(8)==null?null:rs.getDate(8).toLocalDate(),
                        rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant())); }, tenant.value());
        return result;
    }
    public void save(TenantId tenant, AccountId account, String qcId, String planId, JsonNode payload) {
        int changed = jdbc.update("UPDATE qc_plan_metadata SET payload=?,detail=NULL,synced_at=? WHERE tenant_id=? AND oauth_account_id=? AND qc_plan_id=?",
                payload.toString(), java.sql.Timestamp.from(Instant.now()), tenant.value(), account.value(), qcId);
        if (changed == 0) jdbc.update("INSERT INTO qc_plan_metadata(plan_id,tenant_id,oauth_account_id,qc_plan_id,payload,synced_at) VALUES(?,?,?,?,?,?)",
                planId, tenant.value(), account.value(), qcId, payload.toString(), java.sql.Timestamp.from(Instant.now()));
    }
    public void detail(TenantId tenant, String planId, JsonNode detail) {
        jdbc.update("UPDATE qc_plan_metadata SET detail=? WHERE tenant_id=? AND plan_id=?", detail.toString(), tenant.value(), planId);
    }
    public void guarantee(TenantId tenant, String planId, String status) {
        jdbc.update("UPDATE qc_plan_metadata SET guarantee=? WHERE tenant_id=? AND plan_id=?", status, tenant.value(), planId);
    }
    public void report(TenantId tenant, String planId, JsonNode today, JsonNode yesterday, java.time.LocalDate day, Instant ts) {
        jdbc.update("UPDATE qc_plan_metadata SET today_report=?,yesterday_report=?,report_date=?,report_ts=? WHERE tenant_id=? AND plan_id=?",
                today==null?null:today.toString(), yesterday==null?null:yesterday.toString(), java.sql.Date.valueOf(day),
                java.sql.Timestamp.from(ts), tenant.value(), planId);
    }
    public Optional<String> findId(TenantId tenant, AccountId account, String qcId) {
        return jdbc.queryForList("SELECT plan_id FROM qc_plan_metadata WHERE tenant_id=? AND oauth_account_id=? AND qc_plan_id=?",
                String.class, tenant.value(), account.value(), qcId).stream().findFirst();
    }
    private JsonNode parse(String value) {
        if (value == null) return null;
        try { return json.readTree(value); } catch (Exception ex) { throw new IllegalStateException("千川缓存解析失败", ex); }
    }
}
