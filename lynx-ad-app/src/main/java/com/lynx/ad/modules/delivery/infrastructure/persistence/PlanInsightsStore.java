package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PlanInsightsStore {
    public record Log(String id,Instant time,String actor,String operation,String result,String detail) {}
    public record Guarantee(String id,Instant time,String status,String reason,String url) {}
    public record Page<T>(List<T> records,long total,int page,int pageSize) {}
    private final JdbcTemplate jdbc;
    public PlanInsightsStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private Object[] bounds(TenantId tenant,String id,PlanStatisticsRange range) {
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        return new Object[]{tenant.value(),id,java.sql.Timestamp.from(range.startDate().atStartOfDay(zone).toInstant()),
                java.sql.Timestamp.from(range.endDate().plusDays(1).atStartOfDay(zone).toInstant())};
    }
    public Page<Log> logs(TenantId tenant,String id,PlanStatisticsRange range,int page) {
        String where=" WHERE tenant_id=? AND object_type='PLAN' AND object_id=? AND created_at>=? AND created_at<?";
        Object[] args=bounds(tenant,id,range);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM audit_record"+where,Long.class,args);
        List<Object> paged=new ArrayList<>(Arrays.asList(args));paged.add(20);paged.add((page-1)*20);
        var rows=jdbc.query("SELECT id,created_at,actor_name,action_type,result,object_label FROM audit_record"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs,n)->new Log(rs.getString(1),rs.getTimestamp(2).toInstant(),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)),paged.toArray());
        return new Page<>(rows,total==null?0:total,page,20);
    }
    /** Serialize observations for the same physical plan; never synthesize past guarantees. */
    @Transactional
    public void observeGuarantee(TenantId tenant,String id,String status,String reason,String url) {
        if(status==null || status.isBlank())return;
        var owned=jdbc.queryForList("SELECT id FROM ad_plan WHERE tenant_id=? AND id=? FOR UPDATE",String.class,tenant.value(),id);
        if(owned.isEmpty())return;
        var latest=jdbc.queryForList("SELECT status FROM delivery_guarantee_history WHERE tenant_id=? AND plan_id=? ORDER BY observed_at DESC,id DESC LIMIT 1",String.class,tenant.value(),id);
        if(!latest.isEmpty() && latest.getFirst().equals(status))return;
        jdbc.update("INSERT INTO delivery_guarantee_history(id,tenant_id,plan_id,status,reason,detail_url,observed_at) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),tenant.value(),id,status,reason,url,java.sql.Timestamp.from(Instant.now()));
    }
    public Page<Guarantee> guarantees(TenantId tenant,String id,PlanStatisticsRange range,int page) {
        String where=" WHERE tenant_id=? AND plan_id=? AND observed_at>=? AND observed_at<?";
        Object[] args=bounds(tenant,id,range);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM delivery_guarantee_history"+where,Long.class,args);
        List<Object> paged=new ArrayList<>(Arrays.asList(args));paged.add(20);paged.add((page-1)*20);
        var rows=jdbc.query("SELECT id,observed_at,status,reason,detail_url FROM delivery_guarantee_history"+where+" ORDER BY observed_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs,n)->new Guarantee(rs.getString(1),rs.getTimestamp(2).toInstant(),rs.getString(3),rs.getString(4),rs.getString(5)),paged.toArray());
        return new Page<>(rows,total==null?0:total,page,20);
    }
}
