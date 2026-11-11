package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceAccess {
    private final JdbcTemplate jdbc;
    public WorkspaceAccess(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public record Membership(String id,String tenantId,String userId,MemberRole role,long version,List<MemberRole> roles) {
        public Membership(String id,String tenantId,String userId,MemberRole role,long version) {
            this(id,tenantId,userId,role,version,List.of(role));
        }
    }
    public Membership member(String tenant,String user) {
        return jdbc.query("SELECT m.* FROM tenant_member m JOIN sys_tenant t ON t.id=m.tenant_id JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.user_id=? AND m.status='ACTIVE' AND t.status='ACTIVE' AND u.status='ACTIVE'",(r,i)->{
            var primary = MemberRole.valueOf(r.getString("member_role"));
            return new Membership(r.getString("membership_id"),tenant,user,primary,r.getLong("permission_version"),
                WorkspaceChildRoles.read(r.getString("child_roles"), primary));
        },tenant,user).stream().findFirst().orElseThrow(()->new BizException(403,"工作空间成员身份已失效"));
    }
    public Membership current() {return member(TenantContext.requireTenantId().value(),TenantContext.userId().value());}
    public boolean canReadBinding(String tenant,String binding) {
        return readableBindings(tenant).map(ids -> binding!=null && ids.contains(binding)).orElse(true);
    }
    /** No workspace-wide view for operators. Read-only members are granted reporting by the owner. */
    public java.util.Optional<java.util.Set<String>> readableBindings(String tenant) {
        if(TenantContext.current().isEmpty())return java.util.Optional.empty();
        var principal=TenantContext.require();
        if(principal.sysRole()!=SysRole.USER)return java.util.Optional.empty();
        if(principal.tenantId().filter(id->tenant.equals(id.value())).isEmpty())return java.util.Optional.of(java.util.Set.of());
        var member=principal.memberRole().orElse(null);
        if(member==MemberRole.OWNER||member==MemberRole.VIEWER)return java.util.Optional.empty();
        return java.util.Optional.of(java.util.Set.copyOf(jdbc.queryForList(
                "SELECT id FROM ad_account_binding WHERE tenant_id=? AND bound_user_id=?",String.class,tenant,principal.userId().value())));
    }
    public void requireReadableBinding(String tenant,String binding) {
        if(!canReadBinding(tenant,binding))throw new BizException(403,"当前成员没有此广告账户的访问权限");
    }
    /** Tenant-wide legacy list APIs cannot safely expose another responsible user's resources. */
    public boolean ownsBusinessScope(Membership member) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE tenant_id=? AND status IN ('ACTIVE','REVIEW_REQUIRED') AND (bound_user_id IS NULL OR bound_user_id<>?)",Integer.class,member.tenantId(),member.userId())==0;
    }
    public Membership require(MemberRole... roles) {
        var m=current();for(var role:roles)if(m.roles().contains(role))return m;
        throw new BizException(403,"当前成员没有此操作权限");
    }
    public boolean revoked(String session) {return session!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM workspace_session_revocation WHERE session_id=? AND expires_at>CURRENT_TIMESTAMP",Integer.class,session)>0;}
}
