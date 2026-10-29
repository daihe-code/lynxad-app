package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.application.CreationWorker;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;

class CreationSummaryReconciliationTest {
    final CreationStore store=mock(CreationStore.class);
    final CreationEngineClient engine=mock(CreationEngineClient.class);
    final ObjectMapper json=new ObjectMapper();
    CreationState state;
    CreationWorkflowService service;

    @BeforeEach void setup() {
        state=new CreationState();state.tenantId="tenant";state.planId="plan";state.engineJobId="job";
        state.generationStatus="FAILED";state.engineTerminalStatus="partial_failed";state.progress=80;
        state.generationMessage="部分 AI 创意未完成";state.generationProductIds=new ArrayList<>(List.of("p"));
        state.products.add(new CreationState.Product("p","p","商品",null,null,null,null,null,List.of(),List.of()));
        for(String type:List.of("VIDEO","IMAGE","CAROUSEL"))for(int i=1;i<=2;i++) {
            var m=new CreationState.Material();m.id=type+i;m.type=type;m.productId="p";m.engineJobId="job";m.status="READY";state.materials.add(m);
        }
        when(store.get("tenant","plan")).thenReturn(state);
        when(store.mutate(eq("tenant"),eq("plan"),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(state);return state;});
        service=new CreationWorkflowService(store,null,null,null,engine,json,null,null,null,null,null,mock(PlatformTransactionManager.class));
    }
    String response(String status) throws Exception {
        var job=json.createObjectNode().put("job_id","job").put("status",status);
        var group=job.putArray("groups").addObject().put("group_id","group-1").put("status",status);
        group.putObject("expected").put("videos",2).put("image_texts",2).put("product_cards",2);
        var materials=group.putArray("materials");
        for(var m:state.materials)materials.addObject().put("material_id",m.id).put("version","v1").put("status","succeeded")
                .put("material_type",switch(m.type){case "VIDEO"->"video";case "IMAGE"->"product_card";default->"image_text";});
        return job.toString();
    }
    @Test void sixReadyMaterialsRecoverFromStalePartialSummaryWithoutCreatingAnything() throws Exception {
        when(engine.get(anyString())).thenReturn(json.readTree(response("succeeded")));
        service.process(state);
        assertEquals("SUCCEEDED",state.generationStatus);assertEquals("succeeded",state.engineTerminalStatus);assertEquals(100,state.progress);
        assertEquals(6,state.materials.size());assertTrue(state.generationIssues.isEmpty());
        assertTrue(state.generationCounts.stream().allMatch(c->c.completed()==2&&c.total()==2));
        verify(engine,never()).post(any(),any());
    }
    @Test void readingALegacyStoppedDraftReconcilesItUnderTheExistingLease() throws Exception {
        when(store.claim(state)).thenReturn(true);
        when(engine.get(anyString())).thenReturn(json.readTree(response("succeeded")));
        var result=service.get(new TenantId("tenant"),"plan");
        assertEquals("SUCCEEDED",result.generationStatus);verify(store).release(state);verify(engine,never()).post(any(),any());
    }
    @Test void reopeningAfterBackgroundBudgetExpiresStillChecksFinalResultOnce() throws Exception {
        state.generationReconciliationAttempts=10;
        when(store.claim(state)).thenReturn(true);
        when(engine.get(anyString())).thenReturn(json.readTree(response("succeeded")));
        assertEquals("SUCCEEDED",service.get(new TenantId("tenant"),"plan").generationStatus);
        verify(engine,times(1)).get(anyString());verify(engine,never()).post(any(),any());
    }
    @Test void aBusyWorkerPreventsDuplicateReadSideReconciliation() {
        when(store.claim(state)).thenReturn(false);
        assertEquals("FAILED",service.get(new TenantId("tenant"),"plan").generationStatus);
        verifyNoInteractions(engine);
    }
    @Test void realEngineFailureIsNotHiddenByVisibleMaterialsAndRetriesAreBounded() throws Exception {
        when(engine.get(anyString())).thenReturn(json.readTree(response("partial_failed")));
        for(int i=0;i<10;i++)service.process(state);
        assertEquals("FAILED",state.generationStatus);assertEquals(10,state.generationReconciliationAttempts);
        assertFalse(state.needsGenerationReconciliation());assertEquals(6,state.materials.size());
        service.process(state);verify(engine,times(10)).get(any());verify(engine,never()).post(any(),any());
    }
    @Test void resumedEngineMustClearOldTerminalStatusInsteadOfFinishingAgain() throws Exception {
        when(engine.get(anyString())).thenReturn(json.readTree(response("processing")));
        service.process(state);
        assertEquals("RUNNING",state.generationStatus);assertNull(state.engineTerminalStatus);
        assertEquals(0,state.generationReconciliationAttempts);
    }
    @Test void lowerStagePercentNeverRollsBackTheSameJobAndCountsStillRefresh() throws Exception {
        state.generationStatus="RUNNING";state.engineTerminalStatus=null;state.progress=57;
        var job=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(response("processing"));
        job.putObject("progress").put("percent",21).put("stage","video_generation");
        when(engine.get(anyString())).thenReturn(job);
        service.process(state);
        assertEquals(57,state.progress);assertEquals("RUNNING",state.generationStatus);
        assertTrue(state.generationCounts.stream().allMatch(c->c.completed()==2));
        verify(engine,never()).post(any(),any());
    }
    @Test void missingProgressDoesNotInventAnEightyPercentCompletion() throws Exception {
        state.generationStatus="RUNNING";state.engineTerminalStatus=null;state.progress=29;
        when(engine.get(anyString())).thenReturn(json.readTree(response("processing")));
        service.process(state);
        assertEquals(29,state.progress);assertEquals("RUNNING",state.generationStatus);
    }
    @Test void terminalFailurePreservesTheAlreadyReachedSyncProgress() throws Exception {
        state.generationStatus="IMPORTING";state.progress=96;
        state.materials.getFirst().status="FAILED";
        when(engine.get(anyString())).thenReturn(json.readTree(response("partial_failed")));
        service.process(state);
        assertEquals("FAILED",state.generationStatus);assertEquals(96,state.progress);
    }
    @Test void engineOutageKeepsExistingMaterialsAndCountsTheAttempt() {
        when(store.claim(state)).thenReturn(true);when(engine.get(anyString())).thenThrow(new BizException(8002,"引擎暂不可用"));
        assertEquals("FAILED",service.get(new TenantId("tenant"),"plan").generationStatus);
        assertEquals(6,state.materials.size());assertEquals(1,state.generationReconciliationAttempts);
        verify(engine,never()).post(any(),any());verify(store).release(state);
    }
    @Test void lateResponseForPreviousJobCannotOverwriteANewGeneration() throws Exception {
        var old=json.readTree(response("succeeded"));
        when(engine.get(anyString())).thenAnswer(call->{state.engineJobId="new-job";state.engineTerminalStatus=null;state.generationStatus="RUNNING";return old;});
        // Model a detached snapshot, as it is in the real persistence store.
        var detached=json.convertValue(state,CreationState.class);
        service.process(detached);
        assertEquals("new-job",state.engineJobId);assertEquals("RUNNING",state.generationStatus);assertNull(state.engineTerminalStatus);
    }
    @Test void publishedOrAdditionFailureDoesNotReopenOriginalGeneration() {
        state.publishStatus="CREATED";assertFalse(state.needsGenerationReconciliation());
        state.publishStatus="NONE";state.pendingAdditionId="addition";assertFalse(state.needsGenerationReconciliation());
    }
    @Test void backgroundWorkerSchedulesFailedSummaryEvenWhenAllSixMaterialsAreReady() {
        var processor=mock(CreationWorkflowService.class);
        when(store.due()).thenReturn(List.of(state));when(store.claim(state)).thenReturn(true);
        var worker=new CreationWorker(store,processor);
        try{worker.poll();verify(processor,timeout(2000)).process(state);}
        finally{worker.stop();}
    }
}
