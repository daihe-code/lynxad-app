package com.lynx.ad.application.creative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetUploadBoundaryTest {
    @Test void carouselComponentCannotStartIndependentPlatformSyncEvenWithLowercaseLibrary() {
        var repository=mock(AssetUploadRepository.class); var storage=mock(StoragePort.class); var accounts=mock(OauthAccountRepository.class);
        var service=new AssetUploadService(repository,storage,accounts,new LynxAdProperties(),new ObjectMapper(),mock(com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector.class));
        for(String library:List.of("CAROUSEL","carousel")) assertThrows(BizException.class,()->service.init(new TenantId("tenant-a"),
                new AssetUploadService.InitRequest("request-id-123456","image.jpg",100,false,List.of("account"),library)));
        verifyNoInteractions(repository,storage,accounts);
    }
    @Test void crossLibraryFilesAreRejectedBeforeDatabaseOrStorageAccess() {
        var repository=mock(AssetUploadRepository.class); var storage=mock(StoragePort.class);
        var accounts=mock(OauthAccountRepository.class);
        var service=new AssetUploadService(repository,storage,accounts,new LynxAdProperties(),new ObjectMapper(),mock(com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector.class));
        var files=Map.of("VIDEO","file.mp4","IMAGE","file.jpg","AUDIO","file.mp3","TEXT","file.txt");
        for (String library:List.of("VIDEO","IMAGE","AUDIO")) for (var file:files.entrySet()) {
            if (!library.equals(file.getKey())) assertThrows(BizException.class, () -> service.init(new TenantId("tenant-a"),
                    new AssetUploadService.InitRequest("request-id-123456",file.getValue(),100,false,List.of(),library)));
        }
        assertThrows(BizException.class, () -> service.init(new TenantId("tenant-a"),
                new AssetUploadService.InitRequest("request-id-123456","file.mp4",100,false,List.of(),"UNKNOWN")));
        verifyNoInteractions(repository,storage,accounts);
    }
    @Test void incompatibleQianchuanAudioIsRejectedBeforeStorageSession() {
        var repository=mock(AssetUploadRepository.class); var storage=mock(StoragePort.class);
        var service=new AssetUploadService(repository,storage,mock(OauthAccountRepository.class),new LynxAdProperties(),new ObjectMapper(),mock(com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector.class));
        assertThrows(BizException.class,()->service.init(new TenantId("tenant-a"),new AssetUploadService.InitRequest("request-id-123456","audio.wav",100,false,List.of("account"))));
        verifyNoInteractions(repository,storage);
    }
    @Test void rejectsOtherTenantsAccountBeforeCreatingUploadOrSigningOssUrl() {
        var repository=mock(AssetUploadRepository.class); var storage=mock(StoragePort.class); var accounts=mock(OauthAccountRepository.class);
        var tenant=new TenantId("tenant-a"); when(accounts.findById(tenant,new AccountId("tenant-b-account"))).thenReturn(Optional.empty());
        var service=new AssetUploadService(repository,storage,accounts,new LynxAdProperties(),new ObjectMapper(),mock(com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector.class));
        assertThrows(BizException.class,()->service.init(tenant,new AssetUploadService.InitRequest("request-id-123456","file.mp4",100,false,List.of("tenant-b-account"))));
        verifyNoInteractions(repository,storage);
    }
    @Test void tenantIsRequiredForDetailsAndRetries() {
        var repository=mock(AssetUploadRepository.class); var storage=mock(StoragePort.class);
        var service=new AssetUploadService(repository,storage,mock(OauthAccountRepository.class),new LynxAdProperties(),new ObjectMapper(),mock(com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector.class));
        var tenant=new TenantId("tenant-b"); when(repository.require("tenant-b","asset-a")).thenThrow(new BizException(1522,"not found"));
        assertThrows(BizException.class,()->service.detail(tenant,"asset-a"));
        assertThrows(BizException.class,()->service.retry(tenant,"asset-a",null,false));
        assertThrows(BizException.class,()->service.retryReview(tenant,"asset-a"));
        verifyNoInteractions(storage);
    }
    @Test void manualSyncRetryCannotBypassAiReview() {
        var repository=mock(AssetUploadRepository.class); var storage=mock(StoragePort.class);
        var accounts=mock(OauthAccountRepository.class);
        var service=new AssetUploadService(repository,storage,accounts,new LynxAdProperties(),new ObjectMapper(),mock(com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector.class));
        var now=java.time.Instant.now();
        var asset=new AssetUploadRepository.Asset("asset-a","tenant-a","user","request","IMAGE","file.jpg","image/jpeg",10,"key","STORED","local",null,null,"md5",false,1,1,null,null,null,now,now);
        when(repository.require("tenant-a","asset-a")).thenReturn(asset);
        assertThrows(BizException.class,()->service.retry(new TenantId("tenant-a"),"asset-a","account",true));
        verify(repository,never()).retry(anyString(),anyString(),anyString(),anyBoolean());
        verifyNoInteractions(storage,accounts);
    }
}
