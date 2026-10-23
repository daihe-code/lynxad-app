package com.lynx.ad.modules.tenant.infrastructure.persistence;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

/** Durable inbox delivery only. Retrying an event never invokes a business operation or platform API. */
@Repository
public class InboxOutboxStore {
    public record Claim(String id,String tenant,String key,int attempt) {}
    public record Delivered(String tenant,String notificationId,String title) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public InboxOutboxStore(JdbcTemplate jdbc,PlatformTransactionManager manager){this(jdbc,manager,Clock.systemUTC());}
    public InboxOutboxStore(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){
        this.jdbc=jdbc;this.clock=clock;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    /** Called under the source plan's row lock, inside the outcome transaction. */
    public void enqueue(TenantId tenant,String eventKey,String title,String body,BizRef ref,Instant occurredAt) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("通知事件必须与业务结果在同一事务保存");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox WHERE tenant_id=? AND event_key=?",Integer.class,tenant.value(),eventKey)>0)return;
        jdbc.update("INSERT INTO inbox_outbox(id,tenant_id,event_key,notification_id,title,body,biz_type,biz_id,occurred_at,state,attempts,next_attempt_at) VALUES(?,?,?,?,?,?,?,?,?,'READY',0,?)",
            UUID.randomUUID().toString(),tenant.value(),eventKey,UUID.randomUUID().toString(),title,body,ref.bizType(),ref.bizId(),ts(occurredAt),ts(clock.instant()));
    }
    public List<String> due(TenantId tenant,int limit) {
        return due(tenant,limit,false);
    }
    public List<String> due(TenantId tenant,int limit,boolean login) {
        String scope=tenant==null?"":" AND tenant_id=?";
        String due=login?"(last_attempt_at IS NULL OR last_attempt_at<=?)":"next_attempt_at<=?";
        String sql="SELECT id FROM inbox_outbox WHERE state IN ('READY','RETRY','SENDING') AND "+due+" AND (lease_until IS NULL OR lease_until<=?)"+scope+" ORDER BY next_attempt_at,id LIMIT "+Math.max(1,Math.min(limit,50));
        Instant now=clock.instant(),eligible=login?now.minusSeconds(30):now;return tenant==null?jdbc.queryForList(sql,String.class,ts(eligible),ts(now)):jdbc.queryForList(sql,String.class,ts(eligible),ts(now),tenant.value());
    }
    public Optional<Claim> claim(String id,TenantId scope) {
        return claim(id,scope,false);
    }
    public Optional<Claim> claim(String id,TenantId scope,boolean login) {
        return tx.execute(s->{
            Instant now=clock.instant();String key=UUID.randomUUID().toString();String tenant=scope==null?"":" AND tenant_id=?";
            String due=login?"(last_attempt_at IS NULL OR last_attempt_at<=?)":"next_attempt_at<=?";
            String sql="UPDATE inbox_outbox SET state='SENDING',lease_key=?,lease_until=?,last_attempt_at=?,attempts=attempts+1 WHERE id=? AND state IN ('READY','RETRY','SENDING') AND "+due+" AND (lease_until IS NULL OR lease_until<=?)"+tenant;
            Instant eligible=login?now.minusSeconds(30):now;
            Object[] args=scope==null?new Object[]{key,ts(now.plusSeconds(120)),ts(now),id,ts(eligible),ts(now)}:new Object[]{key,ts(now.plusSeconds(120)),ts(now),id,ts(eligible),ts(now),scope.value()};
            if(jdbc.update(sql,args)!=1)return Optional.empty();
            return jdbc.query("SELECT tenant_id,attempts FROM inbox_outbox WHERE id=? AND lease_key=?",(r,n)->new Claim(id,r.getString(1),key,r.getInt(2)),id,key).stream().findFirst();
        });
    }
    /** Notification insertion and SENT acknowledgement commit together; an uncertain commit is safe to retry. */
    public Optional<Delivered> deliver(Claim claim) {
        return tx.execute(s->{
            var events=jdbc.query("SELECT notification_id,title,body,biz_type,biz_id FROM inbox_outbox WHERE id=? AND tenant_id=? AND state='SENDING' AND lease_key=? FOR UPDATE",
                (r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)},claim.id(),claim.tenant(),claim.key());
            if(events.isEmpty())return Optional.empty();
            var e=events.getFirst();
            var existing=jdbc.query("SELECT tenant_id,channel FROM notification WHERE id=?",(r,n)->new String[]{r.getString(1),r.getString(2)},e[0]);
            if(!existing.isEmpty() && (!claim.tenant().equals(existing.getFirst()[0]) || !"INBOX".equals(existing.getFirst()[1])))throw new IllegalStateException("通知标识归属不一致");
            Instant sent=clock.instant();boolean inserted=existing.isEmpty();
            if(inserted)jdbc.update("INSERT INTO notification(id,tenant_id,channel,title,body,biz_type,biz_id,sent_at) VALUES(?,?,'INBOX',?,?,?,?,?)",e[0],claim.tenant(),e[1],e[2],e[3],e[4],ts(sent));
            // Never update an existing message: in particular, never reset its read_at or sent_at.
            jdbc.update("UPDATE inbox_outbox SET state='SENT',delivered_at=?,lease_key=NULL,lease_until=NULL,last_failure=NULL WHERE id=? AND tenant_id=? AND lease_key=?",ts(sent),claim.id(),claim.tenant(),claim.key());
            return inserted?Optional.of(new Delivered(claim.tenant(),e[0],e[1])):Optional.empty();
        });
    }
    public void retry(Claim claim) {
        tx.executeWithoutResult(s->jdbc.update("UPDATE inbox_outbox SET state='RETRY',next_attempt_at=?,lease_key=NULL,lease_until=NULL,last_failure='INBOX_WRITE_FAILED' WHERE id=? AND tenant_id=? AND state='SENDING' AND lease_key=?",
            ts(clock.instant().plusSeconds(retryDelaySeconds(claim.attempt()))),claim.id(),claim.tenant(),claim.key()));
    }
    public static long retryDelaySeconds(int attempt){return switch(Math.min(Math.max(attempt,1),5)){case 1->30;case 2->120;case 3->300;case 4->900;default->3600;};}
    private static Timestamp ts(Instant v){return Timestamp.from(v);}
}
