package com.lynx.ad.modules.delivery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationReviewWorkflowTest {
    CreationStore store=mock(CreationStore.class);CreationEngineClient engine=mock(CreationEngineClient.class);
    CreationPayloadBuilder payloads=mock(CreationPayloadBuilder.class);CreationQianchuanGateway qc=mock(CreationQianchuanGateway.class);
    AdPlanRepository plans=mock(AdPlanRepository.class);CreationState s=new CreationState();CreationWorkflowService service;
    ObjectMapper json=new ObjectMapper();TenantId tenant=new TenantId("tenant");
    @BeforeEach void setup(){
        s.tenantId="tenant";s.planId="plan";s.accountId="account";s.awemeId="aweme";s.revision=7;s.packageVersion=3;
        when(store.get("tenant","plan")).thenReturn(s);when(store.attempt(anyString(),anyString())).thenReturn(Optional.empty());
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{Integer version=call.getArgument(2);if(version!=null&&version!=s.revision)throw new BizException(1404,"版本已变化");Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(plans.findById(any(),any())).thenReturn(Optional.of(mock(AdPlan.class)));
        service=spy(new CreationWorkflowService(store,plans,null,qc,engine,json,null,null,null,payloads,mock(BillingTrialService.class),mock(PlatformTransactionManager.class)));
        doReturn(s).when(service).get(tenant,"plan");
    }
    @Test void onePublishConfirmsCurrentVersionButStaleClientCannotPublish(){
        when(payloads.build(s)).thenAnswer(call->{assertEquals(s.packageVersion,s.confirmedVersion);return json.createObjectNode();});
        service.publish(tenant,"plan",new CreationWorkflowService.Publish(7,3,"publish-request-000001"));
        assertEquals("QUEUED",s.publishStatus);assertEquals(3,s.confirmedVersion);verify(payloads,times(1)).build(s);
    }
    @Test void changedVersionNeverBuildsOrQueuesAnAttempt(){
        assertThrows(BizException.class,()->service.publish(tenant,"plan",new CreationWorkflowService.Publish(6,3,"publish-request-000001")));
        verifyNoInteractions(payloads);verify(store,never()).attempt(any(CreationStore.Attempt.class));
    }
    @Test void cancelledAuthorizationBlocksNewGenerationAcrossAllEntryPoints(){
        var capabilities=mock(com.lynx.ad.modules.identity.application.AuthorizationCapabilityService.class);
        service.authorizationCapabilities(capabilities);
        doThrow(new BizException(1205,"账户授权不可用")).when(capabilities).requireMaterials(tenant,"account");
        assertThrows(BizException.class,()->service.generate(tenant,"plan",new CreationWorkflowService.Generate(7,List.of("product"))));
        assertThrows(BizException.class,()->service.addition(tenant,"plan",new CreationWorkflowService.Addition(7,"product","VIDEO","","addition-001")));
        assertThrows(BizException.class,()->service.regenerate(tenant,"plan","material",new CreationWorkflowService.Feedback(7,List.of(),"调整画面")));
        assertThrows(BizException.class,()->service.repairTypography(tenant,"plan","material",new CreationWorkflowService.Version(7)));
        assertThrows(BizException.class,()->service.retryGeneration(tenant,"plan",new CreationWorkflowService.RetryGeneration(7,"VIDEO")));
        verifyNoInteractions(engine);assertEquals("NONE",s.generationStatus);assertTrue(s.generationAdditions.isEmpty());assertNull(s.regeneration);assertNull(s.generationRetry);
    }
    @Test void previouslyAcceptedPublishReceiptRemainsReadableAfterAuthorizationChanges(){
        var capabilities=mock(com.lynx.ad.modules.identity.application.AuthorizationCapabilityService.class);
        service.authorizationCapabilities(capabilities);
        var receipt=new CreationStore.Attempt("attempt","tenant","plan",7,"publish-request-000001","hash","{}","CREATED",null,null);
        when(store.attempt("tenant","publish-request-000001")).thenReturn(Optional.of(receipt));
        assertSame(s,service.publish(tenant,"plan",new CreationWorkflowService.Publish(7,3,"publish-request-000001")));
        verifyNoInteractions(capabilities,payloads,engine);
    }
    @Test void uncertainFailedRetryUsesReceiptReadsOnlyAndDoesNotResubmit(){
        s.generationStatus="FAILED";s.engineJobId="engine-job";s.enginePlanId="engine-plan";s.generationCounts.add(new CreationState.GenerationCount("VIDEO",1,2,1));
        when(engine.post(contains("retry-failed"),any())).thenThrow(new BizException(8003,"提交结果待确认"));
        service.retryGeneration(tenant,"plan",new CreationWorkflowService.RetryGeneration(7,"VIDEO"));
        assertEquals("UNCERTAIN",s.generationStatus);assertEquals("SENDING",s.generationRetry.status);
        service.process(s);service.process(s);
        verify(engine,times(1)).post(contains("retry-failed"),any());verify(engine,times(2)).find(contains("retry-failed"));
        assertThrows(BizException.class,()->service.retryGeneration(tenant,"plan",new CreationWorkflowService.RetryGeneration(s.revision,"VIDEO")));
        verify(engine,times(1)).post(anyString(),any());
    }
    @Test void expiredEstimateRejectionProvidesRecoveryAndKeepsMaterials() throws Exception {
        var attempt=new CreationStore.Attempt("attempt","tenant","plan",7,"publish-request-000001","hash","{}","QUEUED",null,null);
        when(store.claimPublish("tenant",attempt.key())).thenReturn(true);
        when(qc.create(any(),anyString(),anyString(),any())).thenThrow(new BizException(1406,"千川拒绝请求：效果预估结果有更新，请重新获取（错误码40000）"));
        var m=new CreationState.Material();m.id="existing";s.materials.add(m);
        service.send(attempt);assertEquals("FAILED",s.publishStatus);assertEquals("REFRESH_ESTIMATE",s.publishRecovery);assertEquals("existing",s.materials.getFirst().id);
        assertFalse(s.publishError.contains("40000"));verify(qc,times(1)).create(any(),anyString(),anyString(),any());
    }
}
