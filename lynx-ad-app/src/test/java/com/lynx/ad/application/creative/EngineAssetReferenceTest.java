package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.infrastructure.adapter.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class EngineAssetReferenceTest {
    final AssetUploadRepository repository=mock(AssetUploadRepository.class);
    final CreationEngineClient engine=mock(CreationEngineClient.class);
    final TenantId tenant=new TenantId("tenant");
    final EngineAssetStorage storage=new EngineAssetStorage(repository,engine);
    HttpServer server;String base;final AtomicInteger heads=new AtomicInteger(),reads=new AtomicInteger();
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/oss-image",e->{
            e.getResponseHeaders().set("Content-Type","image/png");
            if(e.getRequestMethod().equals("HEAD")){heads.incrementAndGet();e.sendResponseHeaders(403,-1);}
            else{reads.incrementAndGet();if("bytes=0-0".equals(e.getRequestHeaders().getFirst("Range"))){e.getResponseHeaders().set("Content-Range","bytes 0-0/4");e.sendResponseHeaders(206,1);e.getResponseBody().write(1);}else{e.sendResponseHeaders(200,4);e.getResponseBody().write(new byte[]{1,2,3,4});}}e.close();
        });server.start();base="http://127.0.0.1:"+server.getAddress().getPort();when(engine.base()).thenReturn(base);
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
          {"job_id":"job","groups":[{"materials":[{"material_id":"mat","status":"succeeded","material_type":"product_card",
           "urls":{"image_urls":["%s/oss-image?signature=new"]},"content":{"title":"生成标题","text":"生成文案"},"detail":{"resolution":"1080x1080"}}]}]}
          """.formatted(base)));
        doAnswer(call->{when(repository.require("tenant",((Asset)call.getArgument(0)).id())).thenReturn(call.getArgument(0));return null;}).when(repository).registerEngine(any(),any(),any());
    }
    @AfterEach void stop(){storage.close();server.stop(0);}
    @Test void registrationOnlyReadsMetadataAndCreatesNoFileOrReviewTask() {
        var a=storage.register(tenant,new UserId("user"),"request","job","mat","IMAGE_0","IMAGE",false);
        assertEquals("engine_oss",a.storageMode());assertEquals(1080,a.width());assertEquals(4,a.sizeBytes());assertTrue(a.filename().endsWith(".png"));
        assertTrue(a.filename().startsWith("生成标题_图片1_"));assertTrue(a.filename().contains(a.id()));
        assertEquals(0,heads.get());assertEquals(1,reads.get());
        verify(repository).registerEngine(eq(a),argThat(r->r.materialId().equals("mat")&&r.component().equals("IMAGE_0")),any());
        verify(repository,never()).queueReview(anyString(),anyString());
        assertThrows(BizException.class,()->storage.put(tenant,a.id(),new java.io.ByteArrayInputStream(new byte[0]),"image/png"));
        assertThrows(BizException.class,()->storage.delete(tenant,a.id()));
    }
    @Test void updatedEngineObjectCreatesANewReferenceAndReusesThatVersionOnLaterReads() {
        var now=java.time.Instant.now();
        var old=new Asset("old-asset","tenant","user","request","IMAGE","generated.png","image/png",4,
                "old-object","STORED","engine_oss",null,null,null,true,1080,1080,null,null,null,now,now);
        when(repository.request("tenant","request")).thenReturn(java.util.Optional.of(old));
        when(repository.engineReference("tenant","old-asset")).thenReturn(new EngineReference("old-asset","tenant","job","mat","IMAGE_0",base+"/old"));
        doAnswer(call->{
            Asset asset=call.getArgument(0);
            when(repository.require("tenant",asset.id())).thenReturn(asset);
            when(repository.request("tenant",asset.requestId())).thenReturn(java.util.Optional.of(asset));
            return null;
        }).when(repository).registerEngine(any(),any(),any());
        var latest=storage.register(tenant,new UserId("user"),"request","job","mat","IMAGE_0","IMAGE",false);
        assertNotEquals(old.id(),latest.id());assertNotEquals(old.requestId(),latest.requestId());
        assertEquals(latest,storage.register(tenant,new UserId("user"),"request","job","mat","IMAGE_0","IMAGE",false));
        verify(repository,times(1)).registerEngine(any(),any(),any());
        assertEquals(1,reads.get());
        assertEquals("old-object",old.objectKey());
        verify(repository,never()).queueReview(anyString(),anyString());
    }
    @Test void v2RegistrationPersistsActualKeyAndReadsOssWithoutDependingOnEnginePolling() throws Exception {
        String id="6013b26f-72b1-4f64-9119-d2d60f4d91f3";
        String key="lynx-ad/v2/tenant/creative/20261009/"+id+"/source.png";
        server.createContext("/"+key,e->{e.getResponseHeaders().set("Content-Range","bytes 0-0/4");e.getResponseHeaders().set("Content-Type","image/png");e.sendResponseHeaders(206,1);e.getResponseBody().write(1);e.close();});
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
            {"job_id":"job","groups":[{"materials":[{"material_id":"mat","store_id":"tenant","status":"succeeded",
             "urls":{"image_urls":["%s/%s"]},"detail":{"resolution":"1080x1080"},
             "storage":{"IMAGE_0":{"bucket":"lynxad-douyin-image","object_key":"%s","asset_id":"%s","store_id":"tenant"}}}]}]}
            """.formatted(base,key,key,id)));
        var objects=mock(com.lynx.ad.modules.creative.domain.port.StoragePort.class);
        storage.useObjectStorage(objects,new com.lynx.ad.common.config.LynxAdProperties());
        var asset=storage.register(tenant,new UserId("user"),"request","job","mat","IMAGE_0","IMAGE",false);
        assertEquals(key,asset.objectKey());
        verify(repository).registerEngine(eq(asset),any(),argThat(m->"lynxad-douyin-image".equals(m.bucket())&&"V2".equals(m.pathVersion())));
        when(repository.storageObject("tenant",key)).thenReturn(java.util.Optional.of(asset));
        when(repository.engineReference("tenant",id)).thenReturn(new EngineReference(id,"tenant","job","mat","IMAGE_0",null));
        when(repository.storageMetadata("tenant",id)).thenReturn(new StorageMetadata("lynxad-douyin-image",null,"V2","AI_GENERATED",null,null,null));
        when(objects.getUrl(tenant,key)).thenReturn("fresh-oss-url");clearInvocations(engine);
        assertEquals("fresh-oss-url",storage.getUrl(tenant,key));verifyNoInteractions(engine);
    }
    @Test void textRegistrationKeepsEngineContentWithoutAnyOssWriteOrRead() {
        var a=storage.register(tenant,new UserId("user"),"text-request","job","mat","TEXT","TEXT",true);
        assertEquals("生成标题\n生成文案",a.textContent());assertEquals(0,heads.get());assertEquals(0,reads.get());
        verify(repository).carouselComponent("tenant",a.id());verify(repository,never()).queueReview(anyString(),anyString());
    }
    @Test void retryingRegistrationReusesThePersistedName() {
        var first=storage.register(tenant,new UserId("user"),"text-request","job","mat","TEXT","TEXT",true);
        when(repository.request("tenant",first.requestId())).thenReturn(java.util.Optional.of(first));
        var retry=storage.register(tenant,new UserId("user"),"text-request","job","mat","TEXT","TEXT",true);
        assertSame(first,retry);assertEquals(first.filename(),retry.filename());
        verify(repository,times(1)).registerEngine(any(),any(),any());
    }
    @Test void missingCreativeTitleFallsBackToProductName() throws Exception {
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("""
            {"job_id":"job","groups":[{"materials":[{"material_id":"mat","status":"succeeded",
            "product_name":"长袖瑜伽服","content":{"title":"","text":"商品介绍"}}]}]}
            """));
        var asset=storage.register(tenant,new UserId("user"),"text-request","job","mat","TEXT","TEXT",false);
        assertTrue(asset.filename().startsWith("长袖瑜伽服_文案_"));
        assertTrue(asset.filename().endsWith(asset.id()+".txt"));
    }
    @Test void rangeUnsupportedFallsBackToFullObjectContentLength() throws Exception {
        server.removeContext("/oss-image");
        server.createContext("/oss-image",e->{e.getResponseHeaders().set("Content-Type","image/png");e.sendResponseHeaders(200,4);e.getResponseBody().write(new byte[]{1,2,3,4});e.close();});
        assertEquals(4,storage.register(tenant,new UserId("user"),"request","job","mat","IMAGE_0","IMAGE",false).sizeBytes());
    }
    @Test void malformedRangeCannotBeRegisteredAsOneByteObject() throws Exception {
        server.removeContext("/oss-image");
        server.createContext("/oss-image",e->{e.sendResponseHeaders(206,1);e.getResponseBody().write(1);e.close();});
        assertThrows(BizException.class,()->storage.register(tenant,new UserId("user"),"request","job","mat","IMAGE_0","IMAGE",false));
        verify(repository,never()).registerEngine(any(),any(),any());
    }
    @Test void syncResolvesAFreshEngineUrlAndAnotherTenantCannotReadIt() throws Exception {
        when(repository.engineReference("tenant","asset")).thenReturn(new EngineReference("asset","tenant","job","mat","IMAGE_0",base+"/expired"));
        assertTrue(storage.getUrl(tenant,"asset").contains("signature=new"));
        try(var in=storage.open(tenant,"asset")){assertArrayEquals(new byte[]{1,2,3,4},in.readAllBytes());}
        assertEquals(1,reads.get());verify(engine,times(2)).get("/api/v1/capabilities/generation/jobs/job?store_id=tenant");
        when(repository.engineReference("other","asset")).thenThrow(new BizException(1522,"not owned"));
        assertThrows(BizException.class,()->storage.getUrl(new TenantId("other"),"asset"));
    }
    @Test void engineResponseAndComponentTypesMustMatchBeforeRegistration() throws Exception {
        assertThrows(BizException.class,()->storage.register(tenant,new UserId("user"),"x","job","mat","IMAGE_0","VIDEO",false));
        when(engine.get(any())).thenReturn(new ObjectMapper().readTree("{\"job_id\":\"another\",\"groups\":[]}"));
        assertThrows(BizException.class,()->storage.register(tenant,new UserId("user"),"y","new-job","mat","TEXT","TEXT",false));
        verify(repository,never()).registerEngine(any(),any(),any());
    }
}
