package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.service.PlanReviewProgress;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Leases and cycle identity are durable and shared by both App instances. No network work in transactions. */
@Repository
public class PlanReviewStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    public record Claim(TenantId tenant,PlanId plan,String qcId,String key,Instant since) {}
    @org.springframework.beans.factory.annotation.Autowired
    public PlanReviewStore(JdbcTemplate jdbc,PlatformTransactionManager manager){this(jdbc,manager,Clock.systemUTC());}
    public PlanReviewStore(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){this.jdbc=jdbc;tx=new TransactionTemplate(manager);this.clock=clock;}
    public void saved(AdPlan plan,boolean newCycle) {
        if(plan.status()==PlanStatus.PENDING_AUDIT && plan.qcPlanId().isPresent()) {
            if(newCycle)jdbc.update("UPDATE ad_plan SET audit_pending_since=?,audit_last_checked_at=NULL,audit_last_result=NULL,audit_next_check_at=NULL,audit_check_key=NULL,audit_lease_until=NULL,audit_last_attempt_at=NULL WHERE tenant_id=? AND id=? AND status='PENDING_AUDIT' AND qc_plan_id=?",
                ts(clock.instant()),plan.tenantId().value(),plan.id().value(),plan.qcPlanId().orElseThrow().value());
            else jdbc.update("UPDATE ad_plan SET audit_pending_since=COALESCE(audit_pending_since,?) WHERE tenant_id=? AND id=? AND status='PENDING_AUDIT' AND qc_plan_id=?",ts(clock.instant()),plan.tenantId().value(),plan.id().value(),plan.qcPlanId().orElseThrow().value());
        } else jdbc.update("UPDATE ad_plan SET audit_pending_since=NULL,audit_last_checked_at=NULL,audit_last_result=NULL,audit_next_check_at=NULL,audit_check_key=NULL,audit_lease_until=NULL,audit_last_attempt_at=NULL WHERE tenant_id=? AND id=? AND status<>'PENDING_AUDIT'",plan.tenantId().value(),plan.id().value());
    }
    public Optional<Claim> claim(AdPlan plan,boolean manual) {
        if(plan.status()!=PlanStatus.PENDING_AUDIT || plan.qcPlanId().isEmpty())return Optional.empty();
        Instant now=clock.instant();String key=UUID.randomUUID().toString(),qc=plan.qcPlanId().orElseThrow().value();
        String due=manual?"(audit_last_attempt_at IS NULL OR audit_last_attempt_at<=?)":"(audit_next_check_at IS NULL OR audit_next_check_at<=?)";
        int changed=jdbc.update("UPDATE ad_plan SET audit_pending_since=COALESCE(audit_pending_since,?),audit_check_key=?,audit_lease_until=?,audit_last_attempt_at=? WHERE tenant_id=? AND id=? AND qc_plan_id=? AND status='PENDING_AUDIT' AND (audit_lease_until IS NULL OR audit_lease_until<=?) AND "+due,
            ts(now),key,ts(now.plusSeconds(180)),ts(now),plan.tenantId().value(),plan.id().value(),qc,ts(now),ts(manual?now.minusSeconds(30):now));
        if(changed!=1)return Optional.empty();
        return jdbc.query("SELECT audit_pending_since FROM ad_plan WHERE tenant_id=? AND id=? AND audit_check_key=?",(r,n)->new Claim(plan.tenantId(),plan.id(),qc,key,r.getTimestamp(1).toInstant()),plan.tenantId().value(),plan.id().value(),key).stream().findFirst();
    }
    /** A late response cannot overwrite a newer lease or a later review cycle. */
    public boolean finish(Claim claim,String result,Runnable applyStatus) {
        return Boolean.TRUE.equals(tx.execute(s->{
            var current=jdbc.query("SELECT audit_check_key FROM ad_plan WHERE tenant_id=? AND id=? AND qc_plan_id=? AND status='PENDING_AUDIT' FOR UPDATE",(r,n)->r.getString(1),claim.tenant().value(),claim.plan().value(),claim.qcId());
            if(current.isEmpty()||!claim.key().equals(current.getFirst()))return false;
            applyStatus.run();
            Instant now=clock.instant();
            jdbc.update("UPDATE ad_plan SET audit_last_checked_at=?,audit_last_result=?,audit_next_check_at=?,audit_check_key=NULL,audit_lease_until=NULL WHERE tenant_id=? AND id=? AND audit_check_key=?",
                ts(now),result,ts(now.plus(PlanReviewProgress.interval(claim.since(),now))),claim.tenant().value(),claim.plan().value(),claim.key());
            return true;
        }));
    }
    public Map<String,PlanReviewProgress.View> list(TenantId tenant) {
        Map<String,PlanReviewProgress.View> result=new HashMap<>();Instant now=clock.instant();
        jdbc.query("SELECT id,audit_pending_since,audit_last_checked_at,audit_last_result,audit_next_check_at,audit_lease_until,audit_last_attempt_at FROM ad_plan WHERE tenant_id=? AND status='PENDING_AUDIT' AND qc_plan_id IS NOT NULL",r->{
            result.put(r.getString(1),PlanReviewProgress.view(instant(r,2),instant(r,3),r.getString(4),instant(r,5),instant(r,6),instant(r,7),now));
        },tenant.value());
        return result;
    }
    private static Instant instant(ResultSet r,int col)throws SQLException{return r.getTimestamp(col)==null?null:r.getTimestamp(col).toInstant();}
    private static Timestamp ts(Instant value){return Timestamp.from(value);}
}
