package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.tenant.api.dto.LoginResponse;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserLoginJournal {
    private final JdbcTemplate jdbc;private final JwtService jwt;private final UserRepository users;
    private InboxDeliveryJob deliveries;
    @org.springframework.beans.factory.annotation.Autowired public void deliveryJob(InboxDeliveryJob value){deliveries=value;}
    public UserLoginJournal(JdbcTemplate jdbc,JwtService jwt,UserRepository users) { this.jdbc=jdbc;this.jwt=jwt;this.users=users; }
    public void record(String phone,String method,LoginResponse login) {
        String safePhone=phone!=null&&phone.matches("1[0-9]{10}")?phone:null,user=null,tenant=null;
        if(login!=null) { var p=jwt.parse(login.token());user=p.userId().value();tenant=p.tenantId().map(i->i.value()).orElse(null); }
        else if(safePhone!=null)user=users.findByPhone(new PhoneNumber(safePhone)).map(u->u.id().value()).orElse(null);
        jdbc.update("INSERT INTO user_login_record(id,user_id,tenant_id,phone,login_method,result,failure_reason,ip,user_agent,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),user,tenant,safePhone,method,login==null?"FAILURE":"SUCCESS",login==null?("SMS".equals(method)?"验证码不正确或登录未完成":"账号或密码不正确，登录未完成"):null,
            clip(RequestMeta.ip().orElse(null),64),clip(RequestMeta.userAgent().orElse(null),512),ts(Instant.now()));
        if(login!=null && tenant!=null && deliveries!=null)try{deliveries.recoverAfterLogin(new com.lynx.ad.common.domain.Ids.TenantId(tenant));}catch(RuntimeException unavailable){/* Notification recovery must not fail an otherwise successful login. */}
    }
    private static String clip(String value,int length) { return value==null?null:value.substring(0,Math.min(value.length(),length)); }
    private static java.sql.Timestamp ts(Instant value) { return java.sql.Timestamp.from(value); }
}
