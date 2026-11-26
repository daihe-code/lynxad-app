package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptimizationListStatusTest {
    final TenantId tenant=new TenantId("tenant-a");
    final OptimizationStore store=mock(OptimizationStore.class);
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    final OauthAccountRepository accounts=mock(OauthAccountRepository.class);
    final OptimizationAutomationStore journal=mock(OptimizationAutomationStore.class);
    final OptimizationExecutionService execution=mock(OptimizationExecutionService.class);
    final OptimizationCenterService service=new OptimizationCenterService(plans,accounts,store,journal,execution);

    @Test void readsOnlyRequestedTenantAndPlansInOneLocalBatch() {
        when(store.forPlans(tenant,List.of("a","b"))).thenReturn(List.of(new OptimizationStore.State("tenant-a","a",1,false,OptimizationPolicy.defaults(),null,Instant.now(),null,new ObjectMapper().createObjectNode().put("state","READY").put("observed_at",Instant.now().toString()))));
        var result=service.statuses(tenant,List.of("a","b","a"));
        assertEquals(1,result.size());assertEquals("a",result.getFirst().planId());
        assertFalse(result.getFirst().enabled());assertFalse(result.getFirst().stale());assertFalse(result.getFirst().failed());
        verify(store).forPlans(tenant,List.of("a","b"));verifyNoMoreInteractions(store);
        verifyNoInteractions(plans,accounts,journal,execution);
    }
    @Test void missingAndExpiredEvidenceDoesNotLookHealthy() {
        when(store.forPlans(tenant,List.of("a","b"))).thenReturn(List.of(
            new OptimizationStore.State("tenant-a","a",1,false,OptimizationPolicy.defaults(),null,null,null,null),
            new OptimizationStore.State("tenant-a","b",1,false,OptimizationPolicy.defaults(),null,Instant.now().minusSeconds(601),"unavailable",null)));
        var result=service.statuses(tenant,List.of("a","b"));
        assertTrue(result.stream().allMatch(OptimizationCenterService.Status::stale));
        assertTrue(result.get(1).failed());assertNull(result.get(1).state());
    }
    @Test void rejectsInvalidOrUnboundedBatchBeforeReading() {
        assertThrows(BizException.class,()->service.statuses(tenant,List.of()));
        assertThrows(BizException.class,()->service.statuses(tenant,List.of(" ")));
        assertThrows(BizException.class,()->service.statuses(tenant,Collections.nCopies(51,"a")));
        verifyNoInteractions(store);
    }
    @Test void recentReadCannotMakeOldEvidenceFresh() {
        var result=new ObjectMapper().createObjectNode().put("state","READY").put("observed_at",Instant.now().minusSeconds(601).toString());
        when(store.forPlans(tenant,List.of("a"))).thenReturn(List.of(new OptimizationStore.State("tenant-a","a",1,true,OptimizationPolicy.defaults(),null,Instant.now(),null,result)));
        assertTrue(service.statuses(tenant,List.of("a")).getFirst().stale());
    }

    @Test void manualRefreshDoesNotAppearAsRulesNeedingReview() {
        var now=Instant.now();
        var rules=new OptimizationRules(BigDecimal.ONE,new BigDecimal("400"),new BigDecimal("480"),
            OptimizationRules.AUTO_PAUSE,5,BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("20"));
        var report=new ObjectMapper().createObjectNode().put("state","READY").put("observed_at",now.toString());
        when(store.forPlans(tenant,List.of("a","b"))).thenReturn(List.of(
            new OptimizationStore.State(tenant.value(),"a",1,true,rules.policy(true),now,now,null,report,
                "用户正在手动操作计划，自动调控已暂停",null,null,null,null),
            new OptimizationStore.State(tenant.value(),"b",1,true,rules.policy(true),now,now,null,report,
                OptimizationStore.AUTHORIZATION_HOLD,null,null,null,null)));
        var statuses=service.statuses(tenant,List.of("a","b"));
        assertTrue(statuses.getFirst().enabled());
        assertTrue(statuses.getFirst().stale());
        assertNull(statuses.getFirst().holdReason());
        assertEquals(OptimizationStore.AUTHORIZATION_HOLD,statuses.get(1).holdReason());
    }
}
