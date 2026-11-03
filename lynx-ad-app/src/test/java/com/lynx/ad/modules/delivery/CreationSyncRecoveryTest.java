package com.lynx.ad.modules.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.service.CreationSyncRecovery;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationSyncRecoveryTest {
    static MaterialProcessing progress(String sync){return new MaterialProcessing("SUCCEEDED","SUCCEEDED","SUCCEEDED",sync,"SYNC","错误码 1511",true,List.of(new MaterialProcessing.Component("VIDEO","SUCCEEDED","SUCCEEDED",sync,"内部详情")));}
    static CreationState.Material material(){var m=new CreationState.Material();m.id="video";m.type="VIDEO";m.productId="123";m.failureStage="SYNC";m.status="FAILED";m.error="千川拒绝上传（错误码 1511）";m.engineMaterialId="generated";return m;}
    @Test void recoveryHasBackoffAndNeverMakesUnconfirmedMaterialsReady() {
        var m=material();CreationSyncRecovery.settle(m,progress("UNCERTAIN"),false,1000);
        assertEquals("PROCESSING",m.status);assertFalse(m.excludedFromDelivery);assertFalse(CreationSyncRecovery.due(m,30_999));assertTrue(CreationSyncRecovery.due(m,31_000));
        CreationSyncRecovery.settle(m,progress("UNCERTAIN"),false,601_000);
        assertEquals("FAILED",m.status);assertTrue(m.excludedFromDelivery);assertFalse(CreationSyncRecovery.due(m,999_000));
    }
    @Test void terminalFailureLeavesOriginalFileAndOnlyRemovesItFromDelivery() throws Exception {
        var s=new CreationState();var m=material();m.videoAssetId="owned-file";m.processing=progress("FAILED");s.materials.add(m);
        var good=material();good.id="ready";good.status="READY";good.failureStage=null;s.materials.add(good);
        CreationSyncRecovery.settle(m,m.processing,true,1000);
        assertEquals(List.of(good),s.activeMaterials());assertEquals(2,s.materials.size());assertEquals("owned-file",m.videoAssetId);
        var restored=new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(s),CreationState.class);assertTrue(restored.materials.getFirst().excludedFromDelivery);
        var view=CreationView.of(s);assertEquals(1,view.materialOutcome().ready());assertEquals(1,view.materialOutcome().failed());assertTrue(view.materialOutcome().generatedFailure());
        assertEquals(1,view.materials().size());assertFalse(s.needsMaterialRecovery());
    }
    @Test void pendingContentReviewDoesNotExposeTechnicalErrorsButRejectsRemainInReviewWorkflow() {
        var s=new CreationState();var m=material();m.engineMaterialId=null;m.failureStage="REVIEW";
        m.processing=new MaterialProcessing("NOT_REQUIRED","SUCCEEDED","PENDING","FAILED","REVIEW","内部同步消息",true,List.of());s.materials.add(m);
        var view=CreationView.of(s).materials().getFirst();assertEquals("PROCESSING",view.status());assertNull(view.error());assertEquals("素材处理中",view.processing().message());assertTrue(view.processing().components().isEmpty());assertFalse(view.processing().canRetrySync());
        CreationSyncRecovery.settle(m,new MaterialProcessing("NOT_REQUIRED","SUCCEEDED","REJECTED","PENDING","REVIEW","内容检查未通过",false,List.of()),false,1000);
        assertEquals("REVIEW",m.failureStage);assertFalse(m.excludedFromDelivery);
    }
    @Test void browserProjectionOmitsAllInternalSyncReasonsWithoutDestroyingDiagnostics() throws Exception {
        var s=new CreationState();var m=material();m.processing=progress("FAILED");s.materials.add(m);s.generationMessage=m.error;
        s.generationIssues.add(new CreationState.GenerationIssue("123","商品",m.error,"VIDEO",m.id,m.title,"slot"));
        var addition=new CreationState.GenerationAddition();addition.materialId=m.id;addition.error=m.error;s.generationAdditions.add(addition);
        var wire=new ObjectMapper().writeValueAsString(CreationView.of(s));assertFalse(wire.contains("1511"));assertFalse(wire.contains("内部详情"));assertFalse(wire.contains("拒绝上传"));assertTrue(m.error.contains("1511"));
    }
    @Test void failedDraftResumesExistingFilesAndRechecksReceiptsWithoutNewGeneration() {
        var s=new CreationState();s.tenantId="tenant";s.userId="user";s.planId="plan";s.accountId="account";s.generationStatus="FAILED";
        var m=material();m.videoAssetId="video-file";m.coverAssetId="cover";m.textAssetId="text";m.titles=List.of("商品实拍展示");s.materials.add(m);
        var store=mock(CreationStore.class);var files=mock(AssetUploadService.class);var engine=mock(CreationEngineClient.class);var importer=mock(CreationAssetImporter.class);var payload=mock(CreationPayloadBuilder.class);
        when(store.get("tenant","plan")).thenReturn(s);when(store.mutate(any(),any(),any(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        var syncFailed=new java.util.concurrent.atomic.AtomicBoolean();
        when(files.detail(any(),anyString())).thenAnswer(call->{String id=call.getArgument(1);return CreationPayloadTest.asset(id,id.equals("video-file")?"VIDEO":id.equals("text")?"TEXT":"IMAGE","APPROVED",id.equals("text")?null:"account",id.equals("text")?null:syncFailed.get()?"FAILED":"UPLOADED","external-"+id);});
        var service=new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),importer,files,null,payload,null,mock(PlatformTransactionManager.class));
        assertTrue(s.needsMaterialRecovery());service.process(s);assertEquals("READY",m.status);assertNull(m.error);assertNull(m.failureStage);assertFalse(s.needsMaterialRecovery());assertFalse(m.excludedFromDelivery);
        verifyNoInteractions(engine,importer);verify(payload).materialReady(s,m);
        // A later failed platform receipt must also reactivate a historically READY draft row.
        syncFailed.set(true);service.get(new TenantId("tenant"),"plan");assertEquals("PROCESSING",m.status);assertTrue(s.needsMaterialRecovery());
        syncFailed.set(false);service.process(s);assertEquals("READY",m.status);assertFalse(s.needsMaterialRecovery());verifyNoInteractions(engine,importer);
    }
    @Test void failedMaterialCannotBypassPublishingChecksWhenNoUsableMaterialRemains() {
        var s=new CreationState();s.tenantId="tenant";s.products.add(new CreationState.Product("123","123","商品",null,null,null,null,null,List.of(),List.of()));
        var m=material();CreationSyncRecovery.settle(m,progress("FAILED"),true,1000);s.materials.add(m);
        var payload=new CreationPayloadBuilder(new ObjectMapper(),mock(AssetUploadService.class),null,null);
        assertThrows(BizException.class,()->payload.creatives(s));assertEquals(0,CreationView.of(s).materialOutcome().ready());
    }
}
