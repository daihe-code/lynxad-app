package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.creative.application.LibraryCarouselPreviewService;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.adapter.LiveMaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LibraryCarouselPreviewTest {
    @Test void previewUsesOnlyOwnedCarouselAndItsCurrentAdvertiserCredential() {
        var catalog=mock(PlatformMaterialCatalog.class);var credentials=mock(QianchuanCredentialFacade.class);var library=mock(MaterialLibraryPort.class);
        var tenant=new TenantId("tenant");var account=new AccountId("account");
        var service=new LibraryCarouselPreviewService(catalog,credentials,library);
        var entry=new PlatformMaterialCatalog.Material("tenant","account","123",MaterialType.CAROUSEL,"m:501","501","501","图文",100,720,1280,null,"CAROUSEL","NONE",Instant.now());
        when(catalog.find("tenant","account",MaterialType.CAROUSEL,"m:501")).thenReturn(Optional.of(entry));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(new Credential("token",new AdvertiserId("123")));
        var media=new MaterialLibraryPort.CarouselMedia(List.of(new MaterialLibraryPort.CarouselMediaImage("image","https://media.example/image.jpg",720,1280)),"https://media.example/audio.mp3","商品介绍");
        when(library.carouselMedia(any())).thenReturn(media);
        assertEquals(media,service.preview(tenant,"EXT:account:CAROUSEL:m:501"));
        verify(library).carouselMedia(argThat(q->q.type()==MaterialType.CAROUSEL && q.materialId().equals("501") && q.advertiserId().equals("123") && q.accessToken().equals("token")));
        clearInvocations(library);
        assertThrows(BizException.class,()->service.preview(new TenantId("other"),"EXT:account:CAROUSEL:m:501"));
        assertThrows(BizException.class,()->service.preview(tenant,"EXT:account:IMAGE:m:501"));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(new Credential("token",new AdvertiserId("456")));
        assertThrows(BizException.class,()->service.preview(tenant,"EXT:account:CAROUSEL:m:501"));
        verifyNoInteractions(library);
    }
    @Test void providerPreviewPreservesPictureOrderDescriptionAndAudioWhileExcludingUnsafeUrls() {
        var rest=mock(QianchuanRestClient.class);var adapter=new LiveMaterialLibraryPort(rest,new LynxAdProperties(),new OceanJson(new ObjectMapper()));
        var query=new MaterialLibraryPort.MaterialLibraryQuery("123",MaterialType.CAROUSEL,null,null,1,100,"token",null,"501");
        when(rest.exchange(any(),eq(String.class))).thenReturn("""
                {"code":0,"data":{"carousels":[{"material_id":501,"description":"商品介绍\\n第二行","images":[
                  {"image_id":"second","url":"https://cdn.example/2.jpg","width":720,"height":1280},
                  {"image_id":"first","url":"https://cdn.example/1.jpg","width":1200,"height":1200}],
                  "audio":{"url":"https://cdn.example/audio.mp3"}}]}}
                """);
        var media=adapter.carouselMedia(query);
        assertEquals(List.of("second","first"),media.images().stream().map(MaterialLibraryPort.CarouselMediaImage::id).toList());
        assertEquals(720,media.images().getFirst().width());assertEquals("https://cdn.example/audio.mp3",media.audioUrl());assertEquals("商品介绍\n第二行",media.description());
        when(rest.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"carousels\":[{\"material_id\":501,\"images\":[{\"image_id\":\"image\",\"url\":\"javascript:alert(1)\"}],\"audio\":{\"url\":\"file:///private\"}}]}}");
        assertEquals("",adapter.carouselMedia(query).images().getFirst().url());assertEquals("",adapter.carouselMedia(query).audioUrl());
    }
}
