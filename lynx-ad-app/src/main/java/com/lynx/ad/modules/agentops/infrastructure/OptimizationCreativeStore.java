package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.application.OptimizationPolicy;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationAutomationStore.Event;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable generation reservations and trials. Platform publication remains in the execution journal. */
@Repository
public class OptimizationCreativeStore {
    public record Job(String id,String tenant,String planId,String logicalPlanId,String accountId,String productId,long version,String status,BigDecimal reservedCost,JsonNode body,Instant createdAt,Instant publishedAt){}
    private static final Set<String> TERMINAL=Set.of("QUALIFIED","FAILED","CANCELLED","REMOVED","LOSS_RETAINED","UNTESTED");
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final TransactionTemplate tx;
    public OptimizationCreativeStore(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager){this.jdbc=jdbc;this.json=json;tx=new TransactionTemplate(manager);}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String error(JsonNode body){String value=body.path("error").asText(null);return value==null?null:value.substring(0,Math.min(500,value.length()));}
    private JsonNode decode(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Instant instant(ResultSet r,String field)throws SQLException{var t=r.getTimestamp(field);return t==null?null:t.toInstant();}
    private Job row(ResultSet r,int n)throws SQLException{return new Job(r.getString("id"),r.getString("tenant_id"),r.getString("plan_id"),r.getString("logical_plan_id"),r.getString("account_id"),r.getString("product_id"),r.getLong("policy_version"),r.getString("status"),r.getBigDecimal("reserved_cost"),decode(r.getString("body_json")),instant(r,"created_at"),instant(r,"published_at"));}
    public Optional<Job> get(String tenant,String id){return jdbc.query("SELECT * FROM optimization_creative_job WHERE tenant_id=? AND id=?",this::row,tenant,id).stream().findFirst();}
    public List<Job> history(String tenant,String plan){return jdbc.query("SELECT * FROM optimization_creative_job WHERE tenant_id=? AND plan_id=? ORDER BY created_at DESC LIMIT 50",this::row,tenant,plan);}
    public List<Job> sharedHistory(String tenant,String root){return jdbc.query("SELECT * FROM optimization_creative_job WHERE tenant_id=? AND logical_plan_id=? ORDER BY created_at DESC LIMIT 50",this::row,tenant,root);}
    public List<Job> due(){return jdbc.query("SELECT * FROM optimization_creative_job WHERE status NOT IN ('QUALIFIED','FAILED','CANCELLED','REMOVED','LOSS_RETAINED','UNTESTED') AND (lease_until IS NULL OR lease_until<?) ORDER BY updated_at LIMIT 10",this::row,Timestamp.from(Instant.now()));}
    public boolean claim(Job job,String owner){return jdbc.update("UPDATE optimization_creative_job SET lease_owner=?,lease_until=? WHERE tenant_id=? AND id=? AND (lease_until IS NULL OR lease_until<?)",owner,Timestamp.from(Instant.now().plusSeconds(600)),job.tenant(),job.id(),Timestamp.from(Instant.now()))==1;}
    public void release(Job job,String owner){jdbc.update("UPDATE optimization_creative_job SET lease_owner=NULL,lease_until=? WHERE tenant_id=? AND id=? AND lease_owner=?",Timestamp.from(Instant.now().plusSeconds(15)),job.tenant(),job.id(),owner);}
    public JsonNode context(String tenant,String root){
        var result=json.createObjectNode();var pending=result.putObject("pending");var last=result.putObject("last_generation");var outcomes=result.putArray("trial_outcomes");BigDecimal reserved=BigDecimal.ZERO;
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        for(var job:jdbc.query("SELECT * FROM optimization_creative_job WHERE tenant_id=? AND logical_plan_id=? ORDER BY created_at",this::row,tenant,root)){
            last.put(job.productId(),job.createdAt().toString());if(!TERMINAL.contains(job.status()))pending.put(job.productId(),pending.path(job.productId()).asInt()+1);
            if(!job.createdAt().isBefore(today))reserved=reserved.add(job.reservedCost());
            if(Set.of("QUALIFIED","REMOVED","LOSS_RETAINED","UNTESTED").contains(job.status())){var outcome=outcomes.addObject().put("product_id",job.productId()).put("status",job.status());outcome.set("strategy",job.body().path("strategy"));outcome.set("summary",job.body().path("trial_summary"));}
        }while(outcomes.size()>20)outcomes.remove(0);result.put("reserved_today",reserved.toPlainString());return result;
    }
    public Job reserve(Event event,String root,OptimizationPolicy policy,JsonNode body,String owner){return tx.execute(s->{
        if(jdbc.queryForList("SELECT account_id FROM optimization_account_guard WHERE tenant_id=? AND account_id=? AND lease_owner=? AND lease_until>? FOR UPDATE",event.tenant(),event.accountId(),owner,Timestamp.from(Instant.now())).isEmpty())throw new BizException(1803,"账户生成授权租约已变化");
        if(jdbc.queryForList("SELECT plan_id FROM optimization_policy WHERE tenant_id=? AND plan_id=? AND policy_version=? AND enabled=TRUE AND hold_reason IS NULL FOR UPDATE",event.tenant(),event.planId(),event.version()).isEmpty())throw new BizException(1803,"生成授权已变化");
        var existing=get(event.tenant(),event.id());if(existing.isPresent())return existing.get();
        var ctx=context(event.tenant(),root);String product=event.proposal().path("product_id").asText();var g=policy.creativeGeneration();
        if(!g.enabled()||g.authorizedBy()==null||!"AUTO_LIMITED".equals(policy.mode()))throw new BizException(1802,"请保存并授权新的自动生成设置");
        var last=ctx.path("last_generation").path(product);if(last.isTextual()&&Instant.parse(last.asText()).plusSeconds(86400).isAfter(Instant.now()))throw new BizException(1803,"该商品24小时内已有生成批次");
        BigDecimal cost=new BigDecimal(event.proposal().path("strategy").path("reserved_cost").asText());
        if(cost.signum()<=0||cost.compareTo(g.perAssetCostLimit())>0||cost.add(new BigDecimal(ctx.path("reserved_today").asText())).compareTo(g.dailyCostLimit())>0||ctx.path("pending").path(product).asInt()>=g.maxPending())throw new BizException(1803,"生成预算预留或待验证数量达到上限");
        var now=Timestamp.from(Instant.now());jdbc.update("INSERT INTO optimization_creative_job(id,tenant_id,plan_id,logical_plan_id,account_id,product_id,policy_version,status,reserved_cost,body_json,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'QUEUED',?,?,?,?)",event.id(),event.tenant(),event.planId(),root,event.accountId(),product,event.version(),cost,encode(body),now,now);
        jdbc.update("UPDATE optimization_event SET status='GENERATING' WHERE tenant_id=? AND id=? AND status='READY'",event.tenant(),event.id());return get(event.tenant(),event.id()).orElseThrow();
    });}
    public void update(Job job,String status,JsonNode body){tx.executeWithoutResult(s->{int changed=jdbc.update("UPDATE optimization_creative_job SET status=?,body_json=?,updated_at=? WHERE tenant_id=? AND id=? AND status=?",status,encode(body),Timestamp.from(Instant.now()),job.tenant(),job.id(),job.status());if(changed==1){jdbc.update("UPDATE optimization_event SET status=?,actual_json=?,error_message=?,finished_at=? WHERE tenant_id=? AND id=? AND kind='MATERIAL_GENERATE'",status,encode(body),error(body),TERMINAL.contains(status)?Timestamp.from(Instant.now()):null,job.tenant(),job.id());if(!status.equals(job.status())&&Set.of("FAILED","CANCELLED","LOSS_RETAINED","UNTESTED","REMOVED","UNKNOWN").contains(status))jdbc.update("INSERT INTO notification(id,tenant_id,channel,title,body,biz_type,biz_id,sent_at) VALUES(?,?,'INBOX','素材生成与试投状态更新',?,'OPTIMIZATION_EXECUTION',?,?)",UUID.randomUUID().toString(),job.tenant(),error(body)==null?"请到自动盯盘页面查看素材任务处理结果":error(body),job.planId(),Timestamp.from(Instant.now()));}});}
    public void published(Job job,JsonNode body){jdbc.update("UPDATE optimization_creative_job SET published_at=? WHERE tenant_id=? AND id=? AND published_at IS NULL",Timestamp.from(Instant.now()),job.tenant(),job.id());update(job,"PLATFORM_REVIEW",body);}
    public void approve(String tenant,String id,boolean approve){tx.executeWithoutResult(s->{var job=get(tenant,id).orElseThrow(()->new BizException(1405,"素材生成记录不存在"));if(approve?!"REVIEW".equals(job.status()):TERMINAL.contains(job.status())||Set.of("PUBLISHING","REMOVING").contains(job.status()))throw new BizException(1802,"当前状态不能确认或停止，请先核查原平台执行记录");var body=(ObjectNode)job.body().deepCopy();body.put("approved",approve);if(!approve)body.put("error","用户已停止后续发布和试投管理；已生成文件和已发布绑定保留，已发出的生成请求可能仍需计费");update(job,approve?"SYNCING":"CANCELLED",body);});}
    public Job publication(String tenant,String id,String plan,long version){var job=get(tenant,id).orElseThrow(()->new BizException(1802,"生成来源记录不存在"));if(!job.planId().equals(plan)||job.version()!=version||!"PUBLISHING".equals(job.status()))throw new BizException(1803,"生成来源、授权或状态不匹配");return job;}
    public String enqueue(Job job,JsonNode action,JsonNode report){return tx.execute(s->{
        if(jdbc.queryForList("SELECT id FROM optimization_creative_job WHERE tenant_id=? AND id=? AND status=? FOR UPDATE",job.tenant(),job.id(),job.status()).isEmpty()||jdbc.queryForList("SELECT plan_id FROM optimization_policy WHERE tenant_id=? AND plan_id=? AND policy_version=? AND enabled=TRUE AND hold_reason IS NULL FOR UPDATE",job.tenant(),job.planId(),job.version()).isEmpty())throw new BizException(1803,"生成或发布授权状态已变化");
        String fingerprint="creative:"+job.id()+":"+action.path("kind").asText();var rows=jdbc.queryForList("SELECT id FROM optimization_event WHERE tenant_id=? AND fingerprint=?",job.tenant(),fingerprint);if(!rows.isEmpty())return rows.getFirst().get("id").toString();
        String id=UUID.randomUUID().toString();int changed=jdbc.update("INSERT INTO optimization_event(id,tenant_id,plan_id,account_id,plan_name,fingerprint,policy_version,kind,title,evidence,proposal_json,report_json,status,created_at) SELECT ?,tenant_id,plan_id,account_id,plan_name,?,policy_version,?,?,?, ?,?,'READY',? FROM optimization_event WHERE tenant_id=? AND id=?",id,fingerprint,action.path("kind").asText(),"MATERIAL_ADD".equals(action.path("kind").asText())?"发布生成素材并试投":"移除达到试投亏损上限的素材",job.body().path("strategy").path("hypothesis").asText(),encode(action),encode(report),Timestamp.from(Instant.now()),job.tenant(),job.id());if(changed!=1)throw new BizException(1803,"生成原始记录缺失");
        boolean adding="MATERIAL_ADD".equals(action.path("kind").asText());var next=(ObjectNode)job.body().deepCopy();next.put(adding?"publication_event_id":"removal_event_id",id);update(job,adding?"PUBLISHING":"REMOVING",next);return id;
    });}
}
