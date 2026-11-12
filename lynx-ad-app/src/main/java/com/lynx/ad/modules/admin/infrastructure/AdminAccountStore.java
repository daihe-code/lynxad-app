package com.lynx.ad.modules.admin.infrastructure;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.security.RequestMeta;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminAccountStore {
    private final JdbcTemplate jdbc;
    public AdminAccountStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Account(String id, String username, String displayName, String status, Instant lastLoginAt,
            String phone, boolean canManageAdmins) {
        @Override public String toString() { return "AdminAccount(id="+id+",username="+username+",status="+status+")"; }
    }
    private static final RowMapper<Account> ROW = (r,n) -> new Account(r.getString("id"), r.getString("username"),
            r.getString("display_name"), r.getString("status"), instant(r, "last_login_at"), r.getString("phone"),
            r.getBoolean("can_manage_admins"));
    public Optional<Account> activePhone(PhoneNumber phone) {
        return jdbc.query("SELECT * FROM platform_admin_account WHERE phone=? AND status='ACTIVE'", ROW, phone.value())
                .stream().findFirst();
    }
    public List<Account> phoneAdmins() {
        return jdbc.query("SELECT * FROM platform_admin_account WHERE phone IS NOT NULL ORDER BY can_manage_admins DESC, created_at, id", ROW);
    }
    public Optional<Account> account(String id) { return jdbc.query("SELECT * FROM platform_admin_account WHERE id=?",ROW,id).stream().findFirst(); }
    public boolean validSession(String admin,String id,Instant now) { return jdbc.queryForObject("SELECT COUNT(*) FROM platform_admin_session WHERE id=? AND admin_id=? AND revoked_at IS NULL AND expires_at>?",Integer.class,id,admin,ts(now))>0; }
    @Transactional
    public Account grantPhone(Account grantor, PhoneNumber phone) {
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        String username = "+86 " + phone.value();
        String displayName = "平台管理员";
        jdbc.update("""
                INSERT INTO platform_admin_account(id,username,display_name,status,phone,created_at,updated_at)
                VALUES(?,?,?,'ACTIVE',?,?,?)
                """, id, username, displayName, phone.value(), ts(now), ts(now));
        audit(grantor, "ADMIN_PHONE_GRANT", "SUCCESS", null, null, "授权管理员手机号 " + phone.value());
        return new Account(id, username, displayName, "ACTIVE", null, phone.value(), false);
    }
    @Transactional public void login(Account checked,String session,Instant expires,Instant now) {
        int updated = jdbc.update("""
                UPDATE platform_admin_account SET last_login_at=?,updated_at=?
                WHERE id=? AND phone=? AND status='ACTIVE'
                """, ts(now), ts(now), checked.id(), checked.phone());
        if (updated != 1) throw new BizException(1901, "该手机号未获管理员授权");
        jdbc.update("INSERT INTO platform_admin_session(id,admin_id,expires_at,created_at) VALUES(?,?,?,?)",session,checked.id(),ts(expires),ts(now));
        audit(checked,"LOGIN","SUCCESS",null,null,"管理员验证码登录");
    }
    @Transactional public void logout(Account a,String session) { jdbc.update("UPDATE platform_admin_session SET revoked_at=? WHERE id=? AND admin_id=?",ts(Instant.now()),session,a.id());audit(a,"LOGOUT","SUCCESS",null,null,"管理员退出登录"); }
    public void audit(Account a,String action,String result,String user,String tenant,String description) { audit(a==null?null:a.id(),a==null?"未知管理员":a.username(),action,result,user,tenant,description); }
    public void audit(String admin,String actor,String action,String result,String user,String tenant,String description) {
        jdbc.update("INSERT INTO platform_admin_audit(id,admin_id,actor_name,action_type,result,target_user_id,tenant_id,description,ip,user_agent,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),admin,clip(actor,64),action,result,user,tenant,clip(description,512),clip(RequestMeta.ip().orElse(null),64),clip(RequestMeta.userAgent().orElse(null),512),ts(Instant.now()));
    }
    public static String clip(String value,int length) { return value==null?null:value.substring(0,Math.min(value.length(),length)); }
    public static Instant instant(ResultSet r,String name)throws SQLException { var t=r.getTimestamp(name);return t==null?null:t.toInstant(); }
    public static Timestamp ts(Instant value) { return value==null?null:Timestamp.from(value); }
}
