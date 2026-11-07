package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.application.OptimizationPolicy;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OptimizationStore {
    public record State(String tenant,String plan,long version,boolean enabled,OptimizationPolicy policy,Instant lastCheck,Instant lastSuccess,String error,JsonNode result,
            String holdReason,JsonNode baseline,Instant lastAction,String autoPausedReason,Instant autoPausedAt) {
        public State {
            if(result instanceof com.fasterxml.jackson.databind.node.ObjectNode && result.path("recommendations").isArray()){
                var copy=(com.fasterxml.jackson.databind.node.ObjectNode)result.deepCopy();var visible=copy.putArray("recommendations");
                for(var item:result.path("recommendations"))if(!"PRODUCT_ADD".equals(item.path("action").path("kind").asText()) && !"PRODUCT_ADD_REVIEW".equals(item.path("type").asText()))visible.add(item.deepCopy());
                result=copy;
            }
        }
        public State(String tenant,String plan,long version,boolean enabled,OptimizationPolicy policy,Instant lastCheck,Instant lastSuccess,String error,JsonNode result){this(tenant,plan,version,enabled,policy,lastCheck,lastSuccess,error,result,null,null,null,null,null);}
    }
    public record Pending(String id,long version,String payload,Instant created) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private OptimizationAutomationStore automationStore;
    private OptimizationRuleMetricsStore ruleMetrics;
    @org.springframework.beans.factory.annotation.Autowired public void ruleMetrics(OptimizationRuleMetricsStore value){ruleMetrics=value;}
    @org.springframework.beans.factory.annotation.Autowired public void automationStore(OptimizationAutomationStore value){automationStore=value;}
    public OptimizationStore(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager) {this.jdbc=jdbc;this.json=json;tx=new TransactionTemplate(manager);}
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Invalid monitoring payload",ex);}}
    private Instant time(ResultSet row,String field) throws SQLException {var v=row.getTimestamp(field);return v==null?null:v.toInstant();}
    private State row(ResultSet r,int n) throws SQLException {
        try {return new State(r.getString("tenant_id"),r.getString("plan_id"),r.getLong("policy_version"),r.getBoolean("enabled"),
            json.readValue(r.getString("config_json"),OptimizationPolicy.class),time(r,"last_check_at"),time(r,"last_success_at"),r.getString("last_error"),
            r.getString("result_json")==null?null:json.readTree(r.getString("result_json")),r.getString("hold_reason"),
            r.getString("baseline_json")==null?null:json.readTree(r.getString("baseline_json")),time(r,"last_action_at"),r.getString("auto_paused_reason"),time(r,"auto_paused_at"));}
        catch(SQLException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Invalid saved monitoring state",ex);}
    }
    public Optional<State> get(TenantId tenant,PlanId id) {return jdbc.query("SELECT * FROM optimization_policy WHERE tenant_id=? AND plan_id=?",this::row,tenant.value(),id.value()).stream().findFirst();}
    public List<State> forTenant(TenantId tenant){return jdbc.query("SELECT * FROM optimization_policy WHERE tenant_id=?",this::row,tenant.value());}
    public List<State> forPlans(TenantId tenant,List<String> ids){
        if(ids.isEmpty())return List.of();
        var args=new ArrayList<Object>();args.add(tenant.value());args.addAll(ids);
        return jdbc.query("SELECT * FROM optimization_policy WHERE tenant_id=? AND plan_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",this::row,args.toArray());
    }
    public List<State> due(Instant now) {return jdbc.query("SELECT * FROM optimization_policy WHERE enabled=TRUE AND next_check_at<=? AND (lease_until IS NULL OR lease_until<?) ORDER BY next_check_at LIMIT 50",this::row,Timestamp.from(now),Timestamp.from(now));}
    public List<OptimizationPolicy.Economics> economics(AdPlan plan) {
        return jdbc.query("SELECT * FROM product_economics_profile WHERE tenant_id=? AND account_id=? AND aweme_id=?",
            (r,n)->new OptimizationPolicy.Economics(r.getString("product_id"),r.getBigDecimal("contribution_margin_rate"),r.getBigDecimal("refund_reserve_rate"),r.getBoolean("verified")),
            plan.tenantId().value(),plan.accountId().value(),plan.awemeId().value());
    }
    public void save(AdPlan plan,OptimizationPolicy policy) {
        save(plan,policy,get(plan.tenantId(),plan.id()).map(State::version).orElse(0L));
    }
    public void save(AdPlan plan,OptimizationPolicy policy,long expectedVersion) {
        save(plan,policy,expectedVersion,null,null);
    }
    public void resumeRules(AdPlan plan,State paused,JsonNode report,JsonNode facts) {
        var policy=paused.policy();
        var resumed=new OptimizationPolicy(true,policy.mode(),policy.dailySpendLimit(),policy.maxLoss(),
            policy.minSpend(),policy.minOrders(),policy.persistenceWindows(),policy.conversionDelayMinutes(),
            policy.economics(),policy.automation(),policy.tactics(),policy.creativeGeneration(),policy.rules());
        save(plan,resumed,paused.version(),report,facts);
    }
    private void save(AdPlan plan,OptimizationPolicy policy,long expectedVersion,JsonNode resumeReport,JsonNode resumeFacts) {
        tx.executeWithoutResult(s->{
            // A tenant row serializes first inserts and cross-plan shared economics changes.
            if(jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE",plan.tenantId().value()).isEmpty())throw new BizException(1802,"租户不存在");
            var previous=jdbc.query("SELECT * FROM optimization_policy WHERE tenant_id=? AND plan_id=? FOR UPDATE",
                this::row,plan.tenantId().value(),plan.id().value()).stream().findFirst();
            long version=previous.map(State::version).orElse(0L);
            if(version!=expectedVersion)throw new BizException(1804,"盯盘设置已被更新，请重新加载设置后保存");
            if(policy.enabled() && resumeReport==null
                && previous.filter(state->!state.enabled() && state.policy().rules()!=null).isPresent())
                throw new BizException(1803,"请先核对当天消耗，再确认恢复盯盘优化");
            var now=Timestamp.from(Instant.now());
            for(var e:policy.economics()) {
                int changed=jdbc.update("UPDATE product_economics_profile SET contribution_margin_rate=?,refund_reserve_rate=?,verified=?,updated_at=? WHERE tenant_id=? AND account_id=? AND aweme_id=? AND product_id=?",
                    e.contributionMarginRate(),e.refundReserveRate(),e.verified(),now,plan.tenantId().value(),plan.accountId().value(),plan.awemeId().value(),e.productId());
                if(changed==0)jdbc.update("INSERT INTO product_economics_profile VALUES(?,?,?,?,?,?,?,?)",plan.tenantId().value(),plan.accountId().value(),plan.awemeId().value(),e.productId(),e.contributionMarginRate(),e.refundReserveRate(),e.verified(),now);
            }
            // Economics are shared across mode variants. Invalidate old advice and unsent revisions.
            if(policy.rules()!=null){
                jdbc.update("UPDATE optimization_policy SET policy_version=policy_version+1,result_json=NULL,last_error=NULL,next_check_at=? WHERE tenant_id=? AND plan_id=?",now,plan.tenantId().value(),plan.id().value());
                jdbc.update("UPDATE optimization_outbox SET status='CANCELLED',updated_at=? WHERE tenant_id=? AND plan_id=? AND status='PENDING'",now,plan.tenantId().value(),plan.id().value());
                jdbc.update("UPDATE optimization_event SET status='SKIPPED',error_message='盯盘规则已更新',finished_at=? WHERE tenant_id=? AND plan_id=? AND status='READY'",now,plan.tenantId().value(),plan.id().value());
            }else{
                jdbc.update("UPDATE optimization_policy SET policy_version=policy_version+1,result_json=NULL,last_error=NULL,next_check_at=? WHERE tenant_id=?",now,plan.tenantId().value());
                jdbc.update("UPDATE optimization_outbox SET status='CANCELLED',updated_at=? WHERE tenant_id=? AND status='PENDING'",now,plan.tenantId().value());
                jdbc.update("UPDATE optimization_event SET status='SKIPPED',error_message='盯盘设置或利润资料已更新',finished_at=? WHERE tenant_id=? AND status='READY'",now,plan.tenantId().value());
            }
            jdbc.update("UPDATE optimization_policy SET baseline_json=NULL,auto_paused_reason=NULL,auto_paused_at=NULL WHERE tenant_id=? AND plan_id=?",plan.tenantId().value(),plan.id().value());
            if(policy.rules()!=null || !"AUTO_LIMITED".equals(policy.mode()))jdbc.update("UPDATE optimization_policy SET hold_reason=NULL WHERE tenant_id=? AND plan_id=?",plan.tenantId().value(),plan.id().value());
            int changed=jdbc.update("UPDATE optimization_policy SET enabled=?,config_json=?,next_check_at=? WHERE tenant_id=? AND plan_id=?",policy.enabled(),encode(policy.withEconomics(List.of())),now,plan.tenantId().value(),plan.id().value());
            if(changed==0)jdbc.update("INSERT INTO optimization_policy(tenant_id,plan_id,policy_version,enabled,config_json,next_check_at) VALUES(?,?,1,?,?,?)",plan.tenantId().value(),plan.id().value(),policy.enabled(),encode(policy.withEconomics(List.of())),now);
            if(ruleMetrics!=null)ruleMetrics.reset(plan.tenantId().value(),plan.id().value());
            if(policy.rules()!=null && automationStore!=null){
                var prior=previous.orElse(null);
                boolean wasEnabled=prior!=null && prior.enabled();
                OptimizationAutomationStore.PauseSnapshot snapshot=null;
                if(wasEnabled && !policy.enabled()){
                    String tenant=plan.tenantId().value(), id=plan.id().value();
                    var report=prior.result();
                    snapshot=new OptimizationAutomationStore.PauseSnapshot(
                        ruleMetrics==null?0:ruleMetrics.seconds(tenant,id,false),
                        automationStore.ruleSummary(tenant,id,false),
                        report==null?null:report.get("daily_spend"),
                        report==null?null:report.path("fetched_at").asText(report.path("observed_at").asText(null)));
                    jdbc.update("""
                        UPDATE optimization_event SET status='RESOLVED',error_message='盯盘优化已暂停，原建议已失效',finished_at=?,updated_at=?
                        WHERE tenant_id=? AND plan_id=? AND status='RECOMMENDED'
                        """,now,now,tenant,id);
                }
                automationStore.ruleChanged(plan,policy,version+1,wasEnabled,snapshot,resumeReport!=null);
                if(resumeReport!=null){
                    jdbc.update("""
                        UPDATE optimization_policy SET result_json=?,last_check_at=?,last_success_at=?,baseline_json=?
                        WHERE tenant_id=? AND plan_id=?
                        """,encode(resumeReport),now,now,encode(resumeFacts),plan.tenantId().value(),plan.id().value());
                    automationStore.recordAdvice(get(plan.tenantId(),plan.id()).orElseThrow(),resumeReport);
                }
            }
        });
    }
    public boolean claim(State state,String owner,Instant now) {
        return jdbc.update("UPDATE optimization_policy SET lease_owner=?,lease_until=?,last_check_at=? WHERE tenant_id=? AND plan_id=? AND enabled=TRUE AND policy_version=? AND (lease_until IS NULL OR lease_until<?)",
            owner,Timestamp.from(now.plusSeconds(180)),Timestamp.from(now),state.tenant(),state.plan(),state.version(),Timestamp.from(now))==1;
    }
    public Optional<Pending> pending(State s) {
        return jdbc.query("SELECT * FROM optimization_outbox WHERE tenant_id=? AND plan_id=? AND status='PENDING' ORDER BY created_at LIMIT 1",
            (r,n)->new Pending(r.getString("id"),r.getLong("policy_version"),r.getString("payload_json"),time(r,"created_at")),s.tenant(),s.plan()).stream().findFirst();
    }
    public Pending enqueue(State state,String owner,Map<String,Object> payload) {
        return tx.execute(s->{
            assertLease(state,owner);String id=(String)payload.get("request_id");var now=Instant.now();String body=encode(payload);
            jdbc.update("INSERT INTO optimization_outbox VALUES(?,?,?,?,?,'PENDING',0,?,?)",id,state.tenant(),state.plan(),state.version(),body,Timestamp.from(now),Timestamp.from(now));
            return new Pending(id,state.version(),body,now);
        });
    }
    private void assertLease(State s,String owner) {
        var rows=jdbc.queryForList("SELECT plan_id FROM optimization_policy WHERE tenant_id=? AND plan_id=? AND lease_owner=? AND policy_version=? AND lease_until>? FOR UPDATE",s.tenant(),s.plan(),owner,s.version(),Timestamp.from(Instant.now()));
        if(rows.isEmpty())throw new BizException(1803,"盯盘设置或采集租约已变化，等待下轮采集");
    }
    public void complete(State state,String owner,Pending pending,JsonNode result) {
        tx.executeWithoutResult(s->{
            assertLease(state,owner);var now=Timestamp.from(Instant.now());
            jdbc.update("UPDATE optimization_outbox SET status='SENT',updated_at=? WHERE id=? AND tenant_id=? AND plan_id=? AND status='PENDING'",now,pending.id(),state.tenant(),state.plan());
            jdbc.update("UPDATE optimization_policy SET result_json=?,last_success_at=?,last_error=NULL WHERE tenant_id=? AND plan_id=? AND lease_owner=?",encode(result),now,state.tenant(),state.plan(),owner);
            if(ruleMetrics!=null)ruleMetrics.observed(state,result);
            if(automationStore!=null){automationStore.recordAdvice(state,result);if(state.error()!=null)automationStore.notice(state.tenant(),state.plan(),"OPTIMIZATION_RECOVERED","盯盘采集已恢复","已重新获取最新平台数据。");}
        });
    }
    public void discard(State s,String id) {jdbc.update("UPDATE optimization_outbox SET status='CANCELLED',updated_at=? WHERE id=? AND tenant_id=? AND plan_id=?",Timestamp.from(Instant.now()),id,s.tenant(),s.plan());}
    public void disableMissing(State state,String owner) {
        tx.executeWithoutResult(s->{
            if(jdbc.update("UPDATE optimization_policy SET enabled=FALSE WHERE tenant_id=? AND plan_id=? AND lease_owner=? AND policy_version=?",state.tenant(),state.plan(),owner,state.version())==1)
                jdbc.update("UPDATE optimization_outbox SET status='CANCELLED',updated_at=? WHERE tenant_id=? AND plan_id=? AND status='PENDING'",Timestamp.from(Instant.now()),state.tenant(),state.plan());
        });
    }
    public void release(State s,String owner,String error) {
        tx.executeWithoutResult(txStatus->{
        var now=Instant.now();String safe=error==null?null:error.substring(0,Math.min(500,error.length()));
        int changed=jdbc.update("UPDATE optimization_policy SET lease_owner=NULL,lease_until=NULL,next_check_at=CASE WHEN policy_version=? THEN ? ELSE next_check_at END,last_error=CASE WHEN policy_version=? THEN ? ELSE last_error END WHERE tenant_id=? AND plan_id=? AND lease_owner=?",
            s.version(),Timestamp.from(now.plusSeconds(s.policy().rules()!=null?60:error==null || s.error()!=null?300:60)),s.version(),safe,s.tenant(),s.plan(),owner);
        if(error!=null)jdbc.update("UPDATE optimization_outbox SET attempts=attempts+1,updated_at=? WHERE tenant_id=? AND plan_id=? AND status='PENDING'",Timestamp.from(now),s.tenant(),s.plan());
        if(changed==1 && error!=null && !Objects.equals(s.error(),safe) && automationStore!=null && get(new TenantId(s.tenant()),new PlanId(s.plan())).map(v->v.version()==s.version()).orElse(false))automationStore.notice(s.tenant(),s.plan(),"OPTIMIZATION_ERROR","盯盘采集异常",safe);
        });
    }
    public void prune() {jdbc.update("DELETE FROM optimization_outbox WHERE status<>'PENDING' AND updated_at<?",Timestamp.from(Instant.now().minus(Duration.ofDays(7))));}
}
