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
    private com.lynx.ad.modules.agentops.application.OptimizationReviewService reviews;
    @org.springframework.beans.factory.annotation.Autowired public void reviews(com.lynx.ad.modules.agentops.application.OptimizationReviewService value){reviews=value;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
        @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
            if(!Set.of("POST","PUT","PATCH","DELETE").contains(request.getMethod()))return true;
            String path=request.getRequestURI().substring(request.getContextPath().length());var match=PATH.matcher(path);if(!match.matches())return true;
            String suffix=match.group(2);if(suffix!=null && (suffix.startsWith("/optimization") || suffix.equals("/sync-detail") || suffix.equals("/settings/reconcile") || suffix.equals("/audit-refresh")))return true;
            var tenant=TenantContext.requireTenantId();var p=plans.findById(tenant,new PlanId(match.group(1))).orElse(null);if(p==null)return true;
            String owner=execution.manualClaim(p);request.setAttribute(ATTRIBUTE,new Object[]{p,owner});
            String review=request.getHeader("X-Monitoring-Review");if(reviews!=null&&review!=null&&review.matches("[a-fA-F0-9-]{36}"))try{if(reviews.beforeMerchantWrite(p,review))request.setAttribute(ATTRIBUTE+".review",review);}catch(RuntimeException ignored){}return true;
        }
        @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception exception){var value=(Object[])request.getAttribute(ATTRIBUTE);if(value!=null){try{
            String review=(String)request.getAttribute(ATTRIBUTE+".review");if(reviews!=null&&exception==null&&response.getStatus()<300&&Boolean.TRUE.equals(request.getAttribute(OptimizationWriteReceipt.SUCCESS))&&review!=null)reviews.merchantWriteCompleted((AdPlan)value[0],review);
        }catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("merchant review readback deferred");}finally{execution.release((AdPlan)value[0],(String)value[1]);}}}
    });}
}
