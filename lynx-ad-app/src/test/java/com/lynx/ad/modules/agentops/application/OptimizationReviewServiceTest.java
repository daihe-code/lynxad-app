package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.application.PlanInsightsService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class OptimizationReviewServiceTest {
    final ObjectMapper json=new ObjectMapper();final OptimizationReviewStore reviews=mock(OptimizationReviewStore.class);final OptimizationStore policies=mock(OptimizationStore.class);final OptimizationAutomationStore journal=mock(OptimizationAutomationStore.class);final OptimizationExecutionGateway gateway=mock(OptimizationExecutionGateway.class);final CreationStore creations=mock(CreationStore.class);final PlanInsightsService insights=mock(PlanInsightsService.class);final MonitoringStrategy strategy=new MonitoringStrategy(json,"");final OptimizationReviewService service=new OptimizationReviewService(reviews,policies,journal,gateway,insights,strategy,json);final AdPlan plan=mock(AdPlan.class);
    final String id="12345678-1234-4567-8123-123456789abc";final Instant now=Instant.now();
    ObjectNode facts(String binding){var n=json.createObjectNode().put("status","DELIVERING").put("budget","300");n.putArray("bindings").add(binding);return n;}
    OptimizationReviewStore.Review review(String status,String job){return new OptimizationReviewStore.Review(id,"tenant","plan","event",job,1,"materials",status,facts("old"),null,json.createObjectNode(),null,strategy.parameters(),now,null,now);}
    CreationState work(String target,String status){var s=new CreationState();s.planId="work";s.tenantId="tenant";s.publishedEdit=new CreationState.PublishedEdit();s.publishedEdit.targetPlanId=target;s.publishedEdit.status=status;var member=new CreationState.ContentMember();member.planId=target;member.after=facts("new").path("bindings");s.publishedEdit.members.add(member);return s;}
    @BeforeEach void setup(){service.creations(creations);when(plan.tenantId()).thenReturn(new TenantId("tenant"));when(plan.id()).thenReturn(new PlanId("plan"));when(gateway.plan("tenant","plan")).thenReturn(plan);when(reviews.claim(any(),anyString())).thenReturn(true);}
    @Test void correlationCannotLinkAnotherTargetPlanAndNeverCompletesAtSubmission(){var r=review("TRACKING",null);when(reviews.find("tenant",id)).thenReturn(Optional.of(r));when(creations.get("tenant","work")).thenReturn(work("other","DRAFT"));service.linkContent("tenant","work",id);verify(reviews,never()).linkContent(any(),anyString());when(creations.get("tenant","work")).thenReturn(work("plan","DRAFT"));when(gateway.reviewFacts(plan)).thenReturn(facts("old"));service.linkContent("tenant","work",id);verify(reviews).linkContent(r,"work");verify(reviews,never()).applied(any(),any());verifyNoInteractions(insights);}
    @Test void asyncContentReadbackMustMatchTheServerOwnedFinishedJob(){var r=review("TRACKING","work");when(creations.get("tenant","work")).thenReturn(work("plan","SUCCEEDED"));when(gateway.reviewFacts(plan)).thenReturn(facts("new"));when(reviews.applied(r,facts("new"))).thenReturn(true);when(reviews.find("tenant",id)).thenReturn(Optional.of(review("OBSERVING","work")));service.evaluate(r);verify(reviews).verifiedWorkflow(r,facts("new"));verify(reviews,never()).finish(any(),anyString(),anyString(),any());}
    @Test void unfinishedJobOrUnmatchedActualBindingsCannotCountAsACompletedTask(){for(String scenario:List.of("UNCERTAIN","SUCCEEDED")){reset(reviews);when(reviews.claim(any(),anyString())).thenReturn(true);var r=review("TRACKING","work");when(creations.get("tenant","work")).thenReturn(work("plan",scenario));when(gateway.reviewFacts(plan)).thenReturn(facts("unexpected"));when(reviews.find("tenant",id)).thenReturn(Optional.of(r));service.evaluate(r);verify(reviews,never()).applied(any(),any());verify(reviews,never()).verifiedWorkflow(any(),any());verify(reviews).finish(eq(r),anyString(),eq("TRACKING"),any());}}
}
