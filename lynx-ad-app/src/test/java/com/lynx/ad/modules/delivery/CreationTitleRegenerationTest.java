package com.lynx.ad.modules.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.CreationAssetImporter;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.application.CreationTitleRegeneration;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.ProductTitlePool;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationTitleRegenerationTest {
    CreationStore store=mock(CreationStore.class);CreationEngineClient engine=mock(CreationEngineClient.class);
    CreationAssetImporter importer=mock(CreationAssetImporter.class);CreationState s=new CreationState();ObjectMapper json=new ObjectMapper();
    CreationTitleRegeneration service=new CreationTitleRegeneration(store,engine,importer);
    @BeforeEach void setup(){
        s.tenantId="tenant";s.planId="plan";s.userId="user";s.revision=7;
        s.products.add(new CreationState.Product("p1","p1","户外双肩包",null,null,null,"背包",null,List.of(),List.of()));
        var m=new CreationState.Material();m.id="video";m.productId="p1";m.type="VIDEO";m.engineMaterialId="engine-video";m.videoAssetId="original-video";m.coverAssetId="original-cover";m.status="READY";m.titles.addAll(List.of("通勤出行看看双肩包","户外出行展示收纳细节"));s.materials.add(m);
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(importer.ingest(any(),any(),any(),any(),eq("TEXT"),isNull(),eq(false))).thenReturn("reviewed-text");
        when(engine.post(eq("/api/v1/capabilities/generation/titles"),any())).thenAnswer(call->{Map<?,?> body=call.getArgument(1);return json.valueToTree(Map.of("request_id",body.get("request_id"),"status","SUCCEEDED","product_id","p1","kind",body.get("kind"),"original",body.get("original"),"title","看看双肩包的户外收纳细节"));});
    }
    @Test void regeneratesOneTitleAndPreservesAllMediaAndOtherTitles(){
        int version=s.packageVersion;
        service.submit(s,"p1","CUSTOM",0,"通勤出行看看双肩包");
        var pool=s.titlePools.get("p1");assertEquals(List.of("看看双肩包的户外收纳细节","户外出行展示收纳细节"),pool.customTitles);
        assertEquals(new HashSet<>(pool.customTitles),new HashSet<>(pool.customGeneratedTitles));assertEquals("reviewed-text",pool.customTextAssetId);
        assertEquals("SUCCEEDED",s.titleGeneration.status);assertEquals(version+1,s.packageVersion);
        assertEquals("original-video",s.materials.getFirst().videoAssetId);assertEquals("original-cover",s.materials.getFirst().coverAssetId);assertEquals("READY",s.materials.getFirst().status);
        verify(engine,times(1)).post(eq("/api/v1/capabilities/generation/titles"),any());verifyNoMoreInteractions(engine);
        verify(importer,times(1)).ingest(eq(new TenantId("tenant")),eq(new UserId("user")),any(),any(),eq("TEXT"),isNull(),eq(false));
    }
    @Test void timeoutRecoveryReadsSameReceiptAndNeverRepeatsPost(){
        when(engine.post(anyString(),any())).thenThrow(new BizException(8003,"timeout"));
        service.submit(s,"p1","CUSTOM",0,"通勤出行看看双肩包");assertTrue(s.titleGenerationPending());assertTrue(s.titlePools.isEmpty());
        when(engine.find(anyString())).thenAnswer(call->json.valueToTree(Map.of("request_id",s.titleGeneration.requestId,"status","SUCCEEDED","product_id","p1","kind","CUSTOM","original",s.titleGeneration.original,"title","看看双肩包的户外收纳细节")));
        service.refresh(s);service.refresh(s);
        assertEquals("SUCCEEDED",s.titleGeneration.status);verify(engine,times(1)).post(anyString(),any());verify(engine,times(1)).find(contains("store_id=tenant"));
    }
    @Test void invalidRepeatedAndWrongReceiptKeepOriginalAndNeverImport(){
        for(String title:List.of("短","通勤出行看看双肩包","中".repeat(56))){
            when(engine.post(anyString(),any())).thenAnswer(call->json.valueToTree(Map.of("request_id",s.titleGeneration.requestId,"status","SUCCEEDED","product_id","p1","kind","CUSTOM","original",s.titleGeneration.original,"title",title)));
            service.submit(s,"p1","CUSTOM",0,"通勤出行看看双肩包");assertEquals("FAILED",s.titleGeneration.status);assertTrue(s.titlePools.isEmpty());
        }
        when(engine.post(anyString(),any())).thenReturn(json.createObjectNode().put("request_id","other").put("status","SUCCEEDED"));
        service.submit(s,"p1","CUSTOM",0,"通勤出行看看双肩包");assertEquals("FAILED",s.titleGeneration.status);verifyNoInteractions(importer);
    }
    @Test void staleOrForeignTitleNeverReachesEngine(){
        assertThrows(BizException.class,()->service.submit(s,"p1","CUSTOM",0,"过时的标题"));
        assertThrows(BizException.class,()->service.submit(s,"other","CUSTOM",0,"过时的标题"));verifyNoInteractions(engine,importer);
    }
    @Test void provenanceIsProjectedForLegacyGenerationAndManualPoolEdits(){
        assertEquals(s.materials.getFirst().titles,CreationView.of(s).titlePools().get("p1").customGeneratedTitles());
        var pool=ProductTitlePool.of(s,"p1");pool.customTitles=List.of("自己修改后的标题");pool.customGeneratedTitles.clear();s.titlePools.put("p1",pool);
        assertTrue(CreationView.of(s).titlePools().get("p1").customGeneratedTitles().isEmpty());
        assertThrows(BizException.class,()->ProductTitlePool.validate(List.of("中".repeat(31)),true));
    }
    @Test void addingAPreviouslyMissingMediaTypeKeepsItsGeneratedTitlesVisible(){
        s.titlePools.put("p1",ProductTitlePool.of(s,"p1"));
        var image=new CreationState.Material();image.id="image";image.type="IMAGE";image.productId="p1";image.engineMaterialId="engine-image";image.titles.add("户外双肩包的外观展示");s.materials.add(image);
        var pool=ProductTitlePool.of(s,"p1");assertEquals(image.titles,pool.cardTitles);assertEquals(image.titles,pool.cardGeneratedTitles);
    }
    @Test void addingGeneratedTitleAppendsWithoutReplacingExistingTitlesOrMedia(){
        var before=new ArrayList<>(s.materials.getFirst().titles);
        service.add(s,"p1","CUSTOM");
        var pool=s.titlePools.get("p1");assertEquals(before,pool.customTitles.subList(0,before.size()));
        assertEquals("看看双肩包的户外收纳细节",pool.customTitles.getLast());assertTrue(pool.customGeneratedTitles.contains(pool.customTitles.getLast()));
        assertTrue(s.titleGeneration.addition);assertEquals("标题已新增",s.titleGeneration.message);
        assertEquals(before,s.materials.getFirst().titles);assertEquals("original-video",s.materials.getFirst().videoAssetId);
        verify(engine,times(1)).post(eq("/api/v1/capabilities/generation/titles"),any());
    }
    @Test void anEmptyCardPoolCanGenerateWithoutSavingAPlaceholder(){
        assertTrue(ProductTitlePool.of(s,"p1").cardTitles.isEmpty());
        service.add(s,"p1","CARD");
        assertEquals(List.of("看看双肩包的户外收纳细节"),s.titlePools.get("p1").cardTitles);
        assertEquals(s.titlePools.get("p1").cardTitles,s.titlePools.get("p1").cardGeneratedTitles);
        assertEquals(2,s.titlePools.get("p1").customTitles.size());
        verify(engine).post(anyString(),argThat(body->{var b=(Map<?,?>)body;return "户外双肩包".equals(b.get("original"))&&List.of("户外双肩包").equals(b.get("existing_titles"));}));
    }
    @Test void addingRejectsOverlongCardAndFullPoolBeforeChangingAnyExistingTitle(){
        when(engine.post(anyString(),any())).thenAnswer(call->json.valueToTree(Map.of("request_id",s.titleGeneration.requestId,"status","SUCCEEDED","product_id","p1","kind","CARD","original",s.titleGeneration.original,"title","中".repeat(31))));
        service.add(s,"p1","CARD");assertEquals("FAILED",s.titleGeneration.status);assertTrue(s.titlePools.isEmpty());verifyNoInteractions(importer);
        var pool=ProductTitlePool.of(s,"p1");pool.customTitles=new ArrayList<>();for(int i=0;i<29;i++)pool.customTitles.add("背包收纳展示标题"+i);s.titlePools.put("p1",pool);
        clearInvocations(engine);assertThrows(BizException.class,()->service.add(s,"p1","CUSTOM"));verifyNoInteractions(engine);
    }
    @Test void ambiguousAdditionRecoversOneRequestAndAppendsOnlyOnce(){
        when(engine.post(anyString(),any())).thenThrow(new BizException(8003,"timeout"));
        service.add(s,"p1","CUSTOM");assertTrue(s.titleGenerationPending());assertTrue(s.titlePools.isEmpty());
        when(engine.find(anyString())).thenAnswer(call->json.valueToTree(Map.of("request_id",s.titleGeneration.requestId,"status","SUCCEEDED","product_id","p1","kind","CUSTOM","original",s.titleGeneration.original,"title","看看双肩包的户外收纳细节")));
        service.refresh(s);service.refresh(s);assertEquals(3,s.titlePools.get("p1").customTitles.size());verify(engine,times(1)).post(anyString(),any());
    }
}
