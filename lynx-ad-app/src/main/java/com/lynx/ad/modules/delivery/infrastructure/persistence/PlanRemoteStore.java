package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Remote metadata is separate from confirmed AI creative bindings. Contains no credentials. */
@Repository
public class PlanRemoteStore {
    public record Remote(String planId, JsonNode payload, JsonNode detail, String guarantee, Instant syncedAt,
                         JsonNode todayReport, JsonNode yesterdayReport, LocalDate reportDate, Instant reportTs,
                         JsonNode lastReport,LocalDate lastReportDate,Instant lastReportTs) {
        public Remote(String planId,JsonNode payload,JsonNode detail,String guarantee,Instant syncedAt,
                      JsonNode todayReport,JsonNode yesterdayReport,LocalDate reportDate,Instant reportTs) {
            this(planId,payload,detail,guarantee,syncedAt,todayReport,yesterdayReport,reportDate,reportTs,null,null,null);
        }
    }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private PlanInsightsStore insights;
    @org.springframework.beans.factory.annotation.Autowired
    public void insightsStore(PlanInsightsStore value) { insights=value; }
    public PlanRemoteStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public Map<String, Remote> list(TenantId tenant) {
        Map<String, Remote> result = new HashMap<>();
        jdbc.query("SELECT plan_id,payload,detail,guarantee,synced_at,today_report,yesterday_report,report_date,report_ts,last_report,last_report_date,last_report_ts FROM qc_plan_metadata WHERE tenant_id=?",
                rs -> { result.put(rs.getString(1), new Remote(rs.getString(1), parse(rs.getString(2)),
                        parse(rs.getString(3)), rs.getString(4), rs.getTimestamp(5).toInstant(), parse(rs.getString(6)),
                        parse(rs.getString(7)), rs.getDate(8)==null?null:rs.getDate(8).toLocalDate(),
                        rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),parse(rs.getString(10)),
                        rs.getDate(11)==null?null:rs.getDate(11).toLocalDate(),rs.getTimestamp(12)==null?null:rs.getTimestamp(12).toInstant())); }, tenant.value());
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
    /** Only after complete group membership readback. Refresh mutable display metadata, not audit attempts. */
    public void verifiedContent(TenantId tenant,String planId,JsonNode detail,List<com.lynx.ad.modules.delivery.domain.model.CreationState.Product> products){
        var rows=jdbc.query("SELECT payload FROM qc_plan_metadata WHERE tenant_id=? AND plan_id=?",(r,n)->parse(r.getString(1)),tenant.value(),planId);
        for(var saved:rows){var payload=saved!=null&&saved.isObject()?(com.fasterxml.jackson.databind.node.ObjectNode)saved.deepCopy():json.createObjectNode();payload.set("multi_product_creative_list",detail.path("multi_product_creative_list").deepCopy());var infos=payload.putArray("product_info");var ids=payload.putArray("product_ids");for(var p:products){ids.add(new java.math.BigInteger(p.qcProductId()));var info=infos.addObject().put("product_id",p.qcProductId()).put("product_name",p.name());if(p.imageUrl()!=null)info.put("product_image",p.imageUrl());}jdbc.update("UPDATE qc_plan_metadata SET payload=?,detail=?,synced_at=? WHERE tenant_id=? AND plan_id=?",payload.toString(),detail.toString(),java.sql.Timestamp.from(Instant.now()),tenant.value(),planId);}
    }
    /** Called only after native membership verification, inside the product-change transaction. */
    public void removeProduct(TenantId tenant,String planId,String productId) {
        var rows=jdbc.query("SELECT payload,detail FROM qc_plan_metadata WHERE tenant_id=? AND plan_id=? FOR UPDATE",(r,n)->new JsonNode[]{parse(r.getString(1)),parse(r.getString(2))},tenant.value(),planId);
        for(var row:rows) {
            for(var node:row)if(node!=null && node.isObject())for(String field:List.of("product_info","product_infos","multi_product_creative_list","product_ids")) {
                if(node.path(field).isArray()) {
                    var kept=json.createArrayNode();for(var item:node.path(field))if(!productId.equals(item.isObject()?item.path("product_id").asText():item.asText()))kept.add(item);
                    ((com.fasterxml.jackson.databind.node.ObjectNode)node).set(field,kept);
                }
            }
            jdbc.update("UPDATE qc_plan_metadata SET payload=?,detail=? WHERE tenant_id=? AND plan_id=?",row[0]==null?null:row[0].toString(),row[1]==null?null:row[1].toString(),tenant.value(),planId);
        }
    }
    public void guarantee(TenantId tenant, String planId, String status) {
        jdbc.update("UPDATE qc_plan_metadata SET guarantee=? WHERE tenant_id=? AND plan_id=?", status, tenant.value(), planId);
        if(insights!=null)insights.observeGuarantee(tenant,planId,status,null,null);
    }
    public void report(TenantId tenant, String planId, JsonNode today, JsonNode yesterday, java.time.LocalDate day, Instant ts) {
        // A successful empty daily report must not erase a paused plan's last available statistics.
        JsonNode last=validReport(today)?today:validReport(yesterday)?yesterday:null;
        LocalDate lastDay=validReport(today)?day:day.minusDays(1);
        if(last!=null)jdbc.update("UPDATE qc_plan_metadata SET last_report=?,last_report_date=?,last_report_ts=? WHERE tenant_id=? AND plan_id=? AND (last_report_date IS NULL OR last_report_date<? OR (last_report_date=? AND (last_report_ts IS NULL OR last_report_ts<=?)))",
                last.toString(),java.sql.Date.valueOf(lastDay),java.sql.Timestamp.from(ts),tenant.value(),planId,
                java.sql.Date.valueOf(lastDay),java.sql.Date.valueOf(lastDay),java.sql.Timestamp.from(ts));
        jdbc.update("UPDATE qc_plan_metadata SET today_report=?,yesterday_report=?,report_date=?,report_ts=? WHERE tenant_id=? AND plan_id=?",
                today==null?null:today.toString(), yesterday==null?null:yesterday.toString(), java.sql.Date.valueOf(day),
                java.sql.Timestamp.from(ts), tenant.value(), planId);
    }
    public static boolean validReport(JsonNode report) {
        if(report==null || !report.isObject())return false;
        for(String key:List.of("stat_cost","total_pay_order_count_for_roi2","total_pay_order_gmv_include_coupon_for_roi2","total_prepay_and_pay_order_roi2"))
            if(com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping.number(report,key)!=null)return true;
        return false;
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
