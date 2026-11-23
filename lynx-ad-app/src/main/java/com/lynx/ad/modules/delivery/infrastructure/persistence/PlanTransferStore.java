package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Mode variants keep their own public IDs; the root groups them for transfer operations. */
@Repository
public class PlanTransferStore {
    public record Group(String root, String active, String conservative, String custom, String requestKey) {
        public String target(SmartBidType mode) { return mode == SmartBidType.CUSTOM ? custom : conservative; }
    }
    public record Attempt(String tenant, String key, String root, String source, String target, String request,
                          String payload, boolean sourceEnabled, String qcId, String status, String phase,
                          String error, Instant updatedAt) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public PlanTransferStore(JdbcTemplate jdbc, PlatformTransactionManager manager) { this.jdbc=jdbc;tx=new TransactionTemplate(manager); }
    public Map<String,Group> groups(TenantId tenant) {
        Map<String,Group> result=new HashMap<>();
        jdbc.query("SELECT * FROM delivery_transfer_group WHERE tenant_id=?", (org.springframework.jdbc.core.RowCallbackHandler)r->{
            var g=new Group(r.getString("root_plan_id"),r.getString("active_plan_id"),r.getString("conservative_plan_id"),r.getString("custom_plan_id"),r.getString("request_key"));
            result.put(g.root(),g);if(g.conservative()!=null)result.put(g.conservative(),g);if(g.custom()!=null)result.put(g.custom(),g);
        },tenant.value());return result;
    }
    public Group group(TenantId tenant, PlanId id) { return groups(tenant).get(id.value()); }
    public PlanId activeId(TenantId tenant, PlanId id) { var g=group(tenant,id);return g==null?id:new PlanId(g.active()); }
    public void assertIdle(TenantId tenant, PlanId id) {
        var g=group(tenant,id);if(g!=null && g.requestKey()!=null)throw new BizException(1523,"计划有关联操作正在处理或结果待核查，请先核查处理结果");
    }
    /** Called in a transaction; the same group lock protects transfer, enable and product removal. */
    public Group lockForProductChange(AdPlan source,String key) {
        var g=ensure(source);g=groupForUpdate(source.tenantId(),g.root());
        if(g==null || (g.requestKey()!=null && !key.equals(g.requestKey())))throw new BizException(1523,"计划有关联操作正在处理，请先核查处理结果");
        jdbc.update("UPDATE delivery_transfer_group SET request_key=?,updated_at=? WHERE tenant_id=? AND root_plan_id=?",key,now(),source.tenantId().value(),g.root());
        return g;
    }
    public void finishProductChange(TenantId tenant,String root,String key) {
        if(jdbc.update("UPDATE delivery_transfer_group SET request_key=NULL,updated_at=? WHERE tenant_id=? AND root_plan_id=? AND request_key=?",now(),tenant.value(),root,key)!=1)
            throw new BizException(1523,"商品变更关联已变化，请核查处理结果");
    }
    /** Called inside the delete transaction; serializes deletion with a transfer claim. */
    public void lockForDeletion(AdPlan plan) {
        var g=ensure(plan);
        var current=groupForUpdate(plan.tenantId(),g.root());
        if(current==null || current.requestKey()!=null)
            throw new BizException(1523,"转投正在处理或结果待核查，请先核查转投结果");
    }
    /** Keep the stable shared-material root, but never reuse a deleted mode member. */
    public void detachDeleted(TenantId tenant, PlanId id) {
        var g=group(tenant,id);if(g==null)return;
        g=groupForUpdate(tenant,g.root());if(g==null)return;
        if(g.requestKey()!=null)throw new BizException(1523,"转投正在处理或结果待核查，请先核查转投结果");
        String conservative=id.value().equals(g.conservative())?null:g.conservative();
        String custom=id.value().equals(g.custom())?null:g.custom();
        String active=id.value().equals(g.active())?(conservative!=null?conservative:custom!=null?custom:g.root()):g.active();
        if(jdbc.update("UPDATE delivery_transfer_group SET conservative_plan_id=?,custom_plan_id=?,active_plan_id=?,updated_at=? WHERE tenant_id=? AND root_plan_id=? AND request_key IS NULL",
                conservative,custom,active,now(),tenant.value(),g.root())!=1)
            throw new BizException(1523,"转投关联已变化，请同步核查删除结果");
    }
    private Group groupForUpdate(TenantId tenant,String root) {
        return jdbc.query("SELECT * FROM delivery_transfer_group WHERE tenant_id=? AND root_plan_id=? FOR UPDATE",
                (r,n)->new Group(r.getString("root_plan_id"),r.getString("active_plan_id"),r.getString("conservative_plan_id"),
                        r.getString("custom_plan_id"),r.getString("request_key")),tenant.value(),root).stream().findFirst().orElse(null);
    }
    public Group ensure(AdPlan source) {
        var existing=group(source.tenantId(),source.id());if(existing!=null)return existing;
        try {jdbc.update("INSERT INTO delivery_transfer_group(tenant_id,root_plan_id,active_plan_id,conservative_plan_id,custom_plan_id,updated_at) VALUES(?,?,?,?,?,?)",
                source.tenantId().value(),source.id().value(),source.id().value(),source.setting().bidType()==SmartBidType.CONSERVATIVE?source.id().value():null,
                source.setting().bidType()==SmartBidType.CUSTOM?source.id().value():null,now());}
        catch(org.springframework.dao.DuplicateKeyException ignored) {}
        return group(source.tenantId(),source.id());
    }
    /** Attach a user-confirmed historical transfer; never infer relationships from similar names. */
    public Group linkImported(AdPlan root,AdPlan current) {
        if(!root.tenantId().equals(current.tenantId()) || !root.accountId().equals(current.accountId()) || root.id().equals(current.id()) || root.setting().bidType()==current.setting().bidType())
            throw new BizException(1402,"转投关联须为同一账户下的两种投放方式");
        return tx.execute(s->{
            var locked=jdbc.queryForList("SELECT id FROM ad_plan WHERE tenant_id=? AND id IN (?,?) ORDER BY id FOR UPDATE",String.class,root.tenantId().value(),root.id().value(),current.id().value());
            if(locked.size()!=2)throw new BizException(1405,"计划不存在或不属于当前账户");
            var groups=groups(root.tenantId());var a=groups.get(root.id().value());var b=groups.get(current.id().value());
            if((a!=null && !a.root().equals(root.id().value())) || (b!=null && !b.root().equals(root.id().value())))throw new BizException(1404,"计划已有其他转投关联，请核查");
            if(a!=null && (a.requestKey()!=null || (a.target(current.setting().bidType())!=null && !a.target(current.setting().bidType()).equals(current.id().value()))))
                throw new BizException(1404,"计划正在转投或已有另一套计划，请先核查");
            String conservative=root.setting().bidType()==SmartBidType.CONSERVATIVE?root.id().value():current.id().value();
            String custom=root.setting().bidType()==SmartBidType.CUSTOM?root.id().value():current.id().value();
            if(a==null)jdbc.update("INSERT INTO delivery_transfer_group(tenant_id,root_plan_id,active_plan_id,conservative_plan_id,custom_plan_id,updated_at) VALUES(?,?,?,?,?,?)",
                    root.tenantId().value(),root.id().value(),current.id().value(),conservative,custom,now());
            else if(jdbc.update("UPDATE delivery_transfer_group SET active_plan_id=?,conservative_plan_id=?,custom_plan_id=?,updated_at=? WHERE tenant_id=? AND root_plan_id=? AND request_key IS NULL",
                    current.id().value(),conservative,custom,now(),root.tenantId().value(),root.id().value())!=1)
                throw new BizException(1404,"转投关联已变化，请刷新并核查");
            return group(root.tenantId(),root.id());
        });
    }
    public Optional<Attempt> attempt(TenantId tenant,String key) {
        return jdbc.query("SELECT * FROM delivery_transfer_attempt WHERE tenant_id=? AND request_key=?",(r,n)->new Attempt(tenant.value(),key,
                r.getString("root_plan_id"),r.getString("source_plan_id"),r.getString("target_plan_id"),r.getString("request_json"),r.getString("payload_json"),
                r.getInt("source_enabled")==1,r.getString("qc_plan_id"),r.getString("status"),r.getString("phase"),r.getString("error_message"),r.getTimestamp("updated_at").toInstant()),tenant.value(),key).stream().findFirst();
    }
    public void claim(Attempt a) {
        claim(a,a.source());
    }
    /** Compare the group version separately from the selected source; either mode can initiate a switch. */
    public void claim(Attempt a,String expectedActive) {
        tx.executeWithoutResult(s->{
            if(jdbc.update("UPDATE delivery_transfer_group SET request_key=?,updated_at=? WHERE tenant_id=? AND root_plan_id=? AND request_key IS NULL AND active_plan_id=? AND (conservative_plan_id=? OR custom_plan_id=?)",
                    a.key(),now(),a.tenant(),a.root(),expectedActive,a.source(),a.source())!=1)throw new BizException(1523,"计划已变化或转投正在处理，请刷新并核查");
            jdbc.update("INSERT INTO delivery_transfer_attempt(tenant_id,request_key,root_plan_id,source_plan_id,target_plan_id,request_json,payload_json,source_enabled,qc_plan_id,status,phase,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    a.tenant(),a.key(),a.root(),a.source(),a.target(),a.request(),a.payload(),a.sourceEnabled()?1:0,a.qcId(),"RUNNING","PREPARED",now(),now());
        });
    }
    public void phase(TenantId tenant,String key,String phase,String qcId) {
        jdbc.update("UPDATE delivery_transfer_attempt SET phase=?,qc_plan_id=COALESCE(?,qc_plan_id),updated_at=? WHERE tenant_id=? AND request_key=?",
                phase,qcId,now(),tenant.value(),key);
    }
    public List<Attempt> pending(TenantId tenant) {
        return jdbc.query("SELECT request_key FROM delivery_transfer_attempt WHERE tenant_id=? AND status IN ('RUNNING','UNCERTAIN')",(r,n)->r.getString(1),tenant.value())
                .stream().map(key->attempt(tenant,key).orElseThrow()).toList();
    }
    public void adoptTarget(TenantId tenant,String key,String id) {
        jdbc.update("UPDATE delivery_transfer_attempt SET target_plan_id=?,updated_at=? WHERE tenant_id=? AND request_key=?",id,now(),tenant.value(),key);
    }
    /** Follow a switch performed in Qianchuan only when exactly one known member is active. */
    public void reconcileActive(TenantId tenant,List<AdPlan> plans) {
        Map<String,AdPlan> byId=new HashMap<>();plans.forEach(p->byId.put(p.id().value(),p));
        for(var g:new HashSet<>(groups(tenant).values())) {
            if(g.requestKey()!=null || g.custom()==null || g.conservative()==null)continue;
            var a=byId.get(g.conservative());var b=byId.get(g.custom());if(a==null||b==null)continue;
            String active=a.status()==PlanStatus.PAUSED && enabled(b.status())?b.id().value():b.status()==PlanStatus.PAUSED && enabled(a.status())?a.id().value():null;
            if(active!=null)jdbc.update("UPDATE delivery_transfer_group SET active_plan_id=?,updated_at=? WHERE tenant_id=? AND root_plan_id=? AND request_key IS NULL",active,now(),tenant.value(),g.root());
        }
    }
    private static boolean enabled(PlanStatus status) { return status==PlanStatus.DELIVERING || status==PlanStatus.PENDING_AUDIT; }
    public void member(TenantId tenant,String root,SmartBidType mode,String target) {
        String column=mode==SmartBidType.CUSTOM?"custom_plan_id":"conservative_plan_id";
        jdbc.update("UPDATE delivery_transfer_group SET "+column+"=?,updated_at=? WHERE tenant_id=? AND root_plan_id=?",target,now(),tenant.value(),root);
    }
    public void finish(TenantId tenant,String key,String status,String error) {
        tx.executeWithoutResult(s->{
            var a=attempt(tenant,key).orElseThrow();
            jdbc.update("UPDATE delivery_transfer_attempt SET status=?,error_message=?,updated_at=? WHERE tenant_id=? AND request_key=?",status,
                    error==null?null:error.substring(0,Math.min(500,error.length())),now(),tenant.value(),key);
            if("SUCCEEDED".equals(status))jdbc.update("UPDATE delivery_transfer_group SET active_plan_id=?,request_key=NULL,updated_at=? WHERE tenant_id=? AND root_plan_id=? AND request_key=?",
                    a.target(),now(),tenant.value(),a.root(),key);
            else if("FAILED".equals(status))jdbc.update("UPDATE delivery_transfer_group SET request_key=NULL,updated_at=? WHERE tenant_id=? AND root_plan_id=? AND request_key=?",now(),tenant.value(),a.root(),key);
        });
    }
    private static Timestamp now() { return Timestamp.from(Instant.now()); }
}
