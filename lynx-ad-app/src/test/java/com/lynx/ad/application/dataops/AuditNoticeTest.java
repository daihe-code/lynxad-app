package com.lynx.ad.application.dataops;

import static org.mockito.Mockito.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.dataops.application.AuditPollJob;
import com.lynx.ad.modules.dataops.domain.model.AuditVerdict;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditNoticeTest {
    @Test void approvalPostsOneNoticeWithTheActualPlanNameAndId() {
        var repository = mock(AdPlanRepository.class);
        var commands = mock(PlanCommandService.class);
        var inbox = mock(InboxCommandService.class);
        var properties = new LynxAdProperties();
        properties.getMock().setQianchuan(true);
        properties.getMock().setAutoAudit("PASS");
        var plan = mock(AdPlan.class);
        when(plan.id()).thenReturn(new PlanId("notice-plan"));
        when(plan.tenantId()).thenReturn(new TenantId("notice-owner"));
        when(plan.name()).thenReturn(new PlanName("烘焙投放计划"));
        when(repository.findPendingAudit()).thenReturn(List.of(plan), List.of());
        var job = new AuditPollJob(repository, commands, properties, inbox,
                mock(AdListPort.class), mock(QianchuanCredentialFacade.class));
        job.pollPendingAudits(); job.pollPendingAudits();
        verify(commands).markAudit(plan.tenantId(), plan.id(), AuditVerdict.PASS, null);
        verify(inbox).post(eq(plan.tenantId()), eq("计划审核通过"), contains("烘焙投放计划"), eq(new BizRef("AUDIT_PASS", "notice-plan")));
        verifyNoMoreInteractions(inbox);
    }
}
