package com.lynx.ad.modules.delivery.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class CreationMaterialSelectionTest {
    final TenantId tenant=new TenantId("tenant");
    final CreationState state=new CreationState();
    final CreationStore store=mock(CreationStore.class);
    final AssetUploadService assets=mock(AssetUploadService.class);
    final CreationAssetImporter importer=mock(CreationAssetImporter.class);
    CreationWorkflowService service;
    @BeforeEach void setup() {
        state.tenantId="tenant";state.userId="user";state.planId="plan";state.accountId="account";state.step=3;state.revision=7;
        state.products.add(new CreationState.Product("p","p","测试商品展示",null,null,null,"",null,List.of(),List.of()));
        when(store.get("tenant","plan")).thenReturn(state);
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{
            Integer revision=call.getArgument(2);if(revision!=null)assertEquals(state.revision,revision);
            Consumer<CreationState> update=call.getArgument(3);update.accept(state);return state;
        });
        when(assets.detail(eq(tenant),anyString())).thenAnswer(call->asset(call.getArgument(1),"oss",null));
        when(importer.ingest(any(),any(),anyString(),anyString(),anyString(),any(),anyBoolean())).thenReturn("text");
        service=new CreationWorkflowService(store,null,null,null,null,new ObjectMapper(),importer,assets,null,null,
                mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
    }
    private AssetUploadService.Detail asset(String id,String mode,String md5) {
        return new AssetUploadService.Detail(id,id.startsWith("cover")?"IMAGE":"VIDEO",id,10,"STORED",mode,false,md5,
                720,1280,null,"",null,"2026-10-31",List.of(),null);
    }
    private CreationState.Material video(String id,String file,boolean generated) {
        var m=new CreationState.Material();m.id=id;m.productId="p";m.type="VIDEO";m.videoAssetId=file;m.coverAssetId="cover";
        m.title="不同的管理标题";m.titles=List.of("商品真实展示标题");m.status="READY";if(generated)m.engineMaterialId="engine-"+id;
        return m;
    }
    private CreationWorkflowService.MaterialSelection select(int revision,String file) {
        return new CreationWorkflowService.MaterialSelection(revision,"p","VIDEO",file,"cover",null,null,List.of("商品真实展示标题"));
    }
    @Test void selectingAnExistingGeneratedVideoIsANoOpEvenWhenRetryUsesTheOldRevision() {
        state.materials.addAll(List.of(video("first","v1",true),video("second","v2",true)));
        service.add(tenant,"plan",select(6,"v2"));
        assertEquals(2,state.materials.size());assertEquals(7,state.revision);
        verify(store,never()).mutate(anyString(),anyString(),any(),any());
        verify(assets,never()).ensureTarget(any(),anyString(),anyString());verifyNoInteractions(importer);
    }
    @Test void addAndReplayOnlyCreatesOneRowAndStaleDifferentSelectionStillConflicts() {
        state.materials.add(video("first","v1",true));
        var request=select(7,"v2");service.add(tenant,"plan",request);service.add(tenant,"plan",request);
        assertEquals(2,state.materials.size());assertEquals(8,state.revision);
        verify(importer,times(1)).ingest(any(),any(),anyString(),anyString(),anyString(),any(),anyBoolean());
        assertEquals(1404,assertThrows(BizException.class,()->service.add(tenant,"plan",select(7,"other-video"))).code());
    }
    @Test void concurrentIdenticalAdditionIsCheckedAgainUnderTheRowLock() {
        when(store.mutate(eq("tenant"),eq("plan"),isNull(),any())).thenAnswer(call->{
            state.materials.add(video("other-request","v2",false));state.mediaChanged();
            Consumer<CreationState> update=call.getArgument(3);update.accept(state);return state;
        });
        service.add(tenant,"plan",select(7,"v2"));
        assertEquals(1,state.materials.size());assertEquals("other-request",state.materials.getFirst().id);assertEquals(8,state.revision);
    }
    @Test void openingDraftRepairsAccumulatedCopiesAndKeepsTheOriginalGeneratedRow() {
        state.publishedEdit=new CreationState.PublishedEdit();state.publishedEdit.status="DRAFT";
        state.materials.addAll(List.of(video("copy-first","v2",false),video("original","v2",true),video("copy-later","v2",false),video("different","v1",true)));
        service.get(tenant,"plan");service.get(tenant,"plan");
        assertEquals(List.of("original","different"),state.materials.stream().map(m->m.id).toList());
        assertEquals(Set.of("copy-first","copy-later"),new HashSet<>(state.removedMaterialIds));assertEquals(8,state.revision);
        verify(assets,never()).ensureTarget(any(),anyString(),anyString());verifyNoInteractions(importer);
    }
    @Test void sameFileFingerprintAndCanonicalAliasDeduplicateButDifferentProductsAndVersionsRemain() {
        String fingerprint="0123456789abcdef0123456789abcdef";
        when(assets.detail(tenant,"v1")).thenReturn(asset("v1","oss",fingerprint));
        when(assets.detail(tenant,"alias")).thenReturn(asset("alias","oss",fingerprint));
        state.materials.add(video("generated","v1",true));
        var selections=new CreationMaterialSelections(assets,tenant);
        assertTrue(selections.contains(state,video("selected","alias",false)));
        var other=video("other-product","alias",false);other.productId="other";state.materials.add(other);
        var version=video("version","alias",false);version.reviewSourceId="generated";version.versionDecision="KEEP_BOTH";state.materials.add(version);
        assertTrue(selections.duplicateIds(state).isEmpty());
        assertFalse(selections.contains(state,video("missing",null,false)));
    }
    @Test void publishedAndSavingSelectionsAreNeverRepaired() {
        state.materials.addAll(List.of(video("original","v1",true),video("copy","v1",false)));
        state.publishStatus="CREATED";assertFalse(CreationMaterialSelections.repairable(state));
        state.publishStatus="NONE";state.publishedEdit=new CreationState.PublishedEdit();state.publishedEdit.status="SAVING";
        assertFalse(CreationMaterialSelections.repairable(state));
    }
    @Test void reusedGeneratedFileRetainsItsProvenanceWithoutAcquiringRegenerationBindings() {
        when(assets.detail(tenant,"generated-file")).thenReturn(asset("generated-file","engine_oss",null));
        var m=video("library","generated-file",false);state.materials.add(m);
        var view=CreationView.of(service.get(tenant,"plan")).materials().getFirst();
        assertEquals("LYNX",view.source());assertNull(view.engineMaterialId());assertNull(m.engineJobId);
        state.materials.clear();state.materials.add(video("upload","local-file",false));
        assertEquals("UPLOAD",CreationView.of(service.get(tenant,"plan")).materials().getFirst().source());
    }
    @Test void imageAndCarouselSelectionsAlsoUseMediaIdentityRatherThanTitles() {
        var image=new CreationState.Material();image.id="image";image.type="IMAGE";image.productId="p";image.imageAssetIds=List.of("image-file");
        var carousel=new CreationState.Material();carousel.id="carousel";carousel.type="CAROUSEL";carousel.productId="p";carousel.carouselId="bundle";
        state.materials.addAll(List.of(image,carousel));var selections=new CreationMaterialSelections(assets,tenant);
        var picked=new CreationState.Material();picked.productId="p";picked.type="IMAGE";picked.imageAssetIds=List.of("image-file");
        assertTrue(selections.contains(state,picked));picked.type="CAROUSEL";picked.carouselId="bundle";assertTrue(selections.contains(state,picked));
    }
}
