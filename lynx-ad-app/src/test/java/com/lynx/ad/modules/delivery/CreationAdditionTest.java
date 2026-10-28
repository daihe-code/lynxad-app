package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;

class CreationAdditionTest {
    final ObjectMapper json=new ObjectMapper();final CreationState s=new CreationState();
    final CreationStore store=mock(CreationStore.class);final CreationEngineClient engine=mock(CreationEngineClient.class);
    final CreationAssetImporter importer=mock(CreationAssetImporter.class);final AssetUploadService assets=mock(AssetUploadService.class);
    final CarouselService carousels=mock(CarouselService.class);
    CreationWorkflowService service;
    @BeforeEach void setup() {
        s.tenantId="tenant";s.userId="user";s.planId="plan";s.accountId="account";s.enginePlanId="task";s.name="新增素材测试";s.step=3;
        s.analysisStatus="SUCCEEDED";s.budget=new BigDecimal("300");s.roi2Goal=new BigDecimal("1.5");s.confirmedVersion=s.packageVersion;
        s.products.add(new CreationState.Product("123","123","商品",null,null,"https://assets.test/product.jpg","",null,List.of(),List.of()));
        var old=new CreationState.Material();old.id="old";old.type="VIDEO";old.productId="123";old.status="READY";s.materials.add(old);
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(importer.reference(any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any(),anyBoolean())).thenAnswer(call->call.getArgument(2));
        when(assets.detail(any(),anyString())).thenAnswer(call->CreationPayloadTest.asset(call.getArgument(1),"VIDEO","APPROVED",null,null,null));
        when(carousels.create(any(),any(),any())).thenReturn(new CarouselService.Detail("carousel","新增图文","",List.of(),null,List.of()));
        service=new CreationWorkflowService(store,null,null,null,engine,json,importer,assets,carousels,mock(CreationPayloadBuilder.class),mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
    }
    CreationWorkflowService.Addition body(String type,String description){return new CreationWorkflowService.Addition(s.revision,"123",type,description,"request");}
    @ParameterizedTest @ValueSource(strings={"VIDEO","IMAGE","CAROUSEL"})
    void publishedPlanWorkingCopyQueuesAdditionWithoutChangingLivePlan(String type) {
        s.publishedEdit=new CreationState.PublishedEdit();s.publishedEdit.sourcePlanId="live-plan";s.publishedEdit.targetPlanId="live-plan";
        s.publishedEdit.planStatus="PAUSED";
        var updated=service.addition(new TenantId("tenant"),"plan",body(type,"新增场景"));
        assertEquals("DRAFT",updated.publishedEdit.status);assertEquals("NONE",updated.publishStatus);
        assertEquals(1,updated.materials.size());assertEquals(1,updated.generationAdditions.size());
        verifyNoInteractions(engine);
    }
    @Test void manualVideoNeedsNoCoverOrTitleInputsAndReusesProductCopy() {
        var pool=new CreationState.TitlePool();pool.customTitles.add("户外商品实拍展示标题");s.titlePools.put("123",pool);
        when(assets.videoCover(new TenantId("tenant"),new com.lynx.ad.common.domain.Ids.UserId("user"),"uploaded-video")).thenReturn("automatic-cover");
        when(assets.detail(new TenantId("tenant"),"automatic-cover")).thenReturn(CreationPayloadTest.asset("automatic-cover","IMAGE","APPROVED",null,null,null));
        var result=service.add(new TenantId("tenant"),"plan",new CreationWorkflowService.MaterialSelection(s.revision,"123","VIDEO","uploaded-video",null,null,null,null));
        var added=result.materials.getLast();assertEquals("automatic-cover",added.coverAssetId);assertEquals(pool.customTitles,added.titles);
        verify(assets).ensureTarget(new TenantId("tenant"),"uploaded-video","account");verify(assets).ensureTarget(new TenantId("tenant"),"automatic-cover","account");
        assertEquals(2,result.materials.size());assertEquals("old",result.materials.getFirst().id);
    }
    @ParameterizedTest @ValueSource(strings={"VIDEO","IMAGE","CAROUSEL"})
    void acceptsSingleFormatWithoutAnEarlierGenerationJob(String type) throws Exception {
        var r=service.addition(new TenantId("tenant"),"plan",body(type,"突出通勤场景"));
        assertEquals(1,r.generationAdditions.size());assertEquals(type,r.generationAdditions.getFirst().type);
        assertEquals("突出通勤场景",r.generationAdditions.getFirst().engineRequest.path("instruction").asText());
        assertFalse(r.generationAdditions.getFirst().engineRequest.has("job_id"));assertEquals(1,r.materials.size());
        var reloaded=json.readValue(json.writeValueAsString(r),CreationState.class);
        assertEquals("突出通勤场景",CreationView.of(reloaded).generationAdditions().getFirst().description());
    }
    @Test void duplicateRequestKeepsSnapshotAndDoesNotQueueAgain() {
        var original=body("VIDEO","");service.addition(new TenantId("tenant"),"plan",original);
        int revision=s.revision;service.addition(new TenantId("tenant"),"plan",original);
        assertEquals(1,s.generationAdditions.size());assertEquals(revision,s.revision);
        assertThrows(BizException.class,()->service.addition(new TenantId("tenant"),"plan",body("VIDEO","不同描述")));
    }
    @Test void rejectsCrossProductAndLongDescriptionAndStaleRevision() {
        assertThrows(BizException.class,()->service.addition(new TenantId("tenant"),"plan",new CreationWorkflowService.Addition(s.revision,"other","VIDEO","","request")));
        assertThrows(BizException.class,()->service.addition(new TenantId("tenant"),"plan",body("VIDEO","字".repeat(51))));
        assertThrows(BizException.class,()->service.addition(new TenantId("tenant"),"plan",new CreationWorkflowService.Addition(0,"123","VIDEO","","request")));
        assertTrue(s.generationAdditions.isEmpty());
    }
    @Test void submissionTimeoutQueriesReceiptWithoutPostingAgain() throws Exception {
        service.addition(new TenantId("tenant"),"plan",body("VIDEO","通勤"));
        when(engine.post(any(),any())).thenThrow(new BizException(8003,"reply lost"));service.process(s);
        assertEquals("UNCERTAIN",s.generationStatus);
        when(engine.find(any())).thenReturn(json.readTree("{\"request_id\":\"request\",\"product_id\":\"123\",\"target\":\"video\",\"ai_plan_task_id\":\"task\",\"job_id\":\"new-job\"}"));
        service.process(s);assertEquals("new-job",s.generationAdditions.getFirst().engineJobId);verify(engine,times(1)).post(any(),any());
    }
    @Test void failedNewJobCannotSucceedBecauseOldMaterialIsReady() throws Exception {
        service.addition(new TenantId("tenant"),"plan",body("VIDEO","通勤"));s.generationAdditions.getFirst().engineJobId="new-job";
        when(engine.get(any())).thenReturn(json.readTree("{\"job_id\":\"new-job\",\"status\":\"failed\",\"groups\":[]}"));
        service.process(s);assertEquals("FAILED",s.generationStatus);assertEquals("old",s.materials.getFirst().id);assertEquals("READY",s.materials.getFirst().status);
    }
    @ParameterizedTest @ValueSource(strings={"VIDEO","IMAGE","CAROUSEL"})
    void importsOnlyNewOutputPreservesOldMaterialAndExposesDescription(String type) throws Exception {
        service.addition(new TenantId("tenant"),"plan",body(type,"通勤"));s.generationAdditions.getFirst().engineJobId="new-job";
        String target=type.equals("VIDEO")?"video":type.equals("IMAGE")?"product_card":"image_text";
        var job=json.readTree("""
            {"job_id":"new-job","status":"succeeded","groups":[{"group_id":"group-1","materials":[{"material_id":"new","version":"v1","status":"succeeded","material_type":"TARGET","content":{"target_product_id":"123","title":"真实商品素材标题"},"urls":{"video_url":"https://assets.test/video.mp4","cover_url":"https://assets.test/cover.jpg","voice_url":"https://assets.test/voice.mp3","image_urls":["https://assets.test/image.jpg"]},"detail":{"parent_material_id":"old"}}]}]}
            """.replace("TARGET",target));
        when(engine.get(any())).thenReturn(job);service.process(s);
        assertEquals("SUCCEEDED",s.generationStatus);assertEquals(2,s.materials.size());assertEquals("old",s.materials.getFirst().id);
        assertNull(s.materials.getLast().replacesId);assertEquals("通勤",CreationView.of(s).materials().getLast().generationDescription());
        assertNull(s.confirmedVersion);assertEquals(100,s.progress);assertEquals(1,s.generationCounts.getFirst().completed());
        int count=s.materials.size();service.process(s);assertEquals(count,s.materials.size());
    }
    @Test void refusesWrongTypeOrProductWithoutImporting() throws Exception {
        service.addition(new TenantId("tenant"),"plan",body("VIDEO",""));s.generationAdditions.getFirst().engineJobId="new-job";
        when(engine.get(any())).thenReturn(json.readTree("{\"job_id\":\"new-job\",\"status\":\"succeeded\",\"groups\":[{\"materials\":[{\"material_id\":\"wrong\",\"version\":\"v1\",\"status\":\"succeeded\",\"material_type\":\"product_card\"}]}]}"));
        service.process(s);assertEquals("FAILED",s.generationStatus);assertEquals(1,s.materials.size());verifyNoInteractions(importer);
    }
}
