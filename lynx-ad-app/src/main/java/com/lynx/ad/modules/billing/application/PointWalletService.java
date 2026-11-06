package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All mutations lock the tenant wallet; caller-supplied balances are never trusted. */
@Service
public class PointWalletService {
    private final JdbcTemplate jdbc;
    private final WorkspaceFoundation foundation;
    public PointWalletService(JdbcTemplate jdbc,WorkspaceFoundation foundation) {this.jdbc=jdbc;this.foundation=foundation;}
    public record Wallet(BigDecimal available,BigDecimal frozen,BigDecimal consumed,long version) {}
    public record Reservation(String id,String tenantId,String taskKey,String bindingId,String actorUserId,BigDecimal amount,BigDecimal settled,String status) {}
    public Wallet balance(String tenant) {
        return jdbc.query("SELECT * FROM point_wallet WHERE tenant_id=?",(r,i)->new Wallet(r.getBigDecimal("available"),r.getBigDecimal("frozen"),r.getBigDecimal("consumed"),r.getLong("version")),tenant).stream().findFirst().orElseThrow(()->new BizException(1901,"积分账户尚未初始化"));
    }
    private void lock(String tenant) {foundation.ensure(tenant);jdbc.queryForList("SELECT tenant_id FROM point_wallet WHERE tenant_id=? FOR UPDATE",String.class,tenant);}
    private BigDecimal amount(BigDecimal value,boolean zero) {
        if(value==null||value.scale()>4||value.precision()-value.scale()>16||(zero?value.signum()<0:value.signum()<=0)) throw new BizException(1901,"积分数量无效");
        return value.setScale(4);
    }
    private void key(String key,int max) {if(key==null||key.isBlank()||key.length()>max)throw new BizException(1901,"积分记账标识无效");}
    private Optional<Reservation> reservation(String tenant,String task) {
        return jdbc.query("SELECT * FROM point_reservation WHERE tenant_id=? AND task_key=?",(r,i)->new Reservation(r.getString("id"),tenant,task,r.getString("binding_id"),r.getString("actor_user_id"),r.getBigDecimal("amount"),r.getBigDecimal("settled"),r.getString("status")),tenant,task).stream().findFirst();
    }
    private void ledger(String tenant,String event,String type,BigDecimal value,Reservation r) {
        jdbc.update("INSERT INTO point_ledger(id,tenant_id,event_key,event_type,amount,reservation_id,binding_id,actor_user_id) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),tenant,event,type,value,r==null?null:r.id(),r==null?null:r.bindingId(),r==null?null:r.actorUserId());
    }
    /** Internal settlement/payment integration only; there is deliberately no end-user credit API. */
    @Transactional
    public Wallet credit(String tenant,String event,BigDecimal value) {
        key(event,160);value=amount(value,false);lock(tenant);
        var old=jdbc.queryForList("SELECT event_type,amount FROM point_ledger WHERE tenant_id=? AND event_key=?",tenant,event);
        if(!old.isEmpty()) {
            if(!"CREDIT".equals(old.getFirst().get("event_type"))||value.compareTo((BigDecimal)old.getFirst().get("amount"))!=0)throw new BizException(1902,"积分请求标识已用于其他操作");
            return balance(tenant);
        }
        jdbc.update("UPDATE point_wallet SET available=available+?,version=version+1 WHERE tenant_id=?",value,tenant);
        ledger(tenant,event,"CREDIT",value,null);return balance(tenant);
    }
    @Transactional
    public Reservation reserve(String tenant,String task,String binding,String actor,BigDecimal value) {
        key(task,128);value=amount(value,false);lock(tenant);
        var old=reservation(tenant,task);
        if(old.isPresent()) {
            var r=old.get();
            if(r.amount().compareTo(value)!=0||!Objects.equals(binding,r.bindingId())||!Objects.equals(actor,r.actorUserId()))throw new BizException(1902,"任务积分预占信息不一致");
            return r;
        }
        if(binding!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE id=? AND tenant_id=? AND bound_user_id=? AND status='ACTIVE'",Integer.class,binding,tenant,actor)!=1)throw new BizException(403,"积分任务账户归属不匹配");
        if(balance(tenant).available().compareTo(value)<0)throw new BizException(1903,"可用积分不足");
        jdbc.update("UPDATE point_wallet SET available=available-?,frozen=frozen+?,version=version+1 WHERE tenant_id=?",value,value,tenant);
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO point_reservation(id,tenant_id,task_key,binding_id,actor_user_id,amount,status) VALUES(?,?,?,?,?,?,'HELD')",id,tenant,task,binding,actor,value);
        var r=reservation(tenant,task).orElseThrow();ledger(tenant,"reserve:"+id,"RESERVE",value,r);return r;
    }
    @Transactional
    public Reservation settle(String tenant,String task,BigDecimal actual) {
        actual=amount(actual,true);lock(tenant);
        var r=reservation(tenant,task).orElseThrow(()->new BizException(1901,"积分预占记录不存在"));
        if(actual.compareTo(r.amount())>0)throw new BizException(1901,"结算积分不能超过已确认的预占额度");
        if(!"HELD".equals(r.status())) {
            if(!"SETTLED".equals(r.status())||r.settled().compareTo(actual)!=0)throw new BizException(1902,"积分任务已结束，不能重复结算");
            return r;
        }
        jdbc.update("UPDATE point_wallet SET available=available+?,frozen=frozen-?,consumed=consumed+?,version=version+1 WHERE tenant_id=?",r.amount().subtract(actual),r.amount(),actual,tenant);
        jdbc.update("UPDATE point_reservation SET status='SETTLED',settled=? WHERE id=? AND tenant_id=?",actual,r.id(),tenant);
        ledger(tenant,"settle:"+r.id(),"SETTLE",actual,r);return reservation(tenant,task).orElseThrow();
    }
    @Transactional
    public Reservation release(String tenant,String task) {
        lock(tenant);var r=reservation(tenant,task).orElseThrow(()->new BizException(1901,"积分预占记录不存在"));
        if("RELEASED".equals(r.status()))return r;
        if(!"HELD".equals(r.status()))throw new BizException(1902,"已结算任务须走退款流程");
        jdbc.update("UPDATE point_wallet SET available=available+?,frozen=frozen-?,version=version+1 WHERE tenant_id=?",r.amount(),r.amount(),tenant);
        jdbc.update("UPDATE point_reservation SET status='RELEASED' WHERE id=? AND tenant_id=?",r.id(),tenant);
        ledger(tenant,"release:"+r.id(),"RELEASE",r.amount(),r);return reservation(tenant,task).orElseThrow();
    }
    @Transactional
    public Reservation refund(String tenant,String task) {
        lock(tenant);var r=reservation(tenant,task).orElseThrow(()->new BizException(1901,"积分预占记录不存在"));
        if("REFUNDED".equals(r.status()))return r;
        if(!"SETTLED".equals(r.status()))throw new BizException(1902,"仅已结算任务可以退款");
        jdbc.update("UPDATE point_wallet SET available=available+?,consumed=consumed-?,version=version+1 WHERE tenant_id=?",r.settled(),r.settled(),tenant);
        jdbc.update("UPDATE point_reservation SET status='REFUNDED' WHERE id=? AND tenant_id=?",r.id(),tenant);
        ledger(tenant,"refund:"+r.id(),"REFUND",r.settled(),r);return reservation(tenant,task).orElseThrow();
    }
    public List<Map<String,Object>> ledger(String tenant,int page,int size) {
        int limit=Math.clamp(size,1,100),offset=(Math.clamp(page,1,10000)-1)*limit;
        return jdbc.queryForList("SELECT event_type,amount,binding_id,actor_user_id,reservation_id,created_at FROM point_ledger WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",tenant,limit,offset);
    }
}
