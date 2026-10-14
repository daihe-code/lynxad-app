package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.MaterialCreativeContextStore.Entry;
import com.lynx.ad.modules.delivery.application.CreationPayloadBuilder;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class MaterialCreativeContextTest {
    final ObjectMapper json=new ObjectMapper();final MaterialCreativeContextStore store=mock(MaterialCreativeContextStore.class);
    final CreationEngineClient engine=mock(CreationEngineClient.class);final AssetUploadService assets=mock(AssetUploadService.class);
    final CarouselPlatformService carousels=mock(CarouselPlatformService.class);final CreationPayloadBuilder payloads=mock(CreationPayloadBuilder.class);
    final CreationState s=new CreationState();final CreationState.Material m=new CreationState.Material();MaterialCreativeContextService service;
    @BeforeEach void setup(){
        s.tenantId="tenant";s.planId="plan";s.accountId="account";s.confirmedVersion=s.packageVersion;
        m.id="m1";m.productId="123";m.type="VIDEO";m.videoAssetId="video";m.imageAssetIds=List.of("image");m.carouselId="carousel";m.status="READY";s.materials.add(m);
        when(store.get(anyString(),anyString())).thenReturn(Optional.empty());when(store.reusable(anyString(),anyString(),anyString())).thenReturn(Optional.empty());
        when(payloads.uploaded(any(),anyString(),anyString())).thenReturn(new AssetUploadRepository.Sync("id","tenant","video","account","123","UPLOADED","platform-video","1","request","PASS",0,null,null,null));
        when(carousels.ensure(any(),anyString(),anyString())).thenReturn("platform-carousel");
        when(assets.detail(any(),anyString())).thenReturn(new AssetUploadService.Detail("asset","VIDEO","file",100,"STORED","engine_oss",true,"md5",null,null,null,"https://assets.test/actual.mp4",null,"",List.of(),null));
        service=new MaterialCreativeContextService(store,json,engine,assets,carousels,payloads,mock(BillingTrialService.class));
    }
    Entry entry(JsonNode profile){var body=json.createObjectNode().put("format",m.type).put("origin","AI").put("engine_material_id","engine-m1").put("user_description","通勤演示");body.putArray("asset_ids").add("video");body.putArray("platform_keys").add("VIDEO:platform-video");return new Entry("context","tenant","plan","account","123","m1","asset","PENDING",body,profile,false,null,1,0);}
    ObjectNode profile(String source){return json.createObjectNode().put("product_id","123").put("format","VIDEO").put("source",source).put("confidence",.9).set("scenes",json.valueToTree(List.of("骑行通勤")));}
    @ParameterizedTest @ValueSource(strings={"VIDEO","IMAGE","CAROUSEL"}) void libraryAndGeneratedFormatsRegisterDurableAssetVersions(String type){
        m.type=type;service.record(s,m);var capture=ArgumentCaptor.forClass(Entry.class);verify(store).insert(capture.capture());var first=capture.getValue();
        assertEquals("LIBRARY",first.body().path("origin").asText());assertEquals("PENDING",first.status());assertEquals("123",first.product());assertFalse(first.body().path("platform_keys").isEmpty());
        m.engineMaterialId="engine-m1";m.generationDescription="突出通勤";service.record(s,m);verify(store,times(2)).insert(capture.capture());
        assertEquals("AI",capture.getValue().body().path("origin").asText());assertEquals("突出通勤",capture.getValue().body().path("user_description").asText());assertEquals(s.packageVersion,s.confirmedVersion);
    }
    @Test void generationPlanIsKeptWhileActualMediaIsAnalyzed(){
        var planned=profile("GENERATION_PLAN");when(engine.find(contains("/materials/"))).thenReturn(planned);
        var observed=profile("VISUAL_ANALYSIS");when(engine.post(anyString(),any())).thenReturn(json.createObjectNode().put("request_id","context-1").put("status","READY").set("profile",observed));
        service.process(entry(null));var capture=ArgumentCaptor.forClass(JsonNode.class);verify(store).finish(any(),eq("READY"),capture.capture());
        assertEquals("VISUAL_ANALYSIS",capture.getValue().path("source").asText());assertEquals(planned,capture.getValue().path("generation_plan"));
        var body=ArgumentCaptor.forClass(Object.class);verify(engine).post(anyString(),body.capture());assertEquals(List.of("https://assets.test/actual.mp4"),((Map<?,?>)body.getValue()).get("media_urls"));
    }
    @Test void ambiguousPostQueriesSameReceiptInsteadOfCallingModelAgain(){
        when(engine.find(contains("/materials/"))).thenReturn(profile("GENERATION_PLAN"));when(engine.post(anyString(),any())).thenThrow(new IllegalStateException("timeout"));
        assertThrows(IllegalStateException.class,()->service.process(entry(null)));
        when(engine.find(contains("material-contexts/context-1"))).thenReturn(json.createObjectNode().put("request_id","context-1").put("status","READY").set("profile",profile("VISUAL_ANALYSIS")));
        service.process(entry(null));verify(engine,times(1)).post(anyString(),any());verify(store).finish(any(),eq("READY"),any());
    }
    @Test void exactProductAndPlatformIdentityAreRequiredForJoiningEffects(){
        var known=entry(profile("VISUAL_ANALYSIS"));when(store.history("tenant","account")).thenReturn(List.of(known));
        var entities=json.createArrayNode();for(String product:List.of("123","456")){var row=entities.addObject().put("kind","MATERIAL").put("product_id",product).put("object_key","VIDEO:platform-video");row.putObject("creative_features");}
        var history=json.createObjectNode();service.attach("tenant","account",entities,history);
        assertEquals("context",entities.get(0).path("creative_features").path("context_id").asText());assertFalse(entities.get(1).path("creative_features").has("creative_profile"));assertEquals(1,history.path("scene_history").size());
    }
    @Test void unreadableMediaDoesNotInventSceneOrChangeReadiness(){
        when(engine.find(contains("/materials/"))).thenReturn(profile("GENERATION_PLAN"));when(engine.post(anyString(),any())).thenReturn(json.createObjectNode().put("request_id","context-1").put("status","FAILED"));
        service.process(entry(null));verify(store).finish(any(),eq("FAILED"),any());assertEquals("READY",m.status);assertEquals(s.packageVersion,s.confirmedVersion);
    }
    @AfterEach void stop(){service.stop();}
}
