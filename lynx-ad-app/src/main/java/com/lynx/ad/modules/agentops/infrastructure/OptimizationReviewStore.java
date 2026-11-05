package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.application.MonitoringStrategy;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OptimizationReviewStore {
    public record Review(String id,String tenant,String planId,String eventId,String contentWorkId,long version,String workflow,String status,
            JsonNode before,JsonNode after,JsonNode baseline,JsonNode result,MonitoringStrategy.Parameters strategy,
            Instant createdAt,Instant actionAt,Instant updatedAt){}
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public OptimizationReviewStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private JsonNode node(String s){try{return s==null?null:json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Instant instant(ResultSet r,String key)throws SQLException{var t=r.getTimestamp(key);return t==null?null:t.toInstant();}
    private Review row(ResultSet r,int ignored)throws SQLException{
        try{return new Review(r.getString("id"),r.getString("tenant_id"),r.getString("plan_id"),r.getString("event_id"),r.getString("content_work_id"),r.getLong("policy_version"),r.getString("workflow"),r.getString("status"),node(r.getString("before_json")),node(r.getString("after_json")),node(r.getString("baseline_json")),node(r.getString("result_json")),json.readValue(r.getString("strategy_json"),MonitoringStrategy.Parameters.class),instant(r,"created_at"),instant(r,"action_at"),instant(r,"updated_at"));}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public List<Review> history(String tenant,String plan,boolean today){String sql="SELECT * FROM optimization_review WHERE tenant_id=? AND plan_id=?";var args=new ArrayList<Object>(List.of(tenant,plan));if(today){sql+=" AND action_at>=?";args.add(Timestamp.from(LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));}return jdbc.query(sql+" ORDER BY created_at DESC LIMIT 100",this::row,args.toArray());}
    public Optional<Review> find(String tenant,String id){return jdbc.query("SELECT * FROM optimization_review WHERE tenant_id=? AND id=?",this::row,tenant,id).stream().findFirst();}
    public Review start(OptimizationAutomationStore.Event event,String workflow,JsonNode facts,JsonNode baseline,MonitoringStrategy.Parameters parameters){
        var existing=jdbc.query("SELECT * FROM optimization_review WHERE tenant_id=? AND event_id=?",this::row,event.tenant(),event.id());if(!existing.isEmpty())return existing.getFirst();
        String id=UUID.randomUUID().toString();var now=Timestamp.from(Instant.now());
        try{jdbc.update("INSERT INTO optimization_review(id,tenant_id,plan_id,event_id,policy_version,workflow,status,before_json,baseline_json,strategy_json,created_at,updated_at,next_check_at) VALUES(?,?,?,?,?,?,'TRACKING',?,?,?,?,?,?)",id,event.tenant(),event.planId(),event.id(),event.version(),workflow,encode(facts),encode(baseline),encode(parameters),now,now,now);}
        catch(org.springframework.dao.DuplicateKeyException duplicate){return jdbc.query("SELECT * FROM optimization_review WHERE tenant_id=? AND event_id=?",this::row,event.tenant(),event.id()).getFirst();}
        return find(event.tenant(),id).orElseThrow();
    }
    public boolean linkContent(Review r,String workId){return jdbc.update("UPDATE optimization_review SET content_work_id=? WHERE tenant_id=? AND id=? AND status='TRACKING' AND (content_work_id IS NULL OR content_work_id=?)",workId,r.tenant(),r.id(),workId)==1;}
    public List<Review> tracking(String tenant,String plan){return jdbc.query("SELECT * FROM optimization_review WHERE tenant_id=? AND plan_id=? AND status='TRACKING' ORDER BY created_at DESC LIMIT 10",this::row,tenant,plan);}
    public boolean applied(Review r,JsonNode actual){return applied(r,actual,Instant.now());}
    public boolean applied(Review r,JsonNode actual,Instant at){return jdbc.update("UPDATE optimization_review SET after_json=?,action_at=?,updated_at=?,next_check_at=?,status='OBSERVING',lease_owner=NULL,lease_until=NULL WHERE tenant_id=? AND id=? AND status='TRACKING'",encode(actual),Timestamp.from(at),Timestamp.from(Instant.now()),Timestamp.from(Instant.now().plusSeconds(60)),r.tenant(),r.id())==1;}
    public List<Review> due(){return jdbc.query("SELECT * FROM optimization_review WHERE status IN ('TRACKING','OBSERVING','WAITING_DATA') AND next_check_at<=? AND (lease_until IS NULL OR lease_until<?) ORDER BY next_check_at LIMIT 20",this::row,Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));}
    public boolean claim(Review r,String owner){return jdbc.update("UPDATE optimization_review SET lease_owner=?,lease_until=? WHERE tenant_id=? AND id=? AND status=? AND (lease_until IS NULL OR lease_until<?)",owner,Timestamp.from(Instant.now().plusSeconds(180)),r.tenant(),r.id(),r.status(),Timestamp.from(Instant.now()))==1;}
    public void finish(Review r,String owner,String status,JsonNode result){if(jdbc.update("UPDATE optimization_review SET status=?,result_json=?,updated_at=?,next_check_at=?,lease_owner=NULL,lease_until=NULL WHERE tenant_id=? AND id=? AND lease_owner=? AND status=?",status,encode(result),Timestamp.from(Instant.now()),Timestamp.from(Instant.now().plusSeconds(300)),r.tenant(),r.id(),owner,r.status())!=1)throw new BizException(1803,"复评任务租约已变化");}
    public void verifiedWorkflow(Review r,JsonNode actual){
        jdbc.update("UPDATE optimization_event SET status='SUCCEEDED',actual_json=?,finished_at=?,updated_at=? WHERE tenant_id=? AND id=? AND status='RECOMMENDED'",encode(actual),Timestamp.from(Instant.now()),Timestamp.from(Instant.now()),r.tenant(),r.eventId());
    }
}
