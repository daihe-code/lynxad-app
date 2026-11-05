package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Only consecutive fresh, fully usable intervals count as effective monitoring. */
@Repository
public class OptimizationRuleMetricsStore {
    private final JdbcTemplate jdbc;
    public OptimizationRuleMetricsStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void observed(OptimizationStore.State state,JsonNode report){
        if(state.policy().rules()==null)return;
        var at=OffsetDateTime.parse(report.path("observed_at").asText()).toInstant();
        var day=at.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        boolean eligible=(!report.path("rule_version").asText().equals("merchant-rules-v2") || report.path("business_ready").asBoolean()) && "READY".equals(report.path("state").asText()) && report.hasNonNull("actual_net_roi") && !report.path("execution_pending").asBoolean() && state.holdReason()==null;
        var rows=jdbc.queryForList("SELECT last_observed_at,last_eligible FROM optimization_rule_day WHERE tenant_id=? AND plan_id=? AND day_key=? FOR UPDATE",state.tenant(),state.plan(),day);
        if(rows.isEmpty()){jdbc.update("INSERT INTO optimization_rule_day(tenant_id,plan_id,day_key,effective_seconds,last_observed_at,last_eligible) VALUES(?,?,?,0,?,?)",state.tenant(),state.plan(),day,Timestamp.from(at),eligible);return;}
        var row=rows.getFirst();var old=row.get("last_observed_at");var last=old instanceof Timestamp t?t.toInstant():null;
        if(last!=null && !at.isAfter(last))return;
        boolean prior=Boolean.TRUE.equals(row.get("last_eligible")) || row.get("last_eligible") instanceof Number n && n.intValue()==1;
        long seconds=last==null?0:Duration.between(last,at).getSeconds();
        long counted=eligible && prior && seconds>0 && seconds<=90?seconds:0;
        jdbc.update("UPDATE optimization_rule_day SET effective_seconds=effective_seconds+?,last_observed_at=?,last_eligible=? WHERE tenant_id=? AND plan_id=? AND day_key=?",counted,Timestamp.from(at),eligible,state.tenant(),state.plan(),day);
    }
    public void reset(String tenant,String plan){jdbc.update("UPDATE optimization_rule_day SET last_eligible=FALSE WHERE tenant_id=? AND plan_id=?",tenant,plan);}
    public long seconds(String tenant,String plan,boolean today){String sql="SELECT COALESCE(SUM(effective_seconds),0) FROM optimization_rule_day WHERE tenant_id=? AND plan_id=?";return today?jdbc.queryForObject(sql+" AND day_key=?",Long.class,tenant,plan,LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()):jdbc.queryForObject(sql,Long.class,tenant,plan);}
}
