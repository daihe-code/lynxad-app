package com.lynx.ad.application.creative;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CarouselServiceTest {
    final CarouselRepository groups=mock(CarouselRepository.class);
    final AssetUploadRepository assets=mock(AssetUploadRepository.class);
    final AssetUploadService uploads=mock(AssetUploadService.class);
    final CarouselService service=new CarouselService(groups,assets,uploads,mock(OauthAccountRepository.class));
    final CarouselRepository.Bundle bundle=new CarouselRepository.Bundle("group","tenant-a","user","request-id-123456","name",List.of("account"),"PENDING",Instant.now());
    AssetUploadRepository.Asset asset(String id,String kind) {
        return new AssetUploadRepository.Asset(id,"tenant-a","user","request",kind,"file."+(kind.equals("TEXT")?"txt":kind.equals("AUDIO")?"mp3":"jpg"),"mime",100,"key","STORED","oss",null,null,"md5",false,1,1,"真实文本",null,null,Instant.now(),Instant.now());
    }
    void parts(String text,String image,String audio) {
        when(groups.parts(bundle)).thenReturn(List.of(new CarouselRepository.Part("TEXT",0,"text"),new CarouselRepository.Part("IMAGE",0,"image"),new CarouselRepository.Part("AUDIO",0,"audio")));
        for(var entry:Map.of("text",text,"image",image,"audio",audio).entrySet()) {
            String id=entry.getKey(),kind=id.equals("text")?"TEXT":id.equals("audio")?"AUDIO":"IMAGE";
            var a=asset(id,kind); when(assets.require("tenant-a",id)).thenReturn(a);
            var review=new AssetUploadRepository.Review(id,"tenant-a",entry.getValue(),"job",entry.getValue(),"result",List.of(),null,0,Instant.now());
            when(uploads.detail(new TenantId("tenant-a"),id)).thenReturn(new AssetUploadService.Detail(id,kind,a.filename(),100,"STORED","oss",false,"md5",1,1,"真实文本","url",null,Instant.now().toString(),List.of(),review));
        }
    }
    @Test void oneRejectionCreatesNoPlatformTasksForAnyComponent() {
        for(String role:List.of("TEXT","IMAGE","AUDIO")) {
            parts(role.equals("TEXT")?"REJECTED":"APPROVED",role.equals("IMAGE")?"REJECTED":"APPROVED",role.equals("AUDIO")?"REJECTED":"APPROVED");
            service.refresh(bundle);
        }
        verify(assets,never()).addSync(any(),anyString()); verify(assets,never()).activate(anyString(),anyString());
        verify(groups,times(3)).reviewed(bundle,"REJECTED");
    }
    @Test void pendingOrFailedAudioBlocksAlreadyApprovedTextAndImages() {
        parts("APPROVED","APPROVED","PENDING"); service.refresh(bundle);
        parts("APPROVED","APPROVED","FAILED"); service.refresh(bundle);
        verify(assets,never()).addSync(any(),anyString()); verify(groups).reviewed(bundle,"PENDING"); verify(groups).reviewed(bundle,"FAILED");
    }
    @Test void allPassedEnablesOnlyImageAndAudioTasks() {
        parts("APPROVED","APPROVED","APPROVED"); service.refresh(bundle);
        verify(assets).addSync(assets.require("tenant-a","image"),"account");
        verify(assets).addSync(assets.require("tenant-a","audio"),"account");
        verify(assets,never()).addSync(assets.require("tenant-a","text"),"account");
        verify(groups).reviewed(bundle,"APPROVED");
    }
    @Test void allEngineComponentsSyncWithoutPretendingAnUploadReviewPassed() {
        parts("NOT_REQUIRED","NOT_REQUIRED","NOT_REQUIRED");service.refresh(bundle);
        verify(assets).addSync(assets.require("tenant-a","image"),"account");
        verify(assets).addSync(assets.require("tenant-a","audio"),"account");
        verify(groups).reviewed(bundle,"NOT_REQUIRED");
    }
    @Test void malformedCompositeAndMissingAudioCannotBeSaved() {
        var tenant=new TenantId("tenant-a");
        assertThrows(BizException.class,()->service.create(tenant,new CarouselService.Create("request-id-123456","name","text",List.of(),"audio",List.of())));
        assertThrows(BizException.class,()->service.create(tenant,new CarouselService.Create("request-id-123456","name","text",List.of("image","image"),"audio",List.of())));
        when(assets.require("tenant-a","text")).thenReturn(asset("text","TEXT")); when(assets.require("tenant-a","image")).thenReturn(asset("image","IMAGE"));
        assertThrows(BizException.class,()->service.create(tenant,new CarouselService.Create("request-id-123456","name","text",List.of("image"),null,List.of())));
        verify(groups,never()).insert(any(),any());
    }
    @Test void otherTenantsComponentsAndDetailsAreInaccessible() {
        when(assets.require("tenant-b","text")).thenThrow(new BizException(1522,"not found"));
        when(groups.require("tenant-b","group")).thenThrow(new BizException(1522,"not found"));
        assertThrows(BizException.class,()->service.create(new TenantId("tenant-b"),new CarouselService.Create("request-id-123456","name","text",List.of("image"),"audio",List.of())));
        assertThrows(BizException.class,()->service.detail(new TenantId("tenant-b"),"group"));
        verify(groups,never()).insert(any(),any()); verifyNoInteractions(uploads);
    }
}
