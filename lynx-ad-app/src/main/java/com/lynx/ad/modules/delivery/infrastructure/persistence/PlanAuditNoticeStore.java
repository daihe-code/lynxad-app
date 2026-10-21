package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.infrastructure.persistence.InboxOutboxStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Captures results from polling, manual refresh and account/detail sync through the same repository hook. */
@Repository
public class PlanAuditNoticeStore {
    private final JdbcTemplate jdbc;private final InboxOutboxStore outbox;private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public PlanAuditNoticeStore(JdbcTemplate jdbc,InboxOutboxStore outbox){this(jdbc,outbox,Clock.systemUTC());}
    public PlanAuditNoticeStore(JdbcTemplate jdbc,InboxOutboxStore outbox,Clock clock){this.jdbc=jdbc;this.outbox=outbox;this.clock=clock;}
    public void saved(AdPlan plan,String previousStatus,String previousQc) {
        if(plan.source()!=PlanSource.LINGTOU || plan.qcPlanId().isEmpty())return;
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("审核通知必须与计划结果在同一事务保存");
        String tenant=plan.tenantId().value(),id=plan.id().value(),qc=plan.qcPlanId().orElseThrow().value();
        var rows=jdbc.query("SELECT qc_plan_id,cycle_id,state FROM delivery_plan_audit_notice WHERE tenant_id=? AND plan_id=? FOR UPDATE",(r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3)},tenant,id);
        boolean pending=plan.status()==PlanStatus.PENDING_AUDIT;
        boolean wasPending="PENDING_AUDIT".equals(previousStatus) && qc.equals(previousQc);
        boolean enter=pending && !wasPending;
        String cycle;String state;
        if(rows.isEmpty()) {
            if(!pending && !wasPending)return; // Do not manufacture historical results from a terminal snapshot.
            cycle=UUID.randomUUID().toString();state="WAITING";
            jdbc.update("INSERT INTO delivery_plan_audit_notice(tenant_id,plan_id,qc_plan_id,cycle_id,state,updated_at) VALUES(?,?,?,?,'WAITING',?)",tenant,id,qc,cycle,Timestamp.from(clock.instant()));
        } else {
            var row=rows.getFirst();cycle=row[1];state=row[2];
            if(!qc.equals(row[0]) || enter) {
                if(!pending)return;
                cycle=UUID.randomUUID().toString();state="WAITING";
                jdbc.update("UPDATE delivery_plan_audit_notice SET qc_plan_id=?,cycle_id=?,state='WAITING',updated_at=? WHERE tenant_id=? AND plan_id=?",qc,cycle,Timestamp.from(clock.instant()),tenant,id);
            }
        }
        if(!"WAITING".equals(state))return;
        String verdict=plan.status()==PlanStatus.DELIVERING?"PASS":plan.status()==PlanStatus.REJECTED?"REJECT":null;
        if(verdict==null)return; // A pause, missing plan or unknown status is not proof of approval/rejection.
        Instant occurred=clock.instant();String type="AUDIT_"+verdict;
        String time=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(occurred);
        String title=verdict.equals("PASS")?"计划审核通过":"计划审核未通过";
        String body="系统于 "+time+" 确认广告投放计划「"+plan.name().text()+"」本轮审核"+(verdict.equals("PASS")?"已通过":"未通过")+"。请查看计划详情中的当前状态"+(verdict.equals("REJECT")?"与审核原因":"")+"。";
        outbox.enqueue(plan.tenantId(),key(id,qc,cycle,type),title,body,new BizRef(type,id),occurred);
        jdbc.update("UPDATE delivery_plan_audit_notice SET state='RECORDED',updated_at=? WHERE tenant_id=? AND plan_id=?",Timestamp.from(occurred),tenant,id);
    }
    private static String key(String... fields){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",fields).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
