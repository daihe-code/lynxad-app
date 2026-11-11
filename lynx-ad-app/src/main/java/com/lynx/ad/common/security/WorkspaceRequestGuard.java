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
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/**","/plans","/plans/**","/oauth/accounts","/products/select","/creatives/**");
    }
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
            if(!member.roles().contains(MemberRole.OWNER)&&!member.roles().contains(MemberRole.FINANCE))throw new BizException(403,"没有财务权限");
            if(member.role()!=MemberRole.OWNER&&!"GET".equals(request.getMethod()))throw new BizException(403,"财务成员只能查看账务");
        } else if(path.startsWith("/api/v1/tenant/members")) {
            if(member.role()!=MemberRole.OWNER&&member.role()!=MemberRole.ADMIN)throw new BizException(403,"没有成员管理权限");
        } else if(!path.startsWith("/api/v1/tenant/")&&member.role()!=MemberRole.OWNER) {
            boolean planList=path.equals("/api/v1/delivery/plans")||path.equals("/plans");
            boolean planRead="GET".equals(request.getMethod())&&(planList
                    ||path.matches("/api/v1/delivery/plans/[^/]+")
                    ||path.equals("/api/v1/delivery/plans/statistics/trend")
                    ||path.matches("/api/v1/delivery/creation/[^/]+")
                    ||path.equals("/api/v1/identity/oauth/accounts")||path.equals("/oauth/accounts"));
            if(member.roles().contains(MemberRole.VIEWER) && planRead) {
                TenantContext.set(new TenantPrincipal(principal.userId(),principal.tenantId(),java.util.Optional.of(MemberRole.VIEWER),principal.sysRole(),principal.sessionId()));
                return true;
            }
            if(member.role()!=MemberRole.MEMBER&&member.role()!=MemberRole.OPERATOR)throw new BizException(403,"当前成员没有业务资源权限");
            if(path.startsWith("/api/v1/identity/accounts/")) {
                String bindingId=path.substring("/api/v1/identity/accounts/".length()).split("/",2)[0];
                access.requireReadableBinding(member.tenantId(),bindingId);
            }
            if(path.startsWith("/api/v1/identity/")||path.equals("/oauth/accounts")||planList
                    ||path.startsWith("/api/v1/delivery/plans/")||path.startsWith("/plans/")
                    ||path.startsWith("/api/v1/delivery/creation/")||path.equals("/api/v1/delivery/creation"))return true;
            // Legacy tenant-wide surfaces stay closed when they cannot isolate the responsible operator.
            if(!access.ownsBusinessScope(member))throw new BizException(403,"当前成员没有这些业务资源的访问权限");
        }
        return true;
    }
}
