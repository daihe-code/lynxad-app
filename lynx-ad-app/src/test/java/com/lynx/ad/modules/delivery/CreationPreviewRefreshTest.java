package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class CreationPreviewRefreshTest {
    @Test void openingLegacyDraftDurablyRemovesOnlyReplacedPhotoAndKeepsHistoryIds() {
        var store=mock(CreationStore.class);var assets=mock(AssetUploadService.class);
        var state=new CreationState();state.planId="plan";state.generationStatus="FAILED";
        var base=material("base","IMAGE");base.deliverySource="product_reference";base.deliveryFamilyId="slot0";
        var ai=material("ai","IMAGE");ai.deliverySource="ai_generated";ai.deliveryFamilyId="slot0";
        base.productId=ai.productId="p";ai.status="READY";ai.imageAssetIds.add("ai-file");
        state.materials.addAll(List.of(base,ai));
        when(store.get("tenant","plan")).thenReturn(state);
        when(store.mutate(eq("tenant"),eq("plan"),eq(1),any())).thenAnswer(call->{
            Consumer<CreationState> update=call.getArgument(3);update.accept(state);return state;});
        when(assets.detail(any(),anyString())).thenReturn(CreationPayloadTest.asset("ai-file","IMAGE","APPROVED",null,null,null));
        var view=service(store,null,assets).get(new TenantId("tenant"),"plan");
        assertEquals(List.of("ai"),view.materials.stream().map(m->m.id).toList());
        assertEquals(List.of("base"),state.removedMaterialIds);
        verify(store).mutate(eq("tenant"),eq("plan"),eq(1),any());
    }
    @Test void sameMaterialIdAdoptsLatestCoverAndProductImageAndInvalidatesConfirmation() {
        var store=mock(CreationStore.class);var importer=mock(CreationAssetImporter.class);var assets=mock(AssetUploadService.class);
        var state=new CreationState();state.tenantId="tenant";state.userId="user";state.accountId="account";state.planId="plan";
        state.generationStatus="FAILED";state.confirmedVersion=state.packageVersion;
        var video=material("video","VIDEO");video.videoAssetId="video-file";video.coverAssetId="old-cover";
        var image=material("image","IMAGE");image.imageAssetIds.add("old-image");state.materials.addAll(List.of(video,image));
        when(store.get("tenant","plan")).thenReturn(state);
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{Consumer<CreationState> update=call.getArgument(3);update.accept(state);return state;});
        when(importer.reference(any(),any(),anyString(),eq("job"),anyString(),anyString(),anyString(),eq("account"),eq(false)))
                .thenAnswer(call->switch((String)call.getArgument(5)){case "COVER"->"new-cover";case "IMAGE_0"->"new-image";default->"video-file";});
        when(assets.detail(any(),anyString())).thenAnswer(call->CreationPayloadTest.asset(call.getArgument(1),"IMAGE","APPROVED",null,null,null));
        int version=state.packageVersion;
        var view=service(store,importer,assets).get(new TenantId("tenant"),"plan");
        assertEquals("video",view.materials.getFirst().id);assertEquals("new-cover",view.materials.getFirst().coverAssetId);
        assertEquals(List.of("new-image"),view.materials.getLast().imageAssetIds);
        assertEquals("PROCESSING",view.materials.getFirst().status);assertNull(view.confirmedVersion);assertEquals(version+1,view.packageVersion);
        assertEquals("old-cover",video.coverAssetId);assertEquals(List.of("old-image"),image.imageAssetIds);
        service(store,importer,assets).get(new TenantId("tenant"),"plan");
        assertEquals(version+1,state.packageVersion);
    }
    @Test void publishedPlansKeepTheExactFilesThatWerePublished() {
        var store=mock(CreationStore.class);var importer=mock(CreationAssetImporter.class);
        var state=new CreationState();state.planId="plan";state.publishStatus="CREATED";state.generationStatus="SUCCEEDED";
        var image=material("image","IMAGE");image.imageAssetIds.add("published-image");state.materials.add(image);
        when(store.get("tenant","plan")).thenReturn(state);
        var assets=mock(AssetUploadService.class);
        when(assets.detail(any(),anyString())).thenReturn(CreationPayloadTest.asset("published-image","IMAGE","APPROVED",null,null,null));
        service(store,importer,assets).get(new TenantId("tenant"),"plan");verifyNoInteractions(importer);
        assertEquals(List.of("published-image"),state.materials.getFirst().imageAssetIds);
    }
    private static CreationState.Material material(String id,String type) {
        var m=new CreationState.Material();m.id=id;m.type=type;m.engineJobId="job";m.engineMaterialId=id;m.status="FAILED";return m;
    }
    private static CreationWorkflowService service(CreationStore store,CreationAssetImporter importer,AssetUploadService assets) {
        return new CreationWorkflowService(store,null,null,null,null,new ObjectMapper(),importer,assets,null,null,null,mock(PlatformTransactionManager.class));
    }
}
