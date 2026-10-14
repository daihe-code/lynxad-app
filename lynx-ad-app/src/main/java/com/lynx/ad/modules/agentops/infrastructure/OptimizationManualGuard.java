package com.lynx.ad.modules.agentops.infrastructure;

import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.modules.agentops.application.OptimizationExecutionService;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import jakarta.servlet.http.*;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class OptimizationManualGuard implements WebMvcConfigurer {
    private final OptimizationExecutionService execution;private final AdPlanRepository plans;
    private static final String ATTRIBUTE=OptimizationManualGuard.class.getName();
    private static final Pattern PATH=Pattern.compile("^/(?:api/v1/delivery/)?plans/([^/]+)(/.*)?$");
    public OptimizationManualGuard(OptimizationExecutionService execution,AdPlanRepository plans){this.execution=execution;this.plans=plans;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
        @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
            if(!Set.of("POST","PUT","PATCH","DELETE").contains(request.getMethod()))return true;
            String path=request.getRequestURI().substring(request.getContextPath().length());var match=PATH.matcher(path);if(!match.matches())return true;
            String suffix=match.group(2);if(suffix!=null && (suffix.startsWith("/optimization") || suffix.equals("/sync-detail")))return true;
            var tenant=TenantContext.requireTenantId();var p=plans.findById(tenant,new PlanId(match.group(1))).orElse(null);if(p==null)return true;
            String owner=execution.manualClaim(p);request.setAttribute(ATTRIBUTE,new Object[]{p,owner});return true;
        }
        @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception exception){var value=(Object[])request.getAttribute(ATTRIBUTE);if(value!=null)execution.release((AdPlan)value[0],(String)value[1]);}
    });}
}
