package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;

class CreationMaterialProcessingTest {
    AssetUploadService assets;CreationState state;CreationState.Material material;CreationMaterialProcessing progress;
    final TenantId tenant=new TenantId("tenant");
    @BeforeEach void setup() {
        assets=mock(AssetUploadService.class);progress=new CreationMaterialProcessing(assets,null);
        state=new CreationState();state.tenantId="tenant";state.planId="plan";state.accountId="account";
        state.engineJobId="job";state.engineTerminalStatus="succeeded";state.generationStatus="FAILED";
        material=new CreationState.Material();material.id="material";material.productId="123";material.type="VIDEO";
        material.engineMaterialId="generated";material.engineJobId="job";material.videoAssetId="video";material.coverAssetId="cover";material.textAssetId="text";material.status="FAILED";
        material.titles=List.of("完整的商品展示标题");state.materials.add(material);
        when(assets.detail(tenant,"video")).thenReturn(asset("video","VIDEO","APPROVED","UPLOADED"));
        when(assets.detail(tenant,"cover")).thenReturn(asset("cover","IMAGE","APPROVED","FAILED"));
        when(assets.detail(tenant,"text")).thenReturn(asset("text","TEXT","APPROVED",null));
    }
    static AssetUploadService.Detail asset(String id,String kind,String review,String sync) {
        return CreationPayloadTest.asset(id,kind,review,sync==null?null:"account",sync,"UPLOADED".equals(sync)?"real-platform-id":null);
    }
    @Test void generationCompletionDoesNotDependOnPreviewAndOnlyFailedCoverIsRetryable() {
        var result=progress.inspect(state,material);
        assertEquals("SUCCEEDED",result.progress().generationStatus());assertEquals("SUCCEEDED",MaterialProcessing.generationResult(state));
        assertEquals("SYNC",result.progress().stage());assertEquals("FAILED",result.progress().syncStatus());
        assertTrue(result.progress().canRetrySync());assertEquals(List.of("cover"),result.retryAssets());
        assertNull(material.previewUrl);assertEquals("SUCCEEDED",result.progress().components().stream().filter(c->"VIDEO".equals(c.component())).findFirst().orElseThrow().syncStatus());
    }
    @Test void uncertainUploadIsNotClassifiedAsFailedOrRetryableEvenIfOtherComponentFailed() {
        when(assets.detail(tenant,"video")).thenReturn(asset("video","VIDEO","APPROVED","UNCERTAIN"));
        var p=progress.inspect(state,material).progress();assertEquals("UNCERTAIN",p.syncStatus());assertFalse(p.canRetrySync());
    }
    @Test void qualityFailureIsNotMisreportedAsUploadFailure() {
        when(assets.detail(tenant,"cover")).thenReturn(asset("cover","IMAGE","REJECTED","WAITING_REVIEW"));
        var p=progress.inspect(state,material).progress();assertEquals("REVIEW",p.stage());assertEquals("REJECTED",p.reviewStatus());assertFalse(p.canRetrySync());
    }
    @Test void registrationFailurePreservesGenerationSuccessAndBlocksUploadRetry() {
        material.coverAssetId=null;material.failureStage="REGISTRATION";
        var p=progress.inspect(state,material).progress();assertEquals("SUCCEEDED",p.generationStatus());assertEquals("FAILED",p.storageStatus());
        assertEquals("REGISTRATION",p.stage());assertFalse(p.canRetrySync());
    }
    @Test void expiredPreviewDoesNotChangeReadyStateAndForeignAccountReceiptsAreNotUsed() {
        when(assets.detail(tenant,"cover")).thenReturn(asset("cover","IMAGE","APPROVED","UPLOADED"));material.status="READY";
        assertEquals("READY",progress.inspect(state,material).progress().stage());
        var a=CreationPayloadTest.asset("cover","IMAGE","APPROVED","other-account","UPLOADED","not-owned-by-this-account");
        when(assets.detail(tenant,"cover")).thenReturn(a);
        var p=progress.inspect(state,material).progress();assertEquals("PENDING",p.syncStatus());assertNotEquals("READY",p.stage());
    }
    @Test void missingReceiptsAreUnknownAndPublicProjectionDoesNotContainAssetIdsOrSourceUrls() {
        when(assets.detail(tenant,"cover")).thenThrow(new BizException(1503,"unavailable"));
        material.sourceUrls.put("COVER","https://private.test/signed?secret=private");
        progress.hydrate(tenant,state);var wire=new ObjectMapper().valueToTree(CreationView.of(state));
        assertEquals("UNKNOWN",wire.path("materials").get(0).path("processing").path("storageStatus").asText());
        assertFalse(wire.toString().contains("private.test"));assertFalse(wire.toString().contains("videoAssetId"));
        assertFalse(wire.path("materials").get(0).path("processing").toString().contains("real-platform-id"));
    }
    @Test void textValidationFailureIsSeparateFromSuccessfulFileUploads() {
        when(assets.detail(tenant,"cover")).thenReturn(asset("cover","IMAGE","APPROVED","UPLOADED"));
        material.failureStage="VALIDATION";material.error="素材标题过短";
        var p=progress.inspect(state,material).progress();assertEquals("SUCCEEDED",p.syncStatus());assertEquals("VALIDATION",p.stage());assertFalse(p.canRetrySync());
    }
    @Test void sharedAssetsAreReadOnlyOncePerHydration() {
        var other=new CreationState.Material();other.id="other";other.type="VIDEO";other.videoAssetId="video";other.coverAssetId="cover";other.textAssetId="text";state.materials.add(other);
        progress.hydrate(tenant,state);
        verify(assets,times(1)).detail(tenant,"video");verify(assets,times(1)).detail(tenant,"cover");verify(assets,times(1)).detail(tenant,"text");
        verify(assets,never()).retry(any(),any(),any(),anyBoolean());
    }
    @Test void carouselAssemblyUncertaintyIsVisibleWithoutReissuingCreation() {
        material.type="CAROUSEL";material.videoAssetId=null;material.coverAssetId=null;material.audioAssetId="audio";material.imageAssetIds=List.of("cover");material.carouselId="bundle";
        when(assets.detail(tenant,"cover")).thenReturn(asset("cover","IMAGE","APPROVED","UPLOADED"));
        when(assets.detail(tenant,"audio")).thenReturn(asset("audio","AUDIO","APPROVED","UPLOADED"));
        var platform=mock(CarouselPlatformService.class);when(platform.receipt(tenant,"bundle","account")).thenReturn(new CarouselPlatformService.Receipt("UNCERTAIN",null));progress.carouselPlatform(platform);
        var p=progress.inspect(state,material).progress();assertEquals("UNCERTAIN",p.syncStatus());assertFalse(p.canRetrySync());
        verify(platform,never()).ensure(any(),any(),any());
    }
    @Test void terminalSummaryCannotComeFromThePrecedingAdditionOrInventMissingOutputs() throws Exception {
        state.pendingAdditionId="addition";state.engineTerminalJobId="old-job";
        assertEquals("UNKNOWN",MaterialProcessing.generationResult(state));
        state.engineTerminalJobId="job";assertEquals("SUCCEEDED",MaterialProcessing.generationResult(state));
        state.generationCounts=List.of(new CreationState.GenerationCount("VIDEO",1,2));
        assertEquals("UNKNOWN",MaterialProcessing.generationResult(state));
        state.engineJobId=null;assertEquals("FAILED",MaterialProcessing.generationResult(state));
        material.failureStage="SYNC";material.failureCode=1523;material.processing=progress.inspect(state,material).progress();
        var json=new ObjectMapper();var saved=json.readValue(json.writeValueAsString(state),CreationState.class);
        assertEquals(1523,saved.materials.getFirst().failureCode);assertNull(saved.materials.getFirst().processing);
    }
    @Test void syncRetryReusesAssetsAndNeverCallsGenerationOrRetriesSuccessfulVideo() {
        var store=store();var engine=mock(CreationEngineClient.class);
        workflow(store,engine).retryMaterialSync(tenant,"plan","material",new CreationWorkflowService.Version(1));
        verify(assets).retry(tenant,"cover","account",false);verify(assets,never()).retry(eq(tenant),eq("video"),any(),anyBoolean());
        verifyNoInteractions(engine);assertEquals("PROCESSING",material.status);assertEquals("IMPORTING",state.generationStatus);
        assertEquals("SUCCEEDED",state.engineTerminalStatus.toUpperCase());verify(store).release(state);
    }
    @Test void staleVersionOrActiveWorkerCannotQueueUploads() {
        var store=store();var service=workflow(store,mock(CreationEngineClient.class));
        assertEquals(1404,assertThrows(BizException.class,()->service.retryMaterialSync(tenant,"plan","material",new CreationWorkflowService.Version(8))).code());
        when(store.claim(state)).thenReturn(false);
        assertThrows(BizException.class,()->service.retryMaterialSync(tenant,"plan","material",new CreationWorkflowService.Version(1)));
        verify(assets,never()).retry(any(),any(),any(),anyBoolean());
    }
    @Test void uncertainOrUnapprovedComponentsFailBeforeAnySyncMutation() {
        var store=store();var engine=mock(CreationEngineClient.class);var service=workflow(store,engine);
        when(assets.detail(tenant,"video")).thenReturn(asset("video","VIDEO","APPROVED","UNCERTAIN"));
        assertEquals(1523,assertThrows(BizException.class,()->service.retryMaterialSync(tenant,"plan","material",new CreationWorkflowService.Version(1))).code());
        verify(assets,never()).retry(any(),any(),any(),anyBoolean());verifyNoInteractions(engine);
        verify(store,never()).mutate(any(),any(),any(),any());
    }
    @Test void readOnlyVerificationChecksOneUncertainComponentWithoutGenerationOrQueueChanges() {
        var store=store();var engine=mock(CreationEngineClient.class);var service=workflow(store,engine);var verifier=mock(AssetSyncReconciliationService.class);service.assetReconciliation(verifier);
        when(assets.detail(tenant,"video")).thenReturn(asset("video","VIDEO","APPROVED","UNCERTAIN"));
        when(assets.detail(tenant,"cover")).thenReturn(asset("cover","IMAGE","APPROVED","UNCERTAIN"));
        when(verifier.reconcile(tenant,"video","account")).thenAnswer(call->{when(assets.detail(tenant,"video")).thenReturn(asset("video","VIDEO","APPROVED","UPLOADED"));return new AssetSyncReconciliationService.Result("CONFIRMED","已补齐回执",null);});
        var result=service.verifyMaterialSync(tenant,"plan","material",new CreationWorkflowService.Version(1));
        assertEquals("CONFIRMED",result.state());assertEquals("UNCERTAIN",result.processing().syncStatus());assertEquals(1,result.revision());assertTrue(result.message().contains("其他组件"));
        verify(verifier).reconcile(tenant,"video","account");verifyNoMoreInteractions(verifier);verifyNoInteractions(engine);
        verify(store,never()).claim(any());verify(store,never()).mutate(any(),any(),any(),any());verify(assets,never()).retry(any(),any(),any(),anyBoolean());verify(assets,never()).ensureTarget(any(),any(),any());
    }
    @Test void staleReadOnlyVerificationCannotQueryOrOverwriteNewMaterialRevision() {
        var store=store();var service=workflow(store,mock(CreationEngineClient.class));var verifier=mock(AssetSyncReconciliationService.class);service.assetReconciliation(verifier);
        assertEquals(1404,assertThrows(BizException.class,()->service.verifyMaterialSync(tenant,"plan","material",new CreationWorkflowService.Version(8))).code());verifyNoInteractions(verifier);verify(store,never()).mutate(any(),any(),any(),any());
    }
    CreationStore store() {
        var store=mock(CreationStore.class);when(store.get("tenant","plan")).thenReturn(state);when(store.claim(state)).thenReturn(true);
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{Integer expected=call.getArgument(2);if(expected!=null&&expected!=state.revision)throw new BizException(1404,"version");Consumer<CreationState> change=call.getArgument(3);change.accept(state);return state;});return store;
    }
    CreationWorkflowService workflow(CreationStore store,CreationEngineClient engine) {
        return new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),null,assets,null,null,mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
    }
}
