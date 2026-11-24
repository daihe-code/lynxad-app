package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.service.PlanTransferNames;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore.Attempt;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlanTransferRebuildStore {
    public record Rebuild(String account, String sourceQcId, JsonNode sourcePayload, JsonNode sourceDetail, String targetName,
                          String targetPlatformName, String restorePlatformName, String restoreQcId) {}
    public record History(String qcPlanId, Instant archivedAt, LocalDate statisticsStartDate, LocalDate statisticsDate,
                          BigDecimal costYuan, BigDecimal gmvYuan, BigDecimal roi, BigDecimal orders) {}
    public record ArchivedReport(String qcPlanId, Instant archivedAt, JsonNode snapshot) {}
    public record SyncGuard(Set<String> ids, Set<String> names) {
        public boolean excludes(String id, String name) { return ids.contains(id) || names.contains(name); }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PlanTransferRebuildStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void save(Attempt attempt, Rebuild rebuild) {
        jdbc.update("""
                INSERT INTO delivery_transfer_rebuild
                (tenant_id,request_key,oauth_account_id,source_qc_id,source_payload,source_detail,target_name,target_platform_name,restore_platform_name)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, attempt.tenant(), attempt.key(), rebuild.account(), rebuild.sourceQcId(), rebuild.sourcePayload().toString(),
                rebuild.sourceDetail().toString(), rebuild.targetName(), rebuild.targetPlatformName(), rebuild.restorePlatformName());
    }

    public Optional<Rebuild> find(TenantId tenant, String key) {
        return jdbc.query("""
                SELECT * FROM delivery_transfer_rebuild WHERE tenant_id=? AND request_key=?
                """,(r,n)->new Rebuild(r.getString("oauth_account_id"),r.getString("source_qc_id"),
                parse(r.getString("source_payload")),parse(r.getString("source_detail")),r.getString("target_name"),r.getString("target_platform_name"),
                r.getString("restore_platform_name"),r.getString("restore_qc_id")),tenant.value(),key).stream().findFirst();
    }

    public boolean hasSnapshot(TenantId tenant, String planId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM delivery_transfer_history WHERE tenant_id=? AND plan_id=?)
                """,Boolean.class,tenant.value(),planId));
    }

    public Optional<JsonNode> savedPayload(TenantId tenant, String planId) {
        return jdbc.query("""
                SELECT snapshot FROM delivery_transfer_history WHERE tenant_id=? AND plan_id=?
                ORDER BY archived_at DESC LIMIT 1
                """,(r,n)->parse(r.getString(1)).path("createPayload"),tenant.value(),planId).stream().findFirst();
    }

    public Map<String,List<ArchivedReport>> reports(TenantId tenant, Set<String> planIds) {
        if(planIds.isEmpty())return Map.of();
        var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        Map<String,List<ArchivedReport>> reports=new HashMap<>();
        named.query("""
                SELECT plan_id,qc_plan_id,archived_at,snapshot FROM delivery_transfer_history
                WHERE tenant_id=:tenant AND plan_id IN (:plans) ORDER BY archived_at
                """,Map.of("tenant",tenant.value(),"plans",planIds),r->{
            reports.computeIfAbsent(r.getString("plan_id"),id->new ArrayList<>()).add(
                    new ArchivedReport(r.getString("qc_plan_id"),r.getTimestamp("archived_at").toInstant(),parse(r.getString("snapshot"))));
        });
        return reports;
    }

    public Optional<JsonNode> submittedPayload(TenantId tenant,String planId,String qcId) {
        return jdbc.query("""
                SELECT a.payload_json FROM delivery_transfer_attempt a
                  WHERE a.tenant_id=? AND a.target_plan_id=? AND a.qc_plan_id=? AND a.status='SUCCEEDED'
                UNION ALL
                SELECT r.source_payload FROM delivery_transfer_rebuild r JOIN delivery_transfer_attempt a
                  ON a.tenant_id=r.tenant_id AND a.request_key=r.request_key
                  WHERE r.tenant_id=? AND a.source_plan_id=? AND r.restore_qc_id=? AND a.status='FAILED'
                """,(r,n)->parse(r.getString(1)),tenant.value(),planId,qcId,tenant.value(),planId,qcId).stream().findFirst();
    }

    public void restoreReceipt(TenantId tenant, String key, String qcId) {
        jdbc.update("UPDATE delivery_transfer_rebuild SET restore_qc_id=? WHERE tenant_id=? AND request_key=?",qcId,tenant.value(),key);
    }

    public void saveReport(TenantId tenant,String key,LocalDate start,LocalDate end,
                           com.lynx.ad.modules.delivery.domain.port.AdManagementPort.InsightReport report) {
        var data=json.createObjectNode().put("start",start.toString()).put("end",end.toString());
        data.set("report",json.valueToTree(report));
        jdbc.update("UPDATE delivery_transfer_rebuild SET source_report=? WHERE tenant_id=? AND request_key=?",
                data.toString(),tenant.value(),key);
    }

    public void archive(Attempt attempt, Rebuild rebuild, PlanRemoteStore.Remote metrics) {
        var snapshot = json.createObjectNode();
        snapshot.set("createPayload",rebuild.sourcePayload());
        snapshot.set("detail",rebuild.sourceDetail());
        var report=jdbc.queryForList("SELECT source_report FROM delivery_transfer_rebuild WHERE tenant_id=? AND request_key=?",
                String.class,attempt.tenant(),attempt.key()).getFirst();
        if(report!=null)snapshot.set("historyReport",parse(report));
        if(metrics!=null && metrics.lastReportDate()!=null) snapshot.put("lastReportDate",metrics.lastReportDate().toString());
        if(metrics!=null && metrics.reportDate()!=null) snapshot.put("reportDate",metrics.reportDate().toString());
        if(metrics!=null) snapshot.set("metadata",json.valueToTree(metrics));
        jdbc.update("""
                INSERT INTO delivery_transfer_history (tenant_id,oauth_account_id,qc_plan_id,plan_id,archived_at,snapshot)
                VALUES (?,?,?,?,?,?)
                """,attempt.tenant(),rebuild.account(),rebuild.sourceQcId(),attempt.source(),
                Timestamp.from(Instant.now()),snapshot.toString());
    }

    public List<History> history(TenantId tenant, String planId) {
        return jdbc.query("""
                SELECT qc_plan_id,archived_at,snapshot FROM delivery_transfer_history
                WHERE tenant_id=? AND plan_id=? ORDER BY archived_at DESC
                """,(r,n)->{
            var snapshot=parse(r.getString("snapshot"));
            var metadata=snapshot.path("metadata");
            var report=metadata.path("lastReport");
            var date=snapshot.path("lastReportDate");
            var full=snapshot.path("historyReport");
            var summary=full.path("report").path("summary").path("metrics");
            if(PlanRemoteStore.validReport(summary))
                return new History(r.getString("qc_plan_id"),r.getTimestamp("archived_at").toInstant(),
                        LocalDate.parse(full.path("start").asText()),LocalDate.parse(full.path("end").asText()),
                        metric(summary,"stat_cost"),metric(summary,"total_pay_order_gmv_include_coupon_for_roi2"),
                        metric(summary,"total_prepay_and_pay_order_roi2"),metric(summary,"total_pay_order_count_for_roi2"));
            if(!report.isObject() && metadata.path("todayReport").isObject()) {
                report=metadata.path("todayReport");
                date=snapshot.path("reportDate");
            }
            return new History(r.getString("qc_plan_id"),r.getTimestamp("archived_at").toInstant(),null,
                    date.isTextual()?LocalDate.parse(date.asText()):null,metric(report,"stat_cost"),
                    metric(report,"total_pay_order_gmv_include_coupon_for_roi2"),
                    metric(report,"total_prepay_and_pay_order_roi2"),metric(report,"total_pay_order_count_for_roi2"));
        },tenant.value(),planId);
    }

    public SyncGuard syncGuard(TenantId tenant, AccountId account) {
        Set<String> names=new HashSet<>();
        Set<String> ids=new HashSet<>(jdbc.queryForList("""
                SELECT qc_plan_id FROM delivery_transfer_history WHERE tenant_id=? AND oauth_account_id=?
                """,String.class,tenant.value(),account.value()));
        jdbc.query("""
                SELECT r.source_qc_id,r.target_platform_name,r.restore_platform_name,a.qc_plan_id,r.restore_qc_id
                FROM delivery_transfer_rebuild r JOIN delivery_transfer_attempt a
                  ON a.tenant_id=r.tenant_id AND a.request_key=r.request_key
                WHERE r.tenant_id=? AND r.oauth_account_id=? AND a.status IN ('RUNNING','UNCERTAIN')
                """,r->{
            ids.add(r.getString(1));
            names.add(r.getString(2));
            names.add(r.getString(3));
            if(r.getString(4)!=null) ids.add(r.getString(4));
            if(r.getString(5)!=null) ids.add(r.getString(5));
        },tenant.value(),account.value());
        return new SyncGuard(ids,names);
    }

    public boolean excludesCurrentRow(TenantId tenant,AccountId account,String qcId,String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM delivery_transfer_history WHERE tenant_id=? AND oauth_account_id=? AND qc_plan_id=?)
                  OR EXISTS(SELECT 1 FROM delivery_transfer_rebuild r JOIN delivery_transfer_attempt a
                      ON a.tenant_id=r.tenant_id AND a.request_key=r.request_key
                    WHERE r.tenant_id=? AND r.oauth_account_id=? AND a.status IN ('RUNNING','UNCERTAIN')
                      AND (? IN (r.source_qc_id,a.qc_plan_id,r.restore_qc_id)
                          OR ? IN (r.target_platform_name,r.restore_platform_name)))
                """,Boolean.class,tenant.value(),account.value(),qcId,tenant.value(),account.value(),qcId,name));
    }

    public String displayName(AdPlan plan) {
        String name = plan.name().text();
        if (!PlanTransferNames.mayNeedRecovery(name)) return name;
        String planId = plan.id().value();
        String qcId = plan.qcPlanId().map(QcPlanId::value).orElse(null);
        if (qcId == null && plan.transferArchived()) {
            qcId = jdbc.queryForList("""
                    SELECT qc_plan_id FROM delivery_transfer_history
                    WHERE tenant_id=? AND plan_id=? ORDER BY archived_at DESC LIMIT 1
                    """, String.class, plan.tenantId().value(), planId).stream().findFirst().orElse(null);
        }
        return qcId == null ? name : logicalName(plan.tenantId(), planId, qcId, name);
    }

    public String logicalName(TenantId tenant, String planId, String qcId, String platformName) {
        if (!PlanTransferNames.mayNeedRecovery(platformName)) return platformName;
        record NameReceipt(String target, String source, String sourceQcId, String sourcePlanId, String request) {}
        var names = jdbc.query("""
                SELECT r.target_name,r.source_payload,r.source_qc_id,a.source_plan_id,a.request_json
                FROM delivery_transfer_rebuild r JOIN delivery_transfer_attempt a
                  ON a.tenant_id=r.tenant_id AND a.request_key=r.request_key
                WHERE r.tenant_id=? AND a.target_plan_id=? AND a.qc_plan_id=? AND a.status='SUCCEEDED'
                  AND (r.target_platform_name=? OR r.target_name=?)
                """, (r, n) -> new NameReceipt(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)),
                tenant.value(), planId, qcId, platformName, platformName);
        if (names.isEmpty()) return restoredName(tenant, planId, qcId, platformName);
        var row = names.getFirst();
        String source = parse(row.source()).path("name").asText("");
        if (!parse(row.request()).hasNonNull("name")
                && !source.isBlank() && PlanTransferNames.isLegacyDefault(source, row.target())) {
            String original = restoredName(tenant, row.sourcePlanId(), row.sourceQcId(), source);
            return PlanTransferNames.defaultName(original);
        }
        return row.target();
    }

    private String restoredName(TenantId tenant, String planId, String qcId, String platformName) {
        if (!PlanTransferNames.mayNeedRecovery(platformName)) return platformName;
        return jdbc.query("""
                SELECT r.source_payload FROM delivery_transfer_rebuild r JOIN delivery_transfer_attempt a
                  ON a.tenant_id=r.tenant_id AND a.request_key=r.request_key
                WHERE r.tenant_id=? AND a.source_plan_id=? AND r.restore_qc_id=? AND r.restore_platform_name=?
                  AND a.status='FAILED'
                """,(r,n)->parse(r.getString(1)).path("name").asText(platformName),
                tenant.value(),planId,qcId,platformName).stream().findFirst().orElse(platformName);
    }

    private static BigDecimal metric(JsonNode report,String key) {
        return com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping.number(report,key);
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch(java.io.IOException e) { throw new IllegalStateException("无法读取转投快照",e); }
    }
}
