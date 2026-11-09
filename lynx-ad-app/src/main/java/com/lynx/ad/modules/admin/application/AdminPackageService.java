package com.lynx.ad.modules.admin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.admin.infrastructure.*;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.lynx.ad.modules.admin.infrastructure.AdminAccountStore.*;

@Service
public class AdminPackageService {
    public record ExtendRequest(String requestId,String type,String planCode,int days,Instant expectedEnd,String reason) {}
    private record SubscriptionSnapshot(String code,String nextPlan,Instant end) {}
    private record Receipt(AdminUserQuery.Adjustment adjustment,String admin,String hash) {}
    private final AdminAuthService auth;private final AdminAccountStore admins;private final JdbcTemplate jdbc;private final ObjectMapper json;
    public AdminPackageService(AdminAuthService auth,AdminAccountStore admins,JdbcTemplate jdbc,ObjectMapper json) { this.auth=auth;this.admins=admins;this.jdbc=jdbc;this.json=json; }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AdminUserQuery.Adjustment extend(String user,ExtendRequest request) {
        var admin=auth.require();validate(request);
        var tenant=jdbc.query("SELECT tenant_id FROM tenant_member WHERE user_id=?",(r,n)->r.getString(1),user);
        if(tenant.size()!=1)throw new BizException(1904,"用户尚未创建灵投账户或账户归属不明确，暂不可延期");
        String id=tenant.getFirst();String hash=hash(user,request);
        // Serialize this account's manual grants; repeat submissions are re-read under the lock.
        jdbc.queryForObject("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE",String.class,id);
        var currentMembership=jdbc.query("SELECT tenant_id FROM tenant_member WHERE user_id=? AND status<>'REMOVED' FOR UPDATE",(r,n)->r.getString(1),user);
        if(currentMembership.size()!=1||!id.equals(currentMembership.getFirst()))throw new BizException(409,"用户的灵投账户归属已更新，请刷新后重新操作");
        var existing=jdbc.query("SELECT * FROM platform_package_adjustment WHERE request_id=? FOR UPDATE",(r,n)->new Receipt(AdminUserQuery.ADJUSTMENT.mapRow(r,n),r.getString("admin_id"),r.getString("request_hash")),request.requestId()).stream().findFirst().orElse(null);
        if(existing!=null){if(!admin.id().equals(existing.admin())||!hash.equals(existing.hash()))throw new BizException(409,"该请求已用于其他操作，请重新确认");return existing.adjustment();}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM billing_order WHERE tenant_id=? AND status='PENDING_PAY'",Integer.class,id)>0)throw new BizException(1904,"用户有未完成的套餐支付订单，请处理订单后再延期");
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),before,after;String code;
        if("TRIAL".equals(request.type())) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM billing_subscription WHERE tenant_id=? AND status IN ('ACTIVE','PENDING_DOWNGRADE') AND period_end>?",Integer.class,id,ts(now))>0)throw new BizException(1904,"当前已有生效套餐，请选择套餐延期");
            var rows=jdbc.query("SELECT expires_at FROM billing_trial WHERE tenant_id=? FOR UPDATE",(r,n)->instant(r,"expires_at"),id);before=rows.isEmpty()?null:rows.getFirst();checkExpected(before,request.expectedEnd());after=end(before,now,request.days());code="PROFESSIONAL";
            if(before==null)jdbc.update("INSERT INTO billing_trial(tenant_id,started_at,expires_at) VALUES(?,?,?)",id,ts(now),ts(after));
            else if(jdbc.update("UPDATE billing_trial SET expires_at=? WHERE tenant_id=? AND expires_at=?",ts(after),id,ts(before))!=1)throw conflict();
        } else {
            var rows=jdbc.query("SELECT * FROM billing_subscription WHERE tenant_id=? FOR UPDATE",(r,n)->new SubscriptionSnapshot(r.getString("plan_code"),r.getString("next_plan_code"),instant(r,"period_end")),id);var sub=rows.isEmpty()?null:rows.getFirst();
            before=sub==null?null:sub.end();checkExpected(before,request.expectedEnd());
            code=request.planCode();if(sub!=null&&!code.equals(sub.code()))throw new BizException(1904,"延期保持当前套餐类型，请重新加载后操作");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM billing_plan WHERE code=?",Integer.class,code)!=1)throw new BizException(1904,"套餐不存在");after=end(before,now,request.days());
            if(sub==null)jdbc.update("INSERT INTO billing_subscription(tenant_id,plan_code,status,period_start,period_end,auto_renew,pay_method,paid_fen_this_period,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",id,code,"ACTIVE",ts(now),ts(after),false,"ADMIN",0,ts(now));
            else {String status=sub.nextPlan()!=null?"PENDING_DOWNGRADE":"ACTIVE";if(jdbc.update("UPDATE billing_subscription SET period_end=?,status=?,updated_at=? WHERE tenant_id=? AND period_end=? AND plan_code=?",ts(after),status,ts(now),id,ts(before),code)!=1)throw conflict();}
        }
        jdbc.update("INSERT INTO platform_package_adjustment(request_id,request_hash,admin_id,actor_name,user_id,tenant_id,grant_type,plan_code,days,before_end,after_end,reason,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            request.requestId(),hash,admin.id(),admin.username(),user,id,request.type(),code,request.days(),ts(before),ts(after),request.reason().trim(),ts(now));
        admins.audit(admin,"PACKAGE_EXTEND","SUCCESS",user,id,request.type()+" 延长 "+request.days()+" 天；"+request.reason().trim());
        return new AdminUserQuery.Adjustment(request.requestId(),admin.username(),user,id,request.type(),code,request.days(),before,after,request.reason().trim(),now);
    }
    private static Instant end(Instant before,Instant now,int days) { return (before!=null&&before.isAfter(now)?before:now).plusSeconds(days*86400L); }
    private static void checkExpected(Instant actual,Instant expected) { if(!Objects.equals(actual,expected))throw conflict(); }
    private static BizException conflict() { return new BizException(409,"套餐到期时间已被更新，请刷新用户资料后重新确认"); }
    private static void validate(ExtendRequest r) {
        if(r==null||r.requestId()==null||!r.requestId().matches("[a-fA-F0-9-]{36}")||!List.of("TRIAL","SUBSCRIPTION").contains(Objects.toString(r.type(),""))||r.days()<1||r.days()>3650||r.reason()==null||r.reason().trim().length()<2||r.reason().length()>300)throw new BizException(1904,"请填写有效请求、1至3650天的延长时长和2至300字操作原因");
        try {UUID.fromString(r.requestId());if("SUBSCRIPTION".equals(r.type()))PlanCode.valueOf(r.planCode());}catch(RuntimeException invalid){throw new BizException(1904,"请求标识或套餐类型不正确");}
    }
    private String hash(String user,ExtendRequest r) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(List.of(user,r.type(),Objects.toString(r.planCode(),""),r.days(),Objects.toString(r.expectedEnd(),""),r.reason().trim()))));}catch(Exception error){throw new IllegalStateException("延期请求无法编码");} }
}
