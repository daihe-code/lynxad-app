package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.api.dto.PlanSettingsRequest;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.service.PlanEditVersion;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PlanSettingsEditStore {
    public record State(String version,String status,String message) {}
    public record Pending(String tenant,String planId,long revision,String status,String key,String root,String request,String verificationKey,Instant startedAt,Instant updatedAt) {}
    public record Claim(AdPlan plan,Pending pending) {}
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final AdPlanRepository plans;
    private final PlanTransferStore transfers;private final TransactionTemplate tx;
    public PlanSettingsEditStore(JdbcTemplate jdbc,ObjectMapper json,AdPlanRepository plans,PlanTransferStore transfers,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.json=json;this.plans=plans;this.transfers=transfers;this.tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Optional<Pending> pending(TenantId tenant,PlanId id){return row(tenant,id,false).filter(r->!"IDLE".equals(r.status()));}
    public State state(AdPlan p) {
        var row=row(p.tenantId(),p.id(),false).orElse(null);String status=row==null?"IDLE":row.status();
        return new State(PlanEditVersion.version(p,row==null?0:row.revision()),status,switch(status){
            case "PREPARING","SENDING"->"计划修改正在处理，请等待结果，勿重复提交";
            case "VERIFYING"->"正在只读核查上次修改结果";
            case "UNCERTAIN"->"上次修改结果待确认，请先核查千川当前设置，勿重复提交";
            default->null;
        });
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Claim claim(TenantId tenant,PlanId id,PlanSettingsRequest request) {
        if(request.expectedVersion()==null || !request.expectedVersion().matches("[0-9]{1,19}\\.[a-f0-9]{64}"))throw new BizException(1404,"页面缺少有效的计划版本，请重新读取后核对，当前输入可保留");
        var original=plans.findById(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在"));
        transfers.ensure(original);
        // Catch outside the transaction: PostgreSQL rolls back a transaction after a duplicate insert.
        try{tx.executeWithoutResult(s->jdbc.update("INSERT INTO delivery_plan_settings_edit(tenant_id,plan_id,revision,status,updated_at) VALUES(?,?,0,'IDLE',?)",tenant.value(),id.value(),now()));}
        catch(DuplicateKeyException exists){/* An independently connected App may have initialized it. */}
        return tx.execute(s->{
            String key="settings:"+UUID.randomUUID();var group=transfers.lockForProductChange(original,key);
            var plan=plans.findByIdForUpdate(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在"));
            var row=row(tenant,id,true).orElseThrow();
            if(!"IDLE".equals(row.status()))throw new BizException(1523,"上次计划修改尚未确认，请先核查结果");
            PlanEditVersion.require(request.expectedVersion(),plan,row.revision());
            String encoded;try{encoded=json.writeValueAsString(request);}catch(Exception e){throw new IllegalArgumentException(e);}
            jdbc.update("UPDATE delivery_plan_settings_edit SET status='PREPARING',request_key=?,root_plan_id=?,request_json=?,verification_key=NULL,started_at=?,updated_at=? WHERE tenant_id=? AND plan_id=?",key,group.root(),encoded,now(),now(),tenant.value(),id.value());
            return new Claim(plan,row(tenant,id,false).orElseThrow());
        });
    }
    public void dispatched(Pending p) {
        // Advance before the first possible platform mutation, even if its response is later lost.
        if(jdbc.update("UPDATE delivery_plan_settings_edit SET revision=revision+1,status='SENDING',started_at=?,updated_at=? WHERE tenant_id=? AND plan_id=? AND request_key=? AND status='PREPARING'",now(),now(),p.tenant(),p.planId(),p.key())!=1)
            throw new BizException(1523,"计划修改归属已变化，请核查处理结果");
    }
    public void uncertain(Pending p){jdbc.update("UPDATE delivery_plan_settings_edit SET status='UNCERTAIN',updated_at=? WHERE tenant_id=? AND plan_id=? AND request_key=? AND (verification_key=? OR verification_key IS NULL AND ? IS NULL)",now(),p.tenant(),p.planId(),p.key(),p.verificationKey(),p.verificationKey());}
    public void complete(Pending p,Runnable apply) {
        apply(p,apply,true);
    }
    public void observed(Pending p,Runnable apply) {apply(p,apply,false);}
    private void apply(Pending p,Runnable apply,boolean finish) {
        tx.executeWithoutResult(s->{
            var tenant=new TenantId(p.tenant());var id=new PlanId(p.planId());
            var plan=plans.findById(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在"));
            transfers.lockForProductChange(plan,p.key());
            plans.findByIdForUpdate(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在"));
            var current=row(new TenantId(p.tenant()),new PlanId(p.planId()),true).orElseThrow();
            if(!Objects.equals(p.key(),current.key())||!Objects.equals(p.verificationKey(),current.verificationKey()))throw new BizException(1523,"计划修改归属已变化，请核查处理结果");
            apply.run();
            if(!finish){uncertain(p);return;}
            transfers.finishProductChange(new TenantId(p.tenant()),p.root(),p.key());
            jdbc.update("UPDATE delivery_plan_settings_edit SET status='IDLE',request_key=NULL,root_plan_id=NULL,request_json=NULL,verification_key=NULL,started_at=NULL,updated_at=? WHERE tenant_id=? AND plan_id=? AND request_key=?",now(),p.tenant(),p.planId(),p.key());
        });
    }
    /** A delayed readback may update the cache only if no write started since the read began. */
    public void applyRead(AdPlan baseline,String expected,Runnable apply) {
        tx.executeWithoutResult(s->{
            var current=plans.findByIdForUpdate(baseline.tenantId(),baseline.id()).orElseThrow(()->new BizException(1405,"计划不存在"));
            var row=row(baseline.tenantId(),baseline.id(),true).orElse(null);
            if(row!=null&&!"IDLE".equals(row.status()))return;
            try{PlanEditVersion.require(expected,current,row==null?0:row.revision());}catch(BizException changed){return;}
            apply.run();
        });
    }
    /** An expired handler is never replayed. Recovery only reads platform state. */
    public Pending verification(TenantId tenant,PlanId id,String expected) {
        return tx.execute(s->{
            var p=row(tenant,id,true).orElseThrow(()->new BizException(1404,"没有待核查的计划修改，请重新读取计划"));
            var plan=plans.findById(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在"));
            PlanEditVersion.require(expected,plan,p.revision());
            if("IDLE".equals(p.status()))throw new BizException(1404,"修改结果已核查，请重新读取计划");
            if(!"UNCERTAIN".equals(p.status()) && p.updatedAt().plusSeconds("VERIFYING".equals(p.status())?90:600).isAfter(Instant.now()))
                throw new BizException(1523,"修改请求仍在处理中，请稍后核查，勿重复提交");
            jdbc.update("UPDATE delivery_plan_settings_edit SET status='VERIFYING',verification_key=?,updated_at=? WHERE tenant_id=? AND plan_id=? AND request_key=?",UUID.randomUUID().toString(),now(),tenant.value(),id.value(),p.key());
            return row(tenant,id,false).orElseThrow();
        });
    }
    public PlanSettingsRequest request(Pending p){try{return json.readValue(p.request(),PlanSettingsRequest.class);}catch(Exception e){throw new IllegalStateException("Invalid saved settings request",e);}}
    private Optional<Pending> row(TenantId tenant,PlanId id,boolean lock) {
        return jdbc.query("SELECT * FROM delivery_plan_settings_edit WHERE tenant_id=? AND plan_id=?"+(lock?" FOR UPDATE":""),(r,n)->new Pending(tenant.value(),id.value(),r.getLong("revision"),r.getString("status"),r.getString("request_key"),r.getString("root_plan_id"),r.getString("request_json"),r.getString("verification_key"),r.getTimestamp("started_at")==null?null:r.getTimestamp("started_at").toInstant(),r.getTimestamp("updated_at").toInstant()),tenant.value(),id.value()).stream().findFirst();
    }
    private static Timestamp now(){return Timestamp.from(Instant.now());}
}
