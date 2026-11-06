package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceAccess {
    private final JdbcTemplate jdbc;
    public WorkspaceAccess(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public record Membership(String id,String tenantId,String userId,MemberRole role,long version) {}
    public Membership member(String tenant,String user) {
        return jdbc.query("SELECT m.* FROM tenant_member m JOIN sys_tenant t ON t.id=m.tenant_id JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.user_id=? AND m.status='ACTIVE' AND t.status='ACTIVE' AND u.status='ACTIVE'",(r,i)->new Membership(r.getString("membership_id"),tenant,user,MemberRole.valueOf(r.getString("member_role")),r.getLong("permission_version")),tenant,user).stream().findFirst().orElseThrow(()->new BizException(403,"工作空间成员身份已失效"));
    }
    public Membership current() {return member(TenantContext.requireTenantId().value(),TenantContext.userId().value());}
    /** Tenant-wide legacy list APIs cannot safely expose another responsible user's resources. */
    public boolean ownsBusinessScope(Membership member) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE tenant_id=? AND status IN ('ACTIVE','REVIEW_REQUIRED') AND (bound_user_id IS NULL OR bound_user_id<>?)",Integer.class,member.tenantId(),member.userId())==0;
    }
    public Membership require(MemberRole... roles) {
        var m=current();for(var role:roles)if(role==m.role())return m;
        throw new BizException(403,"当前成员没有此操作权限");
    }
    public boolean revoked(String session) {return session!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM workspace_session_revocation WHERE session_id=? AND expires_at>CURRENT_TIMESTAMP",Integer.class,session)>0;}
}
