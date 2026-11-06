package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.billing.application.*;
import com.lynx.ad.modules.tenant.api.dto.LoginResponse;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import com.lynx.ad.common.config.LynxAdProperties;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceQueryService {
    private final JdbcTemplate jdbc; private final WorkspaceAccess access; private final WorkspaceFoundation foundation;
    private final UserRepository users; private final JwtService jwt; private final LynxAdProperties properties;
    private final EntitlementQueryService entitlements; private final PointWalletService points;
    public WorkspaceQueryService(JdbcTemplate jdbc,WorkspaceAccess access,WorkspaceFoundation foundation,UserRepository users,JwtService jwt,LynxAdProperties properties,EntitlementQueryService entitlements,PointWalletService points) {
        this.jdbc=jdbc;this.access=access;this.foundation=foundation;this.users=users;this.jwt=jwt;this.properties=properties;this.entitlements=entitlements;this.points=points;
    }
    public record Workspace(String tenantId,String name,String membershipId,String role) {}
    public List<Workspace> list(String user) {
        return jdbc.query("SELECT t.id,t.name,m.membership_id,m.member_role FROM sys_tenant t JOIN tenant_member m ON m.tenant_id=t.id JOIN sys_user u ON u.id=m.user_id WHERE m.user_id=? AND m.status='ACTIVE' AND t.status='ACTIVE' AND u.status='ACTIVE' ORDER BY m.created_at,t.id",(r,i)->new Workspace(r.getString("id"),r.getString("name"),r.getString("membership_id"),r.getString("member_role")),user);
    }
    public LoginResponse switchTo(String tenant,String user) {
        var member=access.member(tenant,user);
        var person=users.findById(new UserId(user)).orElseThrow(()->new BizException(401,"未登录"));
        return new LoginResponse(jwt.issueForUser(person,Optional.of(new TenantId(tenant)),member.role()),properties.getJwt().getTtlSec(),false,properties.isDemoMode());
    }
    @Transactional
    public Map<String,Object> overview() {
        var member=access.current();String tenant=member.tenantId();foundation.ensure(tenant);
        var result=new LinkedHashMap<String,Object>();
        result.put("tenantId",tenant);result.put("membershipId",member.id());result.put("role",member.role());
        result.put("name",jdbc.queryForObject("SELECT name FROM sys_tenant WHERE id=?",String.class,tenant));
        result.put("accountLimit",entitlements.adAccountLimit(new TenantId(tenant)));
        result.put("accountsUsed",jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE tenant_id=? AND status IN ('ACTIVE','REVIEW_REQUIRED')",Integer.class,tenant));
        result.put("clientSpaces",jdbc.queryForList("SELECT id,name,is_default FROM client_space WHERE tenant_id=? ORDER BY is_default DESC,name",tenant));
        if(member.role()==MemberRole.OWNER||member.role()==MemberRole.FINANCE)result.put("wallet",points.balance(tenant));
        return result;
    }
    public List<Map<String,Object>> bindings() {
        var m=access.current();boolean governance=m.role()==MemberRole.OWNER||m.role()==MemberRole.ADMIN;
        return jdbc.queryForList("SELECT b.id,b.bound_user_id,b.client_space_id,b.status,b.binding_version,a.platform,a.advertiser_id,a.account_name,a.status AS authorization_status,(SELECT COUNT(*) FROM account_grant_path p WHERE p.binding_id=b.id) AS authorization_paths FROM ad_account_binding b JOIN oauth_account a ON a.id=b.id AND a.tenant_id=b.tenant_id WHERE b.tenant_id=?"+(governance?"":" AND b.bound_user_id=?")+" ORDER BY a.authorized_at DESC",governance?new Object[]{m.tenantId()}:new Object[]{m.tenantId(),m.userId()});
    }
}
