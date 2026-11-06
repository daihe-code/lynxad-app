package com.lynx.ad.common.security;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.tenant.application.WorkspaceAccess;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Revalidates membership on every request, including when a JWT still has its old role. */
@Component
public class WorkspaceRequestGuard implements HandlerInterceptor,WebMvcConfigurer {
    private final WorkspaceAccess access;
    public WorkspaceRequestGuard(WorkspaceAccess access) {this.access=access;}
    @Override public void addInterceptors(InterceptorRegistry registry) {registry.addInterceptor(this).addPathPatterns("/api/v1/**");}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if(TenantContext.current().isEmpty())return true;
        var principal=TenantContext.require();
        if(principal.sysRole()!=SysRole.USER)return true; // Platform admin APIs retain their separate authorization checks.
        if(access.revoked(principal.sessionId().orElse(null)))throw new BizException(401,"登录已退出，请重新登录");
        if(TenantContext.tenantId().isEmpty())return true;
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(path.startsWith("/api/v1/tenant/workspaces")||path.equals("/api/v1/tenant/auth/logout"))return true;
        var member=access.current();
        TenantContext.set(new TenantPrincipal(principal.userId(),principal.tenantId(),java.util.Optional.of(member.role()),principal.sysRole(),principal.sessionId()));
        if(path.startsWith("/api/v1/billing")) {
            if(member.role()!=MemberRole.OWNER&&member.role()!=MemberRole.FINANCE)throw new BizException(403,"没有财务权限");
        } else if(path.startsWith("/api/v1/tenant/members")) {
            if(member.role()!=MemberRole.OWNER&&member.role()!=MemberRole.ADMIN)throw new BizException(403,"没有成员管理权限");
        } else if(!path.startsWith("/api/v1/tenant/")&&member.role()!=MemberRole.OWNER) {
            // Fine-grained multi-member collaboration is not a v1 entitlement. Fail closed rather than expose tenant-wide data.
            if(member.role()!=MemberRole.MEMBER&&member.role()!=MemberRole.OPERATOR)throw new BizException(403,"当前成员没有业务资源权限");
            if(!access.ownsBusinessScope(member))throw new BizException(403,"当前成员没有这些业务资源的访问权限");
        }
        return true;
    }
}
