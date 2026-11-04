package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import org.junit.jupiter.api.Test;

class OptimizationRuleDetailsTest {
    @Test void ownershipIsCheckedBeforeAnyMetricsOrWorkHistoryRead(){
        var monitor=mock(OptimizationMonitoringService.class);var journal=mock(OptimizationAutomationStore.class);var metrics=mock(OptimizationRuleMetricsStore.class);var accounts=mock(OauthAccountRepository.class);
        var tenant=new TenantId("foreign");var id=new PlanId("plan");when(monitor.rulesView(tenant,id)).thenThrow(new BizException(1405,"计划不存在"));
        assertThrows(BizException.class,()->new OptimizationRuleDetailsService(monitor,journal,metrics,accounts).details(tenant,id,false,"ALL",7,1,20));
        verifyNoInteractions(journal,metrics,accounts);
    }
}
