package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable recommendation journal and account-wide write serialization. No network calls in transactions. */
@Repository
public class OptimizationAutomationStore {
    public record Event(String id,String tenant,String planId,String accountId,String planName,long version,String kind,String title,String evidence,
            JsonNode proposal,JsonNode report,String status,String error,JsonNode actual,Instant createdAt,Instant dispatchedAt,Instant finishedAt){}
    public record Page(List<Event> records,long total,int pageNum,int pageSize){}
    public record AccountLimit(String accountId,BigDecimal budgetLimit){}
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final TransactionTemplate tx;private final AdPlanRepository plans;
    public OptimizationAutomationStore(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager,AdPlanRepository plans){this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);this.plans=plans;}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Invalid execution record",e);}}
    private JsonNode node(String value){try{return value==null?null:json.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid execution JSON",e);}}
    private Instant time(ResultSet r,String name)throws SQLException{var value=r.getTimestamp(name);return value==null?null:value.toInstant();}
    private Event row(ResultSet r,int ignored)throws SQLException{return new Event(r.getString("id"),r.getString("tenant_id"),r.getString("plan_id"),r.getString("account_id"),r.getString("plan_name"),r.getLong("policy_version"),r.getString("kind"),r.getString("title"),r.getString("evidence"),node(r.getString("proposal_json")),node(r.getString("report_json")),r.getString("status"),r.getString("error_message"),node(r.getString("actual_json")),time(r,"created_at"),time(r,"dispatched_at"),time(r,"finished_at"));}
    public Optional<Event> event(String tenant,String id){return jdbc.query("SELECT * FROM optimization_event WHERE tenant_id=? AND id=?",this::row,tenant,id).stream().findFirst();}
    public List<Event> pending(){return jdbc.query("SELECT * FROM optimization_event WHERE status IN ('READY','EXECUTING','VERIFY_PENDING') ORDER BY created_at LIMIT 30",this::row);}
    public boolean unresolved(String tenant,String account){return jdbc.queryForObject("SELECT COUNT(*) FROM optimization_event WHERE tenant_id=? AND account_id=? AND status IN ('EXECUTING','VERIFY_PENDING','UNKNOWN') AND (kind IS NULL OR kind<>'MATERIAL_GENERATE')",Integer.class,tenant,account)>0;}
    public void recordAdvice(OptimizationStore.State state,JsonNode report){
        var plan=plans.findById(new TenantId(state.tenant()),new PlanId(state.plan())).orElseThrow(()->new BizException(1405,"计划不存在"));
        if(state.policy().rules()!=null && report.path("rule_version").asText().equals("merchant-rules-v2")){recordMerchantAdvice(state,report,plan);return;}
        for(var item:report.path("recommendations")){
            var proposal=item.path("action");if(proposal.isMissingNode() || proposal.isNull())proposal=null;
            String window="DAILY_LIMIT".equals(proposal==null?null:proposal.path("cause").asText()) || "NEXT_DAY".equals(proposal==null?null:proposal.path("cause").asText())?report.path("observed_at").asText().substring(0,10):report.path("window_end").asText();
            String fingerprint=hash(encode(List.of(state.tenant(),state.plan(),state.version(),item.path("type").asText(),proposal==null?"":proposal.toString(),window,report.path("rule_version").asText())));
            if(jdbc.queryForObject("SELECT COUNT(*) FROM optimization_event WHERE tenant_id=? AND fingerprint=?",Integer.class,state.tenant(),fingerprint)>0)continue;
            String id=UUID.randomUUID().toString(),kind=proposal==null?null:proposal.path("kind").asText();
            if(kind==null && state.policy().rules()!=null)kind="RULE_"+item.path("type").asText();
            boolean automatic=state.enabled() && "AUTO_LIMITED".equals(state.policy().mode()) && state.holdReason()==null && item.path("execution_allowed").asBoolean(false) && proposal!=null;
            String status=automatic?"READY":"RECOMMENDED",title=item.path("title").asText(),evidence=item.path("evidence").asText();
            jdbc.update("INSERT INTO optimization_event(id,tenant_id,plan_id,account_id,plan_name,fingerprint,policy_version,kind,title,evidence,proposal_json,report_json,status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,state.tenant(),state.plan(),plan.accountId().value(),plan.name().text(),fingerprint,state.version(),kind,title,evidence,proposal==null?null:encode(proposal),encode(report),status,Timestamp.from(Instant.now()));
            notice(state.tenant(),state.plan(),"OPTIMIZATION_ADVICE",automatic?"自动调控建议已进入执行检查":"新的盯盘建议",plan.name().text()+"："+title+"。"+evidence);
        }
    }
    private void recordMerchantAdvice(OptimizationStore.State state,JsonNode report,com.lynx.ad.modules.delivery.domain.model.AdPlan plan){
        var active=new HashSet<String>();var now=Timestamp.from(Instant.now());
        for(var item:report.path("recommendations")){
            String type=item.path("type").asText(),object=item.path("object_id").asText(plan.qcPlanId().map(v->v.value()).orElse(state.plan()));
            String family=type.startsWith("E4-")||type.equals("ROI_PROTECTION")?"EFFECT":type.startsWith("GROWTH")?"GROWTH":type;
            String scope=state.version()+":"+family+":"+item.path("object_type").asText("PLAN")+":"+object;active.add(scope);
            var open=jdbc.query("SELECT * FROM optimization_event WHERE tenant_id=? AND plan_id=? AND scope_key=? AND (status IN ('RECOMMENDED','READY','EXECUTING','VERIFY_PENDING','UNKNOWN') OR (status='SUCCEEDED' AND EXISTS (SELECT 1 FROM optimization_review r WHERE r.tenant_id=optimization_event.tenant_id AND r.event_id=optimization_event.id AND r.status IN ('TRACKING','OBSERVING','WAITING_DATA')))) ORDER BY created_at DESC LIMIT 1",this::row,state.tenant(),state.plan(),scope);
            if(!open.isEmpty()){
                var existing=open.getFirst();if(existing.status().equals("RECOMMENDED"))jdbc.update("UPDATE optimization_event SET title=?,evidence=?,report_json=?,proposal_json=?,updated_at=? WHERE tenant_id=? AND id=? AND status='RECOMMENDED'",item.path("title").asText(),item.path("evidence").asText(),encode(report),item.hasNonNull("action")?encode(item.path("action")):null,now,state.tenant(),existing.id());
                continue;
            }
            String id=UUID.randomUUID().toString(),kind=item.hasNonNull("action")?item.path("action").path("kind").asText():"RULE_"+(family.equals("EFFECT")?"EFFECT":family);
            boolean auto=item.path("execution_allowed").asBoolean()&&state.enabled()&&state.holdReason()==null&&state.policy().mode().equals("AUTO_LIMITED");
            jdbc.update("INSERT INTO optimization_event(id,tenant_id,plan_id,account_id,plan_name,fingerprint,policy_version,kind,title,evidence,proposal_json,report_json,status,created_at,scope_key,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,state.tenant(),state.plan(),plan.accountId().value(),plan.name().text(),id,state.version(),kind,item.path("title").asText(),item.path("evidence").asText(),item.hasNonNull("action")?encode(item.path("action")):null,encode(report),auto?"READY":"RECOMMENDED",now,scope,now);
            notice(state.tenant(),state.plan(),"OPTIMIZATION_ADVICE","新的盯盘建议",plan.name().text()+"："+item.path("title").asText());
        }
        // Missing/stale business evidence cannot close a previously observed business issue.
        if(report.path("complete").asBoolean())for(var existing:jdbc.query("SELECT * FROM optimization_event WHERE tenant_id=? AND plan_id=? AND policy_version=? AND status='RECOMMENDED' AND scope_key IS NOT NULL",this::row,state.tenant(),state.plan(),state.version())){
            var keys=jdbc.queryForList("SELECT scope_key FROM optimization_event WHERE tenant_id=? AND id=?",String.class,state.tenant(),existing.id());
            if(keys.isEmpty()||active.contains(keys.getFirst()))continue;
            boolean business=keys.getFirst().contains(":EFFECT:")||keys.getFirst().contains(":GROWTH:");
            if(business&&!report.path("diagnostics_ready").asBoolean())continue;
            jdbc.update("UPDATE optimization_event SET status='RESOLVED',finished_at=?,updated_at=?,error_message='最新可靠证据已不再支持原建议；未执行操作' WHERE tenant_id=? AND id=? AND status='RECOMMENDED'",now,now,state.tenant(),existing.id());
        }
    }
    public void ruleChanged(com.lynx.ad.modules.delivery.domain.model.AdPlan p,com.lynx.ad.modules.agentops.application.OptimizationPolicy policy,long version,boolean previouslyEnabled){
        String id=UUID.randomUUID().toString();var report=json.createObjectNode().put("rule_version","merchant-rules-v1");report.set("rules",json.valueToTree(policy.rules()));report.put("enabled",policy.enabled());
        String title=policy.enabled()?(previouslyEnabled?"盯盘规则已更新":"盯盘优化已开启"):(previouslyEnabled?"盯盘优化已关闭":"盯盘规则已保存");
        String body=policy.enabled()?"净成交ROI要求不低于 "+policy.rules().roiFloor().stripTrailingZeros().toPlainString()+"；当天消耗 ¥"+policy.rules().spendReminder().stripTrailingZeros().toPlainString()+" 时提醒，达到 ¥"+policy.rules().spendProtection().stripTrailingZeros().toPlainString()+" 时"+(policy.rules().protectionAction().equals("AUTO_PAUSE")?"按授权自动暂停本计划。":"提醒你确认暂停。")
                :"已关闭经营优化，已提交操作继续核对。计划投放状态保持不变。";
        jdbc.update("INSERT INTO optimization_event(id,tenant_id,plan_id,account_id,plan_name,fingerprint,policy_version,kind,title,evidence,report_json,status,created_at,finished_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,'SUCCEEDED',?,?)",id,p.tenantId().value(),p.id().value(),p.accountId().value(),p.name().text(),id,version,"RULES_CHANGE",title,body,encode(report),Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
        if(policy.enabled()||previouslyEnabled)notice(p.tenantId().value(),p.id().value(),"OPTIMIZATION_RULES",title,p.name().text()+"："+body);
    }
    public record RuleSummary(long found,long completed,long protectedCount){}
    public RuleSummary ruleSummary(String tenant,String plan,boolean today){
        String sql="SELECT COUNT(*) AS found, COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN 1 ELSE 0 END),0) AS completed, COALESCE(SUM(CASE WHEN status='SUCCEEDED' AND kind='PAUSE' THEN 1 ELSE 0 END),0) AS protected_count FROM optimization_event WHERE tenant_id=? AND plan_id=? AND (kind IS NULL OR kind NOT IN ('RULES_CHANGE','RULE_SPEND_REMINDER','RULE_GROWTH_REVIEW'))";
        var args=new ArrayList<Object>(List.of(tenant,plan));if(today){sql+=" AND created_at>=?";args.add(Timestamp.from(LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));}
        return jdbc.queryForObject(sql,(r,n)->new RuleSummary(r.getLong("found"),r.getLong("completed"),r.getLong("protected_count")),args.toArray());
    }
    public List<Event> rulePending(String tenant,String plan){return jdbc.query("SELECT * FROM optimization_event WHERE tenant_id=? AND plan_id=? AND status IN ('EXECUTING','VERIFY_PENDING','UNKNOWN') ORDER BY created_at DESC LIMIT 20",this::row,tenant,plan);}
    public List<Event> ruleActions(String tenant,String plan,long version){return jdbc.query("SELECT * FROM optimization_event WHERE tenant_id=? AND plan_id=? AND policy_version=? AND status IN ('RECOMMENDED','READY') ORDER BY created_at DESC LIMIT 30",this::row,tenant,plan,version);}
    public Page ruleHistory(String tenant,String plan,String category,int days,int page,int size){
        if(page<1 || size<1 || size>50 || days<1 || days>180 || !Set.of("ALL","RISK","GROWTH","RULES").contains(category))throw new BizException(1802,"工作记录筛选范围不正确");
        String sql=" FROM optimization_event WHERE tenant_id=? AND plan_id=? AND created_at>=?";var args=new ArrayList<Object>(List.of(tenant,plan,Timestamp.from(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(days-1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant())));
        sql+=switch(category){case "RULES"->" AND kind='RULES_CHANGE'";case "GROWTH"->" AND kind IN ('BUDGET','ROI','BOOST','MATERIAL_FOLLOW','MATERIAL_GENERATE','MATERIAL_ADD','RULE_GROWTH_REVIEW','RULE_GROWTH')";case "RISK"->" AND (kind IS NULL OR kind IN ('PAUSE','MATERIAL_REMOVE','PRODUCT_REMOVE','RULE_SPEND_REMINDER','RULE_ROI_PROTECTION','RULE_RUNAWAY_RISK','RULE_RUNAWAY_CHECK','RULE_EFFECT'))";default->"";};
        long count=jdbc.queryForObject("SELECT COUNT(*)"+sql,Long.class,args.toArray());args.add(size);args.add((long)(page-1)*size);
        return new Page(jdbc.query("SELECT *"+sql+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",this::row,args.toArray()),count,page,size);
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public void notice(String tenant,String plan,String type,String title,String body){jdbc.update("INSERT INTO notification(id,tenant_id,channel,title,body,biz_type,biz_id,sent_at) VALUES(?,?,'INBOX',?,?,?,?,?)",UUID.randomUUID().toString(),tenant,title,body,type,plan,Timestamp.from(Instant.now()));}
    private void ensureAccount(String tenant,String account){
        if(jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE",tenant).isEmpty())throw new BizException(1802,"租户不存在");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM optimization_account_guard WHERE tenant_id=? AND account_id=?",Integer.class,tenant,account)==0)jdbc.update("INSERT INTO optimization_account_guard(tenant_id,account_id) VALUES(?,?)",tenant,account);
    }
    public boolean claimAccount(String tenant,String account,String owner){return Boolean.TRUE.equals(tx.execute(s->{ensureAccount(tenant,account);var now=Instant.now();return jdbc.update("UPDATE optimization_account_guard SET lease_owner=?,lease_until=? WHERE tenant_id=? AND account_id=? AND (lease_until IS NULL OR lease_until<?)",owner,Timestamp.from(now.plusSeconds(180)),tenant,account,Timestamp.from(now))==1;}));}
    public void releaseAccount(String tenant,String account,String owner){jdbc.update("UPDATE optimization_account_guard SET lease_owner=NULL,lease_until=NULL WHERE tenant_id=? AND account_id=? AND lease_owner=?",tenant,account,owner);}
    public BigDecimal limit(String tenant,String account){var rows=jdbc.query("SELECT budget_limit FROM optimization_account_guard WHERE tenant_id=? AND account_id=?",(r,n)->r.getBigDecimal(1),tenant,account);return rows.isEmpty()?null:rows.getFirst();}
    public List<AccountLimit> limits(String tenant){return jdbc.query("SELECT account_id,budget_limit FROM optimization_account_guard WHERE tenant_id=?",(r,n)->new AccountLimit(r.getString(1),r.getBigDecimal(2)),tenant);}
    public List<Event> unreviewed(){return jdbc.query("SELECT e.* FROM optimization_event e LEFT JOIN optimization_review r ON r.tenant_id=e.tenant_id AND r.event_id=e.id WHERE r.id IS NULL AND e.status='SUCCEEDED' AND e.kind IN ('BUDGET','ROI','PAUSE','ENABLE','BOOST','MATERIAL_FOLLOW','MATERIAL_ADD','MATERIAL_REMOVE','PRODUCT_REMOVE') AND e.report_json LIKE '%merchant-rules-v2%' ORDER BY e.created_at LIMIT 20",this::row);}
    public void setLimit(String tenant,String account,BigDecimal limit){
        if(limit==null || limit.compareTo(new BigDecimal("50"))<0 || limit.compareTo(new BigDecimal("999999999"))>0 || limit.stripTrailingZeros().scale()>2)throw new BizException(1802,"账户总日预算上限须为50–999999999元，最多两位小数");
        tx.executeWithoutResult(s->{ensureAccount(tenant,account);if(jdbc.update("UPDATE optimization_account_guard SET budget_limit=? WHERE tenant_id=? AND account_id=? AND (lease_until IS NULL OR lease_until<?)",limit,tenant,account,Timestamp.from(Instant.now()))!=1)throw new BizException(1803,"账户操作处理中，请稍后调整上限");});
    }
    public void hold(String tenant,List<String> ids,String reason){tx.executeWithoutResult(s->{
        for(String id:ids){int changed=jdbc.update("UPDATE optimization_policy SET hold_reason=?,policy_version=policy_version+1,auto_paused_reason=NULL,auto_paused_at=NULL WHERE tenant_id=? AND plan_id=? AND hold_reason IS NULL",reason,tenant,id);
            jdbc.update("UPDATE optimization_event SET status='SKIPPED',error_message=?,finished_at=? WHERE tenant_id=? AND plan_id=? AND status='READY'",reason,Timestamp.from(Instant.now()),tenant,id);
            jdbc.update("UPDATE optimization_outbox SET status='CANCELLED',updated_at=? WHERE tenant_id=? AND plan_id=? AND status='PENDING'",Timestamp.from(Instant.now()),tenant,id);
            if(changed>0)notice(tenant,id,"OPTIMIZATION_TAKEOVER","自动调控已暂停",reason);
        }
    });}
    public void baseline(OptimizationStore.State state,JsonNode facts){jdbc.update("UPDATE optimization_policy SET baseline_json=? WHERE tenant_id=? AND plan_id=? AND policy_version=? AND baseline_json IS NULL",encode(facts),state.tenant(),state.plan(),state.version());}
    public int actionsToday(String tenant,String plan){var start=LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();return jdbc.queryForObject("SELECT COUNT(*) FROM optimization_event WHERE tenant_id=? AND plan_id=? AND dispatched_at>=? AND kind<>'PAUSE'",Integer.class,tenant,plan,Timestamp.from(start));}
    /** Reserve the full budget even for unknown/ended tasks; do not spend the same allowance twice. */
    public BigDecimal extraBudgetToday(String tenant,String plan){var start=LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();return jdbc.query("SELECT proposal_json FROM optimization_event WHERE tenant_id=? AND plan_id=? AND dispatched_at>=? AND kind IN ('BOOST','MATERIAL_FOLLOW')",(r,n)->new BigDecimal(node(r.getString(1)).path("budget").asText()),tenant,plan,Timestamp.from(start)).stream().reduce(BigDecimal.ZERO,BigDecimal::add);}
    public void rememberBefore(Event event,JsonNode facts){
        if(!event.report().path("rule_version").asText().equals("merchant-rules-v2"))return;
        var report=event.report().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)report).set("pre_action_facts",facts);
        if(jdbc.update("UPDATE optimization_event SET report_json=? WHERE tenant_id=? AND id=? AND status='READY'",encode(report),event.tenant(),event.id())!=1)throw new BizException(1803,"修改前事实无法保存，暂不提交平台");
    }
    public void intent(Event event,JsonNode intent){var actual=json.createObjectNode();actual.set("operation",intent);if(jdbc.update("UPDATE optimization_event SET actual_json=? WHERE tenant_id=? AND id=? AND status='EXECUTING' AND actual_json IS NULL",encode(actual),event.tenant(),event.id())!=1)throw new BizException(1803,"执行意图无法持久化，不提交平台");}
    public boolean dispatch(Event event,String owner){return Boolean.TRUE.equals(tx.execute(s->{
        var guarded=jdbc.queryForList("SELECT account_id FROM optimization_account_guard WHERE tenant_id=? AND account_id=? AND lease_owner=? AND lease_until>? FOR UPDATE",event.tenant(),event.accountId(),owner,Timestamp.from(Instant.now()));
        var policy=jdbc.queryForList("SELECT plan_id FROM optimization_policy WHERE tenant_id=? AND plan_id=? AND policy_version=? AND enabled=TRUE AND hold_reason IS NULL FOR UPDATE",event.tenant(),event.planId(),event.version());
        if(guarded.isEmpty() || policy.isEmpty())return false;
        return jdbc.update("UPDATE optimization_event SET status='EXECUTING',dispatched_at=? WHERE tenant_id=? AND id=? AND status='READY'",Timestamp.from(Instant.now()),event.tenant(),event.id())==1;
    }));}
    public void finish(Event event,String status,String error,JsonNode actual){tx.executeWithoutResult(s->{
        int changed=jdbc.update("UPDATE optimization_event SET status=?,error_message=?,actual_json=?,finished_at=? WHERE tenant_id=? AND id=? AND status IN ('READY','EXECUTING','VERIFY_PENDING','UNKNOWN')",status,trim(error),actual==null?null:encode(actual),Set.of("VERIFY_PENDING","EXECUTING").contains(status)?null:Timestamp.from(Instant.now()),event.tenant(),event.id());
        if(changed>0 && !Set.of("VERIFY_PENDING","EXECUTING").contains(status))notice(event.tenant(),event.planId(),"OPTIMIZATION_EXECUTION",status.equals("SUCCEEDED")?"自动调控已生效":status.equals("UNKNOWN")?"自动调控结果待人工核查":"自动调控未执行",event.planName()+"："+event.title()+"。"+(error==null?"已通过平台结果回查。":trim(error)));
    });}
    public void succeeded(Event event,JsonNode facts){tx.executeWithoutResult(s->{
        if(jdbc.queryForObject("SELECT COUNT(*) FROM optimization_event WHERE tenant_id=? AND id=? AND status IN ('EXECUTING','VERIFY_PENDING','UNKNOWN')",Integer.class,event.tenant(),event.id())!=1)return;
        finish(event,"SUCCEEDED",null,facts);var now=Timestamp.from(Instant.now());
        String cause="PAUSE".equals(event.kind())?event.proposal().path("cause").asText():null;
        var baseline=facts.deepCopy();if(baseline.isObject())((com.fasterxml.jackson.databind.node.ObjectNode)baseline).remove(List.of("operation","actionConfirmed"));
        jdbc.update("UPDATE optimization_policy SET baseline_json=?,last_action_at=?,auto_paused_reason=?,auto_paused_at=? WHERE tenant_id=? AND plan_id=? AND policy_version=? AND hold_reason IS NULL",encode(baseline),now,cause,cause==null?null:now,event.tenant(),event.planId(),event.version());
        if(Set.of("MATERIAL_ADD","MATERIAL_REMOVE","PRODUCT_ADD","PRODUCT_REMOVE").contains(event.kind()))jdbc.update("UPDATE optimization_policy SET result_json=NULL,next_check_at=? WHERE tenant_id=? AND plan_id=? AND policy_version=?",now,event.tenant(),event.planId(),event.version());
    });}
    public void resume(String tenant,String plan,long expectedVersion,JsonNode facts){tx.executeWithoutResult(s->{
        if(jdbc.update("UPDATE optimization_policy SET hold_reason=NULL,baseline_json=?,policy_version=policy_version+1,next_check_at=?,result_json=NULL,auto_paused_reason=NULL,auto_paused_at=NULL WHERE tenant_id=? AND plan_id=? AND policy_version=?",encode(facts),Timestamp.from(Instant.now()),tenant,plan,expectedVersion)!=1)throw new BizException(1804,"盯盘设置已变化，请刷新后恢复");
        jdbc.update("UPDATE optimization_outbox SET status='CANCELLED',updated_at=? WHERE tenant_id=? AND plan_id=? AND status='PENDING'",Timestamp.from(Instant.now()),tenant,plan);
        jdbc.update("UPDATE optimization_event SET status='SKIPPED',error_message='已重新授权新的调控版本',finished_at=? WHERE tenant_id=? AND plan_id=? AND status='READY'",Timestamp.from(Instant.now()),tenant,plan);
        notice(tenant,plan,"OPTIMIZATION_RECOVERED","自动调控授权已恢复","后续使用最新平台状态重新判断。");
    });}
    public Page history(String tenant,String plan,String account,String status,Instant start,Instant end,int page,int size){
        if(page<1 || size<1 || size>100)throw new BizException(1802,"分页范围不正确");
        String sql=" FROM optimization_event WHERE tenant_id=?";List<Object> args=new ArrayList<>(List.of(tenant));
        for(var pair:List.of(new String[]{"plan_id",plan},new String[]{"account_id",account},new String[]{"status",status}))if(pair[1]!=null && !pair[1].isBlank()){sql+=" AND "+pair[0]+"=?";args.add(pair[1]);}
        if(start!=null){sql+=" AND created_at>=?";args.add(Timestamp.from(start));}if(end!=null){sql+=" AND created_at<?";args.add(Timestamp.from(end));}
        long count=jdbc.queryForObject("SELECT COUNT(*)"+sql,Long.class,args.toArray());args.add(size);args.add((long)(page-1)*size);
        return new Page(jdbc.query("SELECT *"+sql+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",this::row,args.toArray()),count,page,size);
    }
    private String trim(String value){return value==null?null:value.substring(0,Math.min(500,value.length()));}
}
