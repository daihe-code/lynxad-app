package com.lynx.ad.application.creative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.Instant;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CarouselPlatformServiceTest {
    @Test void optionalDescriptionAndThirtyFiveImagesReachThePlatformInOrderWithoutReplayingConfirmedCreation() {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:carousel-optional-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE creative_carousel_platform(tenant_id VARCHAR,carousel_id VARCHAR,account_id VARCHAR,status VARCHAR,qc_carousel_id VARCHAR,error_message VARCHAR,updated_at TIMESTAMP,PRIMARY KEY(tenant_id,carousel_id,account_id))");
        var bundles=mock(CarouselService.class);var gateway=mock(CreationQianchuanGateway.class);var json=new ObjectMapper();
        var service=new CarouselPlatformService(bundles,gateway,json,jdbc);var tenant=new TenantId("tenant");
        var parts=new ArrayList<CarouselService.Part>();
        for(int i=0;i<35;i++)parts.add(new CarouselService.Part("IMAGE",i,asset("image-"+i,"IMAGE")));
        parts.add(new CarouselService.Part("AUDIO",0,asset("audio","AUDIO")));
        var review=new AssetUploadRepository.Review("bundle","tenant","APPROVED",null,"APPROVED","ok",List.of(),null,0,Instant.now());
        when(bundles.detail(tenant,"bundle")).thenReturn(new CarouselService.Detail("bundle","图文名称",Instant.now().toString(),parts,review,List.of("account")));
        var credential=new Credential("test",new AdvertiserId("123"));when(gateway.credential(tenant,"account",null)).thenReturn(credential);
        when(gateway.write(eq(credential),anyString(),any())).thenReturn(json.createObjectNode().put("carousel_id","9001"));
        assertEquals("9001",service.ensure(tenant,"bundle","account"));
        assertEquals("9001",service.ensure(tenant,"bundle","account"));
        var payload=ArgumentCaptor.forClass(ObjectNode.class);verify(gateway,times(1)).write(eq(credential),eq("https://api.oceanengine.com/open_api/2/carousel/create/"),payload.capture());
        var body=payload.getValue();assertFalse(body.has("description"));assertEquals("图文名称",body.path("file_name").asText());assertEquals(35,body.path("images").size());
        for(int i=0;i<35;i++)assertEquals("platform-image-"+i,body.path("images").get(i).path("image_id").asText());
        assertEquals("platform-audio",body.path("audio_id").asText());assertEquals("SUCCEEDED",service.receipt(tenant,"bundle","account").status());
    }
    private static AssetUploadService.Detail asset(String id,String kind) {
        var sync=new AssetUploadRepository.Sync("sync-"+id,"tenant",id,"account","123","UPLOADED","platform-"+id,null,null,"PASS",1,null,null,null);
        return new AssetUploadService.Detail(id,kind,id,100,"STORED","oss",false,"md5",720,1280,null,"url",null,Instant.now().toString(),List.of(sync),null);
    }
}
