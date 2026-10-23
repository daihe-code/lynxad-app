package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.creative.application.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class CreationRecoveryTest {
    @Test void originalAndAiVersionsDoNotInflateCompletionOrReplaceExistingFiles() throws Exception {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);
        var s=new CreationState();s.tenantId="tenant";s.planId="plan";s.engineJobId="job";s.generationStatus="RUNNING";
        s.products.add(new CreationState.Product("123","123","商品",null,null,null,null,null,List.of(),List.of()));
        for(String id:List.of("base","ai")) {
            var m=new CreationState.Material();m.id=id;m.type="IMAGE";m.status="READY";m.productId="123";
            m.previewUrl="https://existing.test/"+id;s.materials.add(m);
        }
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
          {"job_id":"job","status":"processing","groups":[{"group_id":"group-1","expected":{"product_cards":2},"materials":[
          {"material_id":"base","version":"v1","status":"succeeded","material_type":"product_card","detail":{"delivery_family_id":"slot-1"}},
          {"material_id":"ai","version":"v1","status":"succeeded","material_type":"product_card","detail":{"delivery_family_id":"slot-1"}}]}]}
          """));
        new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),null,null,null,null,null,mock(PlatformTransactionManager.class)).process(s);
        assertEquals(2,s.materials.size());
        assertEquals("https://existing.test/base",s.materials.getFirst().previewUrl);
        var count=s.generationCounts.stream().filter(c->c.type().equals("IMAGE")).findFirst().orElseThrow();
        assertEquals(1,count.completed());assertEquals(2,count.total());
    }
    @Test void explicitAnalysisRefreshSurvivesDurableStateAndReachesEngine() throws Exception {
        var mapper=new ObjectMapper();var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);
        var initial=new CreationState();initial.tenantId="tenant";initial.planId="plan";initial.enginePlanId="apt";initial.accountId="account";
        initial.analysisStatus="QUEUED";initial.analysisRefreshRequested=true;
        var s=mapper.readValue(mapper.writeValueAsString(initial),CreationState.class);
        when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.post(eq("/api/v1/capabilities/perception/products/analyze"),any())).thenReturn(mapper.readTree("""
            {"product_set":{"product_count":0},"data_quality":{"status":"partial","available_dimensions":1},"dimensions":{}}
            """));
        new CreationWorkflowService(store,null,null,null,engine,mapper,null,null,null,null,null,mock(PlatformTransactionManager.class)).process(s);
        var body=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(engine).post(eq("/api/v1/capabilities/perception/products/analyze"),body.capture());
        assertEquals(Boolean.TRUE,((Map<?,?>)body.getValue()).get("refresh"));
        assertEquals("SUCCEEDED",s.analysisStatus);assertFalse(s.analysisRefreshRequested);
        assertEquals("partial",s.analysis.path("data_quality").path("status").asText());
        var legacy=mapper.readValue("{\"analysisStatus\":\"NONE\"}",CreationState.class);
        assertFalse(legacy.analysisRefreshRequested);
    }
    @Test void failedRegenerationDoesNotClaimSuccessBecauseOriginalMaterialIsReady() throws Exception {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);
        var s=new CreationState();s.tenantId="tenant";s.planId="plan";s.engineJobId="job";
        s.generationStatus="RUNNING";s.generationProductIds=List.of("123");s.pendingRegenerationId="original";
        var original=new CreationState.Material();original.id="original";original.productId="123";original.status="READY";s.materials.add(original);
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("{\"job_id\":\"job\",\"status\":\"failed\",\"groups\":[],\"progress\":{\"percent\":100}}"));
        var service=new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),null,null,null,null,null,mock(PlatformTransactionManager.class));
        service.process(s);assertEquals("FAILED",s.generationStatus);assertEquals("READY",s.materials.getFirst().status);
        assertEquals("original",s.materials.getFirst().id);assertNotEquals(100,s.progress);
    }
    @Test void completedEngineJobImportsRealRolesBeforeMakingMaterialsReady() throws Exception {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);var importer=mock(CreationAssetImporter.class);var assets=mock(AssetUploadService.class);var payload=mock(CreationPayloadBuilder.class);
        var s=new CreationState();s.tenantId="tenant";s.userId="user";s.planId="plan";s.accountId="account";s.engineJobId="job";s.generationStatus="RUNNING";s.generationProductIds=List.of("123");
        s.products.add(new CreationState.Product("123","123","商品",null,null,"https://example.test/main.jpg","",null,List.of(),List.of()));
        when(store.get("tenant","plan")).thenReturn(s);when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
          {"job_id":"job","status":"succeeded","progress":{"percent":100},"groups":[{"group_id":"group-1","materials":[
          {"material_id":"video-result","version":"v1","status":"succeeded","material_type":"video","urls":{"video_url":"https://example.test/real.mp4","cover_url":"https://example.test/real.jpg"},"content":{"title":"真实视频标题","text":"商品描述"}},
          {"material_id":"image-result","version":"v1","status":"succeeded","material_type":"product_card","urls":{"image_urls":["https://example.test/one.jpg","https://example.test/two.jpg"]},"content":{"title":"图片标题"}}]}]}
          """));
        when(importer.reference(any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any(),anyBoolean())).thenAnswer(call->call.getArgument(2));
        when(assets.detail(any(),anyString())).thenAnswer(call->CreationPayloadTest.asset(call.getArgument(1),"VIDEO","APPROVED",null,null,null));
        var service=new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),importer,assets,null,payload,null,mock(PlatformTransactionManager.class));service.process(s);
        assertEquals("SUCCEEDED",s.generationStatus);assertEquals(100,s.progress);assertEquals(2,s.materials.size());
        var video=s.materials.getFirst();assertEquals("plan:video-result:VIDEO",video.videoAssetId);assertEquals("plan:video-result:COVER",video.coverAssetId);assertEquals("job",video.engineJobId);
        assertEquals(2,s.materials.get(1).imageAssetIds.size());assertTrue(s.materials.stream().allMatch(m->m.status.equals("READY")));
        verify(importer,times(6)).reference(any(),any(),anyString(),eq("job"),anyString(),anyString(),anyString(),any(),anyBoolean()); verify(importer,never()).ingest(any(),any(),anyString(),anyString(),anyString(),any(),anyBoolean());verify(payload,times(2)).materialReady(eq(s),any());
    }
    @Test void localFailureAfterPlatformSuccessNeverReissuesCreateOrErasesReceipt(){
        var store=mock(CreationStore.class);var qc=mock(CreationQianchuanGateway.class);
        var service=new CreationWorkflowService(store,null,null,qc,null,new ObjectMapper(),null,null,null,null,null,mock(PlatformTransactionManager.class));
        var a=new CreationStore.Attempt("id","tenant","plan",1,"key","hash","{}","QUEUED",null,null);var s=new CreationState();s.tenantId="tenant";s.planId="plan";s.accountId="account";s.awemeId="123";
        when(store.claimPublish("tenant","key")).thenReturn(true,false);when(store.get("tenant","plan")).thenReturn(s);
        when(qc.create(any(),eq("account"),eq("123"),any())).thenReturn("9007199254740993");
        when(store.mutate(eq("tenant"),eq("plan"),isNull(),any())).thenReturn(s).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class,()->service.send(a));service.send(a);
        verify(qc,times(1)).create(any(),any(),any(),any());verify(store).finishPublish("tenant","key","CREATED","9007199254740993",null);verify(store,never()).finishPublish(eq("tenant"),eq("key"),eq("UNCERTAIN"),any(),any());
    }
    @Test void partialFailureKeepsSuccessfulResultsAndDoesNotInventMissingProductCounts() throws Exception {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);
        var s=new CreationState();s.tenantId="tenant";s.planId="plan";s.engineJobId="job";s.generationStatus="RUNNING";s.generationProductIds=List.of("123","456");
        s.products.add(new CreationState.Product("123","123","成功商品",null,null,null,null,null,List.of(),List.of()));
        s.products.add(new CreationState.Product("456","456","失败商品",null,null,null,null,null,List.of(),List.of()));
        var ready=new CreationState.Material();ready.id="image-result";ready.type="IMAGE";ready.productId="123";ready.status="READY";s.materials.add(ready);
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
          {"job_id":"job","status":"partial_failed","groups":[
          {"group_id":"group-1","status":"succeeded","expected":{"videos":0,"image_texts":0,"product_cards":1},"materials":[{"material_id":"image-result","version":"v1","status":"succeeded","material_type":"product_card"}]},
          {"group_id":"group-2","status":"pending","expected":{"videos":0,"image_texts":0,"product_cards":0},"materials":[]}]}
          """));
        var service=new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),null,null,null,null,null,mock(PlatformTransactionManager.class));service.process(s);
        assertEquals("FAILED",s.generationStatus);assertEquals("READY",s.materials.getFirst().status);
        var image=s.generationCounts.stream().filter(c->c.type().equals("IMAGE")).findFirst().orElseThrow();
        assertEquals(1,image.completed());assertNull(image.total());
        assertEquals("456",s.generationIssues.getFirst().productId());assertEquals("失败商品",s.generationIssues.getFirst().productName());
        verify(engine,never()).post(any(),any());
    }
    @Test void confirmedTypedFailureKeepsRetryCountEvenWhenPlanningTotalsAreUnknown() throws Exception {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);var s=new CreationState();
        s.tenantId="tenant";s.planId="plan";s.engineJobId="job";s.generationStatus="RUNNING";s.generationProductIds=List.of("123");
        s.products.add(new CreationState.Product("123","123","商品",null,null,null,null,null,List.of(),List.of()));
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
          {"job_id":"job","status":"failed","groups":[{"group_id":"group-1","status":"failed","materials":[],
          "generation_failures":[{"slot_id":"video-1","material_type":"video","message":"视频服务超时"}]}]}
          """));
        new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),null,null,null,null,null,mock(PlatformTransactionManager.class)).process(s);
        var count=s.generationCounts.stream().filter(c->c.type().equals("VIDEO")).findFirst().orElseThrow();
        assertNull(count.total());assertEquals(1,count.failed());assertEquals("VIDEO",s.generationIssues.getFirst().type());
    }
    @Test void runningProgressUsesEngineExpectedCountsAndStage() throws Exception {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);var s=new CreationState();
        s.tenantId="tenant";s.planId="plan";s.engineJobId="job";s.generationStatus="RUNNING";
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
          {"job_id":"job","status":"processing","progress":{"percent":62,"stage":"media_generation","message":"正在生成视频"},"groups":[{"group_id":"group-1","status":"processing","expected":{"videos":2,"image_texts":2,"product_cards":2},"materials":[]}]}
          """));
        new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),null,null,null,null,null,mock(PlatformTransactionManager.class)).process(s);
        assertEquals(62,s.progress);assertEquals("media_generation",s.generationStage);
        assertEquals(3,s.generationCounts.size());assertTrue(s.generationCounts.stream().allMatch(c->c.total()==2&&c.completed()==0));
    }
    @Test void recheckingAFailedDraftImportsLaterEngineResultsWithoutGeneratingAgain() throws Exception {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);var importer=mock(CreationAssetImporter.class);var assets=mock(AssetUploadService.class);var payload=mock(CreationPayloadBuilder.class);
        var s=new CreationState();s.tenantId="tenant";s.userId="user";s.planId="plan";s.accountId="account";s.engineJobId="job";s.generationStatus="FAILED";s.engineTerminalStatus="partial_failed";s.generationProductIds=List.of("123","456");
        for(String p:List.of("123","456"))s.products.add(new CreationState.Product(p,p,p,null,null,null,null,null,List.of(),List.of()));
        var original=new CreationState.Material();original.id="old";original.type="IMAGE";original.productId="123";original.status="PROCESSING";original.textAssetId="old-text";original.imageAssetIds.add("old-image");s.materials.add(original);
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
          {"job_id":"job","status":"succeeded","groups":[{"group_id":"group-2","status":"succeeded","expected":{"product_cards":1},"materials":[
          {"material_id":"late-result","version":"v1","status":"succeeded","material_type":"product_card","urls":{"image_urls":["https://example.test/late.jpg"]},"content":{"title":"恢复后的图片"}}]}]}
          """));
        when(importer.reference(any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any(),anyBoolean())).thenAnswer(call->call.getArgument(2));
        when(assets.detail(any(),anyString())).thenAnswer(call->CreationPayloadTest.asset(call.getArgument(1),"IMAGE","APPROVED",null,null,null));
        new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),importer,assets,null,payload,null,mock(PlatformTransactionManager.class)).process(s);
        assertEquals(2,s.materials.size());assertEquals("SUCCEEDED",s.generationStatus);assertEquals("succeeded",s.engineTerminalStatus);
        assertEquals("456",s.materials.getLast().productId);assertEquals("READY",s.materials.getLast().status);
        verify(engine,never()).post(any(),any());
    }
    @Test void interruptedGenerationRecoversJobByReadingEngineContext(){
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);var s=new CreationState();s.tenantId="tenant";s.planId="plan";s.enginePlanId="apt";s.generationStatus="SUBMITTING";s.previousEngineJobId="old";
        try{when(engine.get("/api/v1/plan-tasks/apt")).thenReturn(new ObjectMapper().readTree("{\"store_id\":\"tenant\",\"context\":{\"current_generation_job_id\":\"new-job\"}}"));}catch(Exception e){throw new AssertionError(e);}
        when(store.mutate(any(),any(),isNull(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        var service=new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),null,null,null,null,null,mock(PlatformTransactionManager.class));service.process(s);
        assertEquals("RUNNING",s.generationStatus);assertEquals("new-job",s.engineJobId);verify(engine,never()).post(any(),any());
    }
}
