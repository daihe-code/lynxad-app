package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.delivery.api.DeliveryPlanController;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.dataops.application.AuditPollJob;
import java.util.*;
import org.junit.jupiter.api.*;

class PlanReviewApiTest {
    final TenantId tenant=new TenantId("tenant");final PlanId id=new PlanId("plan");
    final PlanQueryService query=mock(PlanQueryService.class);final AuditPollJob job=mock(AuditPollJob.class);
    final AdPlanRepository plans=mock(AdPlanRepository.class);final PlanCommandService commands=mock(PlanCommandService.class);
    final PlanManagementService management=mock(PlanManagementService.class);
    DeliveryPlanController controller;
    @BeforeEach void setup(){
        TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        controller=new DeliveryPlanController(commands,query,mock(PlanSyncService.class),management);controller.reviewJob(job);controller.reviewPlans(plans);
    }
    @AfterEach void clear(){TenantContext.clear();}
    @Test void ownershipIsCheckedBeforeAnyExternalRead(){
        when(query.get(tenant,id)).thenThrow(new BizException(1405,"计划不存在"));
        assertThrows(BizException.class,()->controller.refreshAudit(id.value()));verifyNoInteractions(plans,job,commands,management);
    }
    @Test void refreshUsesOnlyReviewReadAndRemainsAvailableWithoutWriteTrial()throws Exception {
        var plan=mock(AdPlan.class);when(plans.findById(tenant,id)).thenReturn(Optional.of(plan));controller.refreshAudit(id.value());
        verify(job).refresh(plan);verify(query,times(2)).get(tenant,id);verifyNoInteractions(commands,management);
        assertNull(DeliveryPlanController.class.getMethod("refreshAudit",String.class).getAnnotation(RequiresActiveTrial.class));
    }
}
