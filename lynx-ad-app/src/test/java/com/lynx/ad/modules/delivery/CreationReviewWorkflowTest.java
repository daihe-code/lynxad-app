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
    void failedSlots() {
        s.generationStatus="FAILED";s.engineJobId="engine-job";s.enginePlanId="engine-plan";
        s.generationCounts.add(new CreationState.GenerationCount("VIDEO",1,3,2));
        s.generationIssues.add(new CreationState.GenerationIssue("p1","商品一","内部错误一","VIDEO",null,"失败视频一","group-1-video-2"));
        s.generationIssues.add(new CreationState.GenerationIssue("p2","商品二","内部错误二","VIDEO",null,"失败视频二","group-2-video-1"));
        var ready=new CreationState.Material();ready.id="ready";ready.type="VIDEO";ready.status="READY";ready.productId="p1";s.materials.add(ready);
    }
    @Test void oneFailedSlotRetryPreservesOtherFailuresAndSuccessfulMaterials() {
        failedSlots();
        when(engine.post(contains("retry-failed"),any())).thenAnswer(call->{
            var body=json.valueToTree(call.getArgument(1));
            assertEquals(json.readTree("[{\"product_id\":\"p1\",\"slot_id\":\"group-1-video-2\"}]"),body.path("targets"));
            return ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("job_id","engine-job").put("status","ACCEPTED");
        });
        service.retryGeneration(tenant,"plan",new CreationWorkflowService.RetryGeneration(7,"VIDEO","p1","group-1-video-2"));
        assertEquals("RUNNING",s.generationStatus);assertEquals("ACCEPTED",s.generationRetry.status);
        assertEquals(2,s.generationIssues.size());assertEquals("ready",s.materials.getFirst().id);assertEquals("READY",s.materials.getFirst().status);
        verify(engine,times(1)).post(anyString(),any());
    }
    @Test void deletingAFailedSlotPersistsItsIdentityAndNeverDeletesMediaOrCallsEngine() {
        failedSlots();var issue=s.generationIssues.getFirst();
        service.deleteGenerationFailure(tenant,"plan",new CreationWorkflowService.DeleteGenerationFailure(7,"p1","VIDEO",issue.slotId()));
        assertEquals(8,s.revision);assertEquals(1,s.generationIssues.size());assertEquals("p2",s.generationIssues.getFirst().productId());
        assertEquals(new CreationState.GenerationCount("VIDEO",1,2,1),s.generationCounts.getFirst());
        assertEquals(1,s.materials.size());assertTrue(s.removedMaterialIds.isEmpty());
        assertTrue(json.convertValue(s,CreationState.class).generationIssueDismissed(issue));
        s.engineJobId="next-job";assertFalse(s.generationIssueDismissed(issue));
        verifyNoInteractions(engine,qc);
    }
    @Test void retryingAllFailuresExcludesDeletedSlots() {
        failedSlots();
        service.deleteGenerationFailure(tenant,"plan",new CreationWorkflowService.DeleteGenerationFailure(7,"p1","VIDEO","group-1-video-2"));
        when(engine.post(contains("retry-failed"),any())).thenAnswer(call->{
            var body=json.valueToTree(call.getArgument(1));
            assertEquals(json.readTree("[{\"product_id\":\"p2\",\"slot_id\":\"group-2-video-1\"}]"),body.path("targets"));
            return ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("job_id","engine-job").put("status","ACCEPTED");
        });
        service.retryGeneration(tenant,"plan",new CreationWorkflowService.RetryGeneration(s.revision,"VIDEO"));
        assertEquals("ACCEPTED",s.generationRetry.status);assertEquals(1,s.materials.size());
    }
    @Test void unknownForeignAndStaleFailureActionsNeverSubmitOrDeleteAnything() {
        failedSlots();
        assertThrows(BizException.class,()->service.deleteGenerationFailure(tenant,"plan",new CreationWorkflowService.DeleteGenerationFailure(6,"p1","VIDEO","group-1-video-2")));
        assertThrows(BizException.class,()->service.deleteGenerationFailure(tenant,"plan",new CreationWorkflowService.DeleteGenerationFailure(7,"p2","VIDEO","group-1-video-2")));
        assertThrows(BizException.class,()->service.retryGeneration(tenant,"plan",new CreationWorkflowService.RetryGeneration(7,"VIDEO","p2","group-1-video-2")));
        assertEquals(2,s.generationIssues.size());assertEquals(1,s.materials.size());assertNull(s.generationRetry);verifyNoInteractions(engine);
    }
    @Test void failedPlaceholderActionsCannotRemoveARegisteredMaterialOrChangeAnActiveJob() {
        failedSlots();var issue=s.generationIssues.getFirst();
        s.generationIssues.set(0,new CreationState.GenerationIssue(issue.productId(),issue.productName(),issue.message(),issue.type(),"ready",issue.title(),issue.slotId()));
        assertThrows(BizException.class,()->service.deleteGenerationFailure(tenant,"plan",new CreationWorkflowService.DeleteGenerationFailure(7,"p1","VIDEO",issue.slotId())));
        s.generationStatus="RUNNING";
        assertThrows(BizException.class,()->service.deleteGenerationFailure(tenant,"plan",new CreationWorkflowService.DeleteGenerationFailure(7,"p2","VIDEO","group-2-video-1")));
        assertThrows(BizException.class,()->service.retryGeneration(tenant,"plan",new CreationWorkflowService.RetryGeneration(7,"VIDEO","p2","group-2-video-1")));
        assertEquals(2,s.generationIssues.size());assertTrue(s.dismissedGenerationIssues.isEmpty());verifyNoInteractions(engine);
    }
    @Test void expiredEstimateRejectionProvidesRecoveryAndKeepsMaterials() throws Exception {
        var attempt=new CreationStore.Attempt("attempt","tenant","plan",7,"publish-request-000001","hash","{}","QUEUED",null,null);
        when(store.claimPublish("tenant",attempt.key())).thenReturn(true);
        when(qc.create(any(),anyString(),anyString(),any())).thenThrow(new BizException(1406,"千川拒绝请求：效果预估结果有更新，请重新获取（错误码40000）"));
        var m=new CreationState.Material();m.id="existing";s.materials.add(m);
        service.send(attempt);assertEquals("FAILED",s.publishStatus);assertEquals("REFRESH_ESTIMATE",s.publishRecovery);assertEquals("existing",s.materials.getFirst().id);
        assertFalse(s.publishError.contains("40000"));verify(qc,times(1)).create(any(),anyString(),anyString(),any());
    }
    @Test void failedSubmissionPersistsItsCauseWithoutChangingSavedSettingsOrRegeneratingMaterials() {
        var attempt=new CreationStore.Attempt("attempt","tenant","plan",7,"publish-request-000001","hash","{}","QUEUED",null,null);
        s.publishRecovery="REFRESH_ESTIMATE";s.budget=new java.math.BigDecimal("300");s.roi2Goal=new java.math.BigDecimal("4.82");
        var m=new CreationState.Material();m.id="existing";s.materials.add(m);
        service.failed(attempt,"FAILED","商品已下架，无法投放");
        assertEquals("FAILED",s.publishStatus);assertEquals("PRODUCTS",s.publishRecovery);
        assertEquals(new java.math.BigDecimal("300"),s.budget);assertEquals(new java.math.BigDecimal("4.82"),s.roi2Goal);
        assertEquals("existing",s.materials.getFirst().id);verifyNoInteractions(engine,qc);
        service.failed(attempt,"FAILED","日预算不能低于300元");assertEquals("GOAL",s.publishRecovery);
        service.failed(attempt,"FAILED","视频格式不支持");assertEquals("MATERIALS",s.publishRecovery);
        service.failed(attempt,"FAILED","请求异常");assertEquals("UNKNOWN",s.publishRecovery);
    }
    @Test void unconfirmedCreationNeverGetsAnEditorRecoveryOrAnAutomaticResubmission() {
        var attempt=new CreationStore.Attempt("attempt","tenant","plan",7,"publish-request-000001","hash","{}","QUEUED",null,null);
        when(store.claimPublish("tenant",attempt.key())).thenReturn(true);
        when(qc.create(any(),anyString(),anyString(),any())).thenThrow(new BizException(1523,"预算提交结果尚未确认"));
        s.publishRecovery="GOAL";
        service.send(attempt);assertEquals("UNCERTAIN",s.publishStatus);assertNull(s.publishRecovery);
        verify(qc,times(1)).create(any(),anyString(),anyString(),any());verifyNoInteractions(engine);
    }
}
