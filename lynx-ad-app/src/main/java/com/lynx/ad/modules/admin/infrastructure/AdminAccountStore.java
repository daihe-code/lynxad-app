package com.lynx.ad.modules.admin.infrastructure;

import com.lynx.ad.common.exception.BizException;
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
    public record Account(String id,String username,String displayName,String passwordHash,String status,int failedCount,
            Instant lockedUntil,Instant lastLoginAt,Instant createdAt) {
        @Override public String toString() { return "AdminAccount(id="+id+",username="+username+",status="+status+")"; }
    }
    private static final RowMapper<Account> ROW=(r,n)->new Account(r.getString("id"),r.getString("username"),r.getString("display_name"),r.getString("password_hash"),r.getString("status"),r.getInt("failed_count"),instant(r,"locked_until"),instant(r,"last_login_at"),instant(r,"created_at"));
    public Optional<Account> username(String name) { return jdbc.query("SELECT * FROM platform_admin_account WHERE username=?",ROW,name).stream().findFirst(); }
    public Optional<Account> account(String id) { return jdbc.query("SELECT * FROM platform_admin_account WHERE id=?",ROW,id).stream().findFirst(); }
    public boolean validSession(String admin,String id,Instant now) { return jdbc.queryForObject("SELECT COUNT(*) FROM platform_admin_session WHERE id=? AND admin_id=? AND revoked_at IS NULL AND expires_at>?",Integer.class,id,admin,ts(now))>0; }
    public long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM platform_admin_account",Long.class); }
    public void create(String name,String hash) { var now=Instant.now();jdbc.update("INSERT INTO platform_admin_account(id,username,display_name,password_hash,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),name,"平台管理员",hash,"ACTIVE",ts(now),ts(now)); }
    public void failed(Account a,Instant now) {
        jdbc.update("UPDATE platform_admin_account SET locked_until=CASE WHEN failed_count+1>=5 THEN ? ELSE locked_until END,failed_count=failed_count+1,updated_at=? WHERE id=?",ts(now.plusSeconds(600)),ts(now),a.id());
    }
    public void clearExpiredLock(String name,Instant now) { jdbc.update("UPDATE platform_admin_account SET failed_count=0,locked_until=NULL WHERE username=? AND locked_until<=?",name,ts(now)); }
    @Transactional public void login(Account checked,String session,Instant expires,Instant now) {
        var a=jdbc.query("SELECT * FROM platform_admin_account WHERE id=? FOR UPDATE",ROW,checked.id()).stream().findFirst().orElseThrow(()->new BizException(1901,"管理员账号或密码错误"));
        if(!"ACTIVE".equals(a.status()) || !a.passwordHash().equals(checked.passwordHash()) || a.lockedUntil()!=null&&a.lockedUntil().isAfter(now))throw new BizException(1901,"管理员账号或密码错误或暂时锁定");
        jdbc.update("UPDATE platform_admin_account SET failed_count=0,locked_until=NULL,last_login_at=?,updated_at=? WHERE id=?",ts(now),ts(now),a.id());
        jdbc.update("INSERT INTO platform_admin_session(id,admin_id,expires_at,created_at) VALUES(?,?,?,?)",session,a.id(),ts(expires),ts(now));
        audit(a,"LOGIN","SUCCESS",null,null,"管理员登录");
    }
    @Transactional public void logout(Account a,String session) { jdbc.update("UPDATE platform_admin_session SET revoked_at=? WHERE id=? AND admin_id=?",ts(Instant.now()),session,a.id());audit(a,"LOGOUT","SUCCESS",null,null,"管理员退出登录"); }
    @Transactional public void password(Account a,String hash) {
        if(jdbc.update("UPDATE platform_admin_account SET password_hash=?,failed_count=0,locked_until=NULL,updated_at=? WHERE id=? AND password_hash=?",hash,ts(Instant.now()),a.id(),a.passwordHash())!=1)throw new BizException(1903,"账号资料已更新，请重新登录");
        jdbc.update("UPDATE platform_admin_session SET revoked_at=? WHERE admin_id=? AND revoked_at IS NULL",ts(Instant.now()),a.id());
        audit(a,"PASSWORD_CHANGE","SUCCESS",null,null,"管理员修改密码并退出全部会话");
    }
    public void audit(Account a,String action,String result,String user,String tenant,String description) { audit(a==null?null:a.id(),a==null?"未知管理员":a.username(),action,result,user,tenant,description); }
    public void audit(String admin,String actor,String action,String result,String user,String tenant,String description) {
        jdbc.update("INSERT INTO platform_admin_audit(id,admin_id,actor_name,action_type,result,target_user_id,tenant_id,description,ip,user_agent,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),admin,clip(actor,64),action,result,user,tenant,clip(description,512),clip(RequestMeta.ip().orElse(null),64),clip(RequestMeta.userAgent().orElse(null),512),ts(Instant.now()));
    }
    public static String clip(String value,int length) { return value==null?null:value.substring(0,Math.min(value.length(),length)); }
    public static Instant instant(ResultSet r,String name)throws SQLException { var t=r.getTimestamp(name);return t==null?null:t.toInstant(); }
    public static Timestamp ts(Instant value) { return value==null?null:Timestamp.from(value); }
}
