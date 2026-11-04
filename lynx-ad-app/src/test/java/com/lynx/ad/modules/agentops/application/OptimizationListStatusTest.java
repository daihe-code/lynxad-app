package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
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
}
