package com.lynx.ad.modules.agentops.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.agentops.application.OptimizationExecutionService;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class OptimizationManualGuardTest {
    static class Registry extends InterceptorRegistry {List<Object> all(){return super.getInterceptors();}}
    final OptimizationExecutionService execution=mock(OptimizationExecutionService.class);
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    HandlerInterceptor interceptor;
    @BeforeEach void setup(){var registry=new Registry();new OptimizationManualGuard(execution,plans).addInterceptors(registry);interceptor=(HandlerInterceptor)registry.all().getFirst();}
    @AfterEach void clear(){TenantContext.clear();}
    @Test void readOnlySettingsReconciliationDoesNotPauseAutomationOrAcquireAWriteLease() throws Exception {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST","/api/v1/delivery/plans/plan/settings/reconcile"),new MockHttpServletResponse(),new Object()));
        verifyNoInteractions(execution,plans);
    }
    @Test void actualSettingsWritesStillClaimAndReleaseTheManualWriteLease() throws Exception {
        var tenant=new TenantId("tenant");var plan=mock(AdPlan.class);
        TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        when(plans.findById(tenant,new PlanId("plan"))).thenReturn(Optional.of(plan));when(execution.manualClaim(plan)).thenReturn("manual-owner");
        var request=new MockHttpServletRequest("PUT","/api/v1/delivery/plans/plan/settings");var response=new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request,response,new Object()));verify(execution).manualClaim(plan);
        interceptor.afterCompletion(request,response,new Object(),null);verify(execution).release(plan,"manual-owner");
    }
    @Test void reviewRefreshIsAReadAndDoesNotPauseAutomation() throws Exception {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST","/api/v1/delivery/plans/plan/audit-refresh"),new MockHttpServletResponse(),new Object()));
        verifyNoInteractions(execution,plans);
    }
}
