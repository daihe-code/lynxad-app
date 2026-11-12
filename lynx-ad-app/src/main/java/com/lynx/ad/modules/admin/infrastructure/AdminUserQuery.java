package com.lynx.ad.modules.admin.infrastructure;

import com.lynx.ad.common.exception.BizException;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import static com.lynx.ad.modules.admin.infrastructure.AdminAccountStore.*;

/** Cross-tenant read models. Entry points are restricted by AdminAccessConfiguration. */
@Repository
public class AdminUserQuery {
    private final JdbcTemplate jdbc;
    public AdminUserQuery(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Page<T>(List<T> records,long total,int pageNum,int pageSize) {}
    public record UserRow(String id,String phone,String nickname,String email,String status,Instant registeredAt,boolean hasPassword,
            String companyName,String industry,String tenantId,String tenantName,String memberRole,String packageType,String packageStatus,
            String planCode,Instant expiresAt,Instant lastLoginAt,long loginCount) {}
    public record Profile(String avatarUrl,String companyScale,String position,String monthlyBudget,String adExperience,String coreDemand,Instant completedAt) {}
    public record Package(String planCode,String status,Instant startedAt,Instant expiresAt,Boolean autoRenew,String payMethod,String nextPlanCode,Instant updatedAt) {}
    public record Detail(UserRow user,Profile profile,Package subscription,Package trial,long memberCount,long adAccounts,long adPlans) {}
    public record LoginRow(String id,String userId,String phone,String nickname,String method,String result,String failureReason,String ip,String userAgent,Instant createdAt) {}
    public record Adjustment(String requestId,String actorName,String userId,String tenantId,String type,String planCode,int days,Instant beforeEnd,Instant afterEnd,String reason,Instant createdAt) {}
    public record AdminAudit(String id,String actorName,String action,String result,String userId,String tenantId,String description,String ip,String userAgent,Instant createdAt) {}
    public record Overview(long users,long accounts,long activePackages,long activeTrials,long todayLogins) {}
    private static final String JOIN=" FROM sys_user u LEFT JOIN user_profile p ON p.user_id=u.id LEFT JOIN tenant_member m ON m.user_id=u.id AND m.status<>'REMOVED' LEFT JOIN sys_tenant t ON t.id=m.tenant_id AND t.status='ACTIVE' LEFT JOIN billing_subscription s ON s.tenant_id=t.id LEFT JOIN billing_trial b ON b.tenant_id=t.id";
    private static final String STATUS="CASE WHEN s.status IN ('ACTIVE','PENDING_DOWNGRADE') AND s.period_end>? THEN 'ACTIVE' WHEN b.expires_at>? THEN 'TRIAL' WHEN s.tenant_id IS NOT NULL OR b.tenant_id IS NOT NULL THEN 'EXPIRED' ELSE 'NONE' END";
    private static final String SELECT="SELECT u.id,u.phone,u.status,u.created_at AS registered_at,CASE WHEN u.password_hash IS NOT NULL AND u.password_hash<>'' THEN 1 ELSE 0 END AS has_password,p.nickname,p.email,p.company_name,p.industry,t.id AS tenant_id,t.name AS tenant_name,m.member_role,s.plan_code,s.status AS sub_status,s.period_end,b.expires_at AS trial_end,(SELECT MAX(l.created_at) FROM user_login_record l WHERE l.user_id=u.id AND l.result='SUCCESS') AS last_login,(SELECT COUNT(*) FROM user_login_record l WHERE l.user_id=u.id AND l.result='SUCCESS') AS login_count";
    private UserRow row(ResultSet r)throws SQLException {
        Instant now=Instant.now(),paid=instant(r,"period_end"),trial=instant(r,"trial_end");
        boolean activePaid=paid!=null&&paid.isAfter(now)&&List.of("ACTIVE","PENDING_DOWNGRADE").contains(Objects.toString(r.getString("sub_status"),""));
        boolean activeTrial=!activePaid&&trial!=null&&trial.isAfter(now);
        String kind=activePaid?"SUBSCRIPTION":activeTrial?"TRIAL":paid!=null?"SUBSCRIPTION":trial!=null?"TRIAL":"NONE";
        return new UserRow(r.getString("id"),r.getString("phone"),r.getString("nickname"),r.getString("email"),r.getString("status"),instant(r,"registered_at"),r.getBoolean("has_password"),
            r.getString("company_name"),r.getString("industry"),r.getString("tenant_id"),r.getString("tenant_name"),r.getString("member_role"),kind,activePaid?"ACTIVE":activeTrial?"TRIAL":!"NONE".equals(kind)?"EXPIRED":"NONE",
            "SUBSCRIPTION".equals(kind)?r.getString("plan_code"):"TRIAL".equals(kind)?"PROFESSIONAL":null,"SUBSCRIPTION".equals(kind)?paid:trial,instant(r,"last_login"),r.getLong("login_count"));
    }
    public Page<UserRow> users(String q,String state,int page,int size) {
        page=page(page);size=size(size);var args=new ArrayList<Object>();StringBuilder where=new StringBuilder(" WHERE u.status<>'DELETED'");
        if(q!=null&&!q.isBlank()){String term="%"+q.trim()+"%";where.append(" AND (u.phone LIKE ? OR p.nickname LIKE ? OR p.company_name LIKE ? OR t.name LIKE ? OR u.id=?)");Collections.addAll(args,term,term,term,term,q.trim());}
        if(state!=null&&!state.isBlank()){if(!List.of("ACTIVE","TRIAL","EXPIRED","NONE").contains(state))throw new BizException(1904,"套餐状态不正确");where.append(" AND (").append(STATUS).append(")=?");args.add(ts(Instant.now()));args.add(ts(Instant.now()));args.add(state);}
        long total=jdbc.queryForObject("SELECT COUNT(*)"+JOIN+where,Long.class,args.toArray());args.add(size);args.add((page-1)*size);
        return new Page<>(jdbc.query(SELECT+JOIN+where+" ORDER BY u.created_at DESC,u.id,t.id LIMIT ? OFFSET ?",(r,n)->row(r),args.toArray()),total,page,size);
    }
    public Detail detail(String id) {
        return detail(id, null);
    }
    public Detail detail(String id, String tenantId) {
        var rows = tenantId == null
                ? jdbc.query(SELECT+JOIN+" WHERE u.id=? ORDER BY CASE WHEN m.member_role='OWNER' THEN 0 ELSE 1 END,t.id", (r,n)->row(r), id)
                : jdbc.query(SELECT+JOIN+" WHERE u.id=? AND t.id=? AND u.status<>'DELETED'", (r,n)->row(r), id, tenantId);
        var user = rows.stream().findFirst().orElseThrow(() -> new BizException(1904,
                tenantId == null ? "用户不存在" : "该用户不属于所选坤境账户"));
        var profile=jdbc.query("SELECT * FROM user_profile WHERE user_id=?",(r,n)->new Profile(r.getString("avatar_url"),r.getString("company_scale"),r.getString("position"),r.getString("monthly_budget"),r.getString("ad_experience"),r.getString("core_demand"),instant(r,"completed_at")),id).stream().findFirst().orElse(null);
        String tenant=user.tenantId();Package subscription=null,trial=null;long members=0,ads=0,plans=0;
        if(tenant!=null){
            subscription=jdbc.query("SELECT * FROM billing_subscription WHERE tenant_id=?",(r,n)->new Package(r.getString("plan_code"),r.getString("status"),instant(r,"period_start"),instant(r,"period_end"),r.getBoolean("auto_renew"),r.getString("pay_method"),r.getString("next_plan_code"),instant(r,"updated_at")),tenant).stream().findFirst().orElse(null);
            trial=jdbc.query("SELECT * FROM billing_trial WHERE tenant_id=?",(r,n)->new Package("PROFESSIONAL",r.getTimestamp("expires_at").toInstant().isAfter(Instant.now())?"TRIAL":"EXPIRED",instant(r,"started_at"),instant(r,"expires_at"),null,null,null,null),tenant).stream().findFirst().orElse(null);
            members=count("tenant_member",tenant);ads=count("oauth_account",tenant);plans=count("ad_plan",tenant);
        }
        return new Detail(user,profile,subscription,trial,members,ads,plans);
    }
    private long count(String table,String tenant) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=?",Long.class,tenant); }
    public Overview overview() {
        var now=Instant.now();var today=LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        return new Overview(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE status<>'DELETED'",Long.class),jdbc.queryForObject("SELECT COUNT(*) FROM sys_tenant WHERE status<>'DELETED'",Long.class),
            jdbc.queryForObject("SELECT COUNT(*) FROM billing_subscription WHERE status IN ('ACTIVE','PENDING_DOWNGRADE') AND period_end>?",Long.class,ts(now)),
            jdbc.queryForObject("SELECT COUNT(*) FROM billing_trial b JOIN sys_tenant t ON t.id=b.tenant_id WHERE t.status<>'DELETED' AND b.expires_at>? AND NOT EXISTS (SELECT 1 FROM billing_subscription s WHERE s.tenant_id=b.tenant_id AND s.status IN ('ACTIVE','PENDING_DOWNGRADE') AND s.period_end>?)",Long.class,ts(now),ts(now)),
            jdbc.queryForObject("SELECT COUNT(*) FROM user_login_record WHERE result='SUCCESS' AND created_at>=?",Long.class,ts(today)));
    }
    public Page<LoginRow> logins(String user,String q,String result,LocalDate from,LocalDate to,int page,int size) {
        page=page(page);size=size(size);var args=new ArrayList<Object>();var where=new StringBuilder(" WHERE 1=1");
        if(user!=null&&!user.isBlank()){where.append(" AND l.user_id=?");args.add(user);}
        if(q!=null&&!q.isBlank()){where.append(" AND (l.phone LIKE ? OR p.nickname LIKE ?)");String term="%"+q.trim()+"%";args.add(term);args.add(term);}
        if(result!=null&&!result.isBlank()){if(!List.of("SUCCESS","FAILURE").contains(result))throw new BizException(1904,"登录结果不正确");where.append(" AND l.result=?");args.add(result);}
        if(from!=null&&to!=null&&(to.isBefore(from)||to.toEpochDay()-from.toEpochDay()>366))throw new BizException(1904,"日志日期范围最长366天");
        if(from!=null){where.append(" AND l.created_at>=?");args.add(ts(from.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));}
        if(to!=null){where.append(" AND l.created_at<?");args.add(ts(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));}
        String join=" FROM user_login_record l LEFT JOIN user_profile p ON p.user_id=l.user_id";
        long total=jdbc.queryForObject("SELECT COUNT(*)"+join+where,Long.class,args.toArray());args.add(size);args.add((page-1)*size);
        return new Page<>(jdbc.query("SELECT l.*,p.nickname"+join+where+" ORDER BY l.created_at DESC,l.id DESC LIMIT ? OFFSET ?",(r,n)->new LoginRow(r.getString("id"),r.getString("user_id"),r.getString("phone"),r.getString("nickname"),r.getString("login_method"),r.getString("result"),r.getString("failure_reason"),r.getString("ip"),r.getString("user_agent"),instant(r,"created_at")),args.toArray()),total,page,size);
    }
    public Page<Adjustment> adjustments(String user,int page,int size) {
        page=page(page);size=size(size);var args=new ArrayList<Object>();String where="";
        if(user!=null&&!user.isBlank()){where=" WHERE tenant_id IN (SELECT tenant_id FROM tenant_member WHERE user_id=?)";args.add(user);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM platform_package_adjustment"+where,Long.class,args.toArray());args.add(size);args.add((page-1)*size);
        return new Page<>(jdbc.query("SELECT * FROM platform_package_adjustment"+where+" ORDER BY created_at DESC,request_id DESC LIMIT ? OFFSET ?",ADJUSTMENT,args.toArray()),total,page,size);
    }
    public static final RowMapper<Adjustment> ADJUSTMENT=(r,n)->new Adjustment(r.getString("request_id"),r.getString("actor_name"),r.getString("user_id"),r.getString("tenant_id"),r.getString("grant_type"),r.getString("plan_code"),r.getInt("days"),instant(r,"before_end"),instant(r,"after_end"),r.getString("reason"),instant(r,"created_at"));
    public Page<AdminAudit> audit(int page,int size) {
        page=page(page);size=size(size);
        return new Page<>(jdbc.query("SELECT * FROM platform_admin_audit ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(r,n)->new AdminAudit(r.getString("id"),r.getString("actor_name"),r.getString("action_type"),r.getString("result"),r.getString("target_user_id"),r.getString("tenant_id"),r.getString("description"),r.getString("ip"),r.getString("user_agent"),instant(r,"created_at")),size,(page-1)*size),jdbc.queryForObject("SELECT COUNT(*) FROM platform_admin_audit",Long.class),page,size);
    }
    static int page(int value) { if(value<1||value>100000)throw new BizException(1904,"页码不正确");return value; }
    static int size(int value) { if(value<1||value>100)throw new BizException(1904,"每页数量须为1至100");return value; }
}
