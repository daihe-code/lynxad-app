package com.lynx.ad.modules.admin.application;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {
    private final AdminAccountStore accounts;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final long ttl;
    private final String dummyHash;
    private final ConcurrentHashMap<String,Attempt> attempts=new ConcurrentHashMap<>();
    private record Attempt(int count,Instant until) {}
    public record Me(String id,String username,String displayName,Instant lastLoginAt) {}
    public record Login(String token,long expiresIn,Me admin) { @Override public String toString(){return "AdminLogin(expiresIn="+expiresIn+")";} }
    public AdminAuthService(AdminAccountStore accounts,PasswordEncoder passwords,JwtService jwt,LynxAdProperties properties) {
        this.accounts=accounts;this.passwords=passwords;this.jwt=jwt;this.ttl=Math.min(7200,properties.getJwt().getTtlSec());
        dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    public Login login(String rawName,String password) {
        String name=rawName==null?"":rawName.trim();
        if(!name.matches("[a-zA-Z0-9_.@-]{3,64}") || password==null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw badLogin();
        var now=Instant.now();attempts.entrySet().removeIf(e->!e.getValue().until().isAfter(now));
        var attempt=attempts.get(name);if(attempt!=null&&attempt.count()>=5)throw badLogin();
        accounts.clearExpiredLock(name,now);var a=accounts.username(name).orElse(null);
        if(a==null||!"ACTIVE".equals(a.status())||a.lockedUntil()!=null&&a.lockedUntil().isAfter(now)||!passwords.matches(password,a.passwordHash())) {
            if(a==null)passwords.matches(password,dummyHash);
            if(a!=null)accounts.failed(a,now);
            if(attempts.size()>1000)attempts.clear();attempts.compute(name,(k,v)->new Attempt(v==null?1:v.count()+1,now.plusSeconds(600)));
            accounts.audit(a==null?null:a.id(),name,"LOGIN","FAILURE",null,null,"管理员账号或密码错误或暂时锁定");
            throw badLogin();
        }
        String token=jwt.issue(new UserId(a.id()),Optional.empty(),null,SysRole.ADMIN);
        String session=jwt.parse(token).sessionId().orElseThrow();accounts.login(a,session,now.plusSeconds(ttl),now);attempts.remove(name);
        return new Login(token,ttl,new Me(a.id(),a.username(),a.displayName(),now));
    }
    public AdminAccountStore.Account require() {
        var p=TenantContext.require();
        if(p.sysRole()!=SysRole.ADMIN)throw new BizException(403,"仅平台管理员可访问");
        var a=accounts.account(p.userId().value()).orElseThrow(()->new BizException(403,"管理员身份无效"));
        if(!"ACTIVE".equals(a.status())||p.sessionId().isEmpty()||!accounts.validSession(a.id(),p.sessionId().get(),Instant.now()))throw new BizException(401,"管理员登录已失效，请重新登录");
        return a;
    }
    public Me me() { var a=require();return new Me(a.id(),a.username(),a.displayName(),a.lastLoginAt()); }
    public void logout() { accounts.logout(require(),TenantContext.require().sessionId().orElseThrow()); }
    public void changePassword(String old,String next) {
        var a=require();validatePassword(next);
        if(old==null||old.length()>72||!passwords.matches(old,a.passwordHash()))throw new BizException(1902,"原管理员密码不正确");
        accounts.password(a,passwords.encode(next));
    }
    public static void validatePassword(String password) {
        if(password==null||password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||!password.matches(".*[a-zA-Z].*")||!password.matches(".*[0-9].*"))throw new BizException(1902,"管理员密码须为12至72字节，至少包含字母和数字");
    }
    private static BizException badLogin() { return new BizException(1901,"管理员账号或密码错误，连续失败后请10分钟后重试"); }
}
