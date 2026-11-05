package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.exception.BizException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A one-shot, actor-scoped approval and positive-addition ledger, separate from persistent auto-pause consent. */
@Repository
public class OptimizationConfirmationStore {
    public record Confirmation(String eventId,String tenant,String plan,long version,String actor,JsonNode facts,JsonNode action,JsonNode report,Instant confirmedAt,Instant expiresAt,BigDecimal addition,String day,String status){}
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final TransactionTemplate tx;
    public OptimizationConfirmationStore(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager){this.jdbc=jdbc;this.json=json;tx=new TransactionTemplate(manager);}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode node(String text){try{return json.readTree(text);}catch(Exception e){throw new IllegalStateException(e);}}
    public Optional<Confirmation> find(String tenant,String event){return jdbc.query("SELECT * FROM optimization_confirmation WHERE tenant_id=? AND event_id=?",(r,n)->new Confirmation(r.getString("event_id"),r.getString("tenant_id"),r.getString("plan_id"),r.getLong("policy_version"),r.getString("actor_id"),node(r.getString("facts_json")),node(r.getString("action_json")),node(r.getString("report_json")),r.getTimestamp("confirmed_at").toInstant(),r.getTimestamp("expires_at").toInstant(),r.getBigDecimal("addition"),r.getString("day_key"),r.getString("status")),tenant,event).stream().findFirst();}
    public BigDecimal committed(String tenant,String plan,String day){return jdbc.queryForObject("SELECT COALESCE(SUM(addition),0) FROM optimization_confirmation WHERE tenant_id=? AND plan_id=? AND day_key=? AND status<>'NOT_SENT'",BigDecimal.class,tenant,plan,day);}
    public void approve(OptimizationAutomationStore.Event event,String actor,JsonNode facts,JsonNode action,JsonNode report,BigDecimal addition,BigDecimal dailyLimit,String owner){tx.executeWithoutResult(s->{
        if(addition==null||addition.signum()<0||addition.stripTrailingZeros().scale()>2||dailyLimit==null||dailyLimit.signum()<0)throw new BizException(1802,"新增投入金额不正确");
        if(find(event.tenant(),event.id()).isPresent())return;
        var now=Instant.now();String day=now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        if(jdbc.queryForList("SELECT account_id FROM optimization_account_guard WHERE tenant_id=? AND account_id=? AND lease_owner=? AND lease_until>? FOR UPDATE",event.tenant(),event.accountId(),owner,Timestamp.from(now)).isEmpty())throw new BizException(1803,"确认租约已变化");
        if(committed(event.tenant(),event.planId(),day).add(addition).compareTo(dailyLimit)>0)throw new BizException(1803,"本日新增预算承诺已达上限");
        if(jdbc.queryForList("SELECT plan_id FROM optimization_policy WHERE tenant_id=? AND plan_id=? AND policy_version=? AND enabled=TRUE AND hold_reason IS NULL FOR UPDATE",event.tenant(),event.planId(),event.version()).isEmpty())throw new BizException(1803,"盯盘规则已变化，请重新确认");
        if(jdbc.update("UPDATE optimization_event SET status='READY',proposal_json=?,report_json=?,updated_at=? WHERE tenant_id=? AND id=? AND status='RECOMMENDED' AND policy_version=?",encode(action),encode(report),Timestamp.from(now),event.tenant(),event.id(),event.version())!=1)throw new BizException(1803,"建议已变化，请刷新");
        jdbc.update("INSERT INTO optimization_confirmation(event_id,tenant_id,plan_id,policy_version,actor_id,facts_json,action_json,report_json,confirmed_at,expires_at,addition,day_key,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?, 'RESERVED')",event.id(),event.tenant(),event.planId(),event.version(),actor,encode(facts),encode(action),encode(report),Timestamp.from(now),Timestamp.from(now.plusSeconds(90)),addition,day);
    });}
    public void dispatched(Confirmation c){jdbc.update("UPDATE optimization_confirmation SET status='DISPATCHED' WHERE tenant_id=? AND event_id=? AND status='RESERVED'",c.tenant(),c.eventId());}
    public void notSent(Confirmation c){jdbc.update("UPDATE optimization_confirmation SET status='NOT_SENT' WHERE tenant_id=? AND event_id=? AND status='RESERVED'",c.tenant(),c.eventId());}
}
