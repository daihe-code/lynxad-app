package com.lynx.ad.application.creative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AssetVideoCoverTest {
    AssetUploadRepository.Asset asset(String id,String kind) {return new AssetUploadRepository.Asset(id,"tenant","user",id,kind,id,"video/mp4",100,id,"STORED","local",null,null,null,true,320,240,null,null,null,Instant.now(),Instant.now());}
    @Test void existingCoverIsTenantCheckedAndDoesNotDecodeOrUploadAgain(){
        var repo=mock(AssetUploadRepository.class);var inspector=mock(MediaFileInspector.class);var storage=mock(StoragePort.class);
        var service=new AssetUploadService(repo,storage,mock(OauthAccountRepository.class),new LynxAdProperties(),new ObjectMapper(),inspector);
        when(repo.require("tenant","video")).thenReturn(asset("video","VIDEO"));when(repo.require("tenant","cover")).thenReturn(asset("cover","IMAGE"));
        when(repo.storageMetadata("tenant","video")).thenReturn(new AssetUploadRepository.StorageMetadata(null,null,"V2","LOCAL_UPLOAD",1000L,null,"cover"));
        assertEquals("cover",service.videoCover(new TenantId("tenant"),new UserId("user"),"video"));verifyNoInteractions(storage,inspector);verify(repo).require("tenant","cover");
    }
    @Test void derivedCoverUsesNormalIngestAndPreservesTheVideosAiDeclaration() throws Exception {
        var repo=mock(AssetUploadRepository.class);var inspector=mock(MediaFileInspector.class);
        var service=spy(new AssetUploadService(repo,mock(StoragePort.class),mock(OauthAccountRepository.class),new LynxAdProperties(),new ObjectMapper(),inspector));
        var video=asset("video","VIDEO");var tenant=new TenantId("tenant");var user=new UserId("user");
        when(repo.require("tenant","video")).thenReturn(video);when(repo.request("tenant","video-cover:video")).thenReturn(Optional.empty());
        Path source=Files.createTempFile("cover-test-",".mp4");doReturn(source).when(service).download(tenant,video);
        var cover=new AssetUploadService.Detail("derived","IMAGE","cover.jpg",100,"STORED","oss",true,null,320,240,null,"/cover",null,Instant.now().toString(),List.of(),null);
        doReturn(cover).when(service).ingest(eq(tenant),eq(user),eq("video-cover:video"),any(Path.class),eq("视频首帧封面.jpg"),eq(true),isNull(),eq(false));
        assertEquals("derived",service.videoCover(tenant,user,"video"));verify(inspector).cover(eq(source),any(Path.class));verify(repo).preview("tenant","video","derived");assertFalse(Files.exists(source));
    }
}
