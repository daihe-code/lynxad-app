package com.lynx.ad.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.service.AssetStoragePolicy;
import com.lynx.ad.modules.creative.infrastructure.adapter.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.*;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssetStorageV2Test {
    @Test void routesWritesAndReadsToTheSameMediaBucketAndRejectsOtherTenants() {
        var props=new LynxAdProperties();props.getStorage().getOss().setBucket("old-video-bucket");
        var client=mock(ObjectStoreClient.class);var storage=new OssStorageAdapter(props,client);
        var tenant=new TenantId("tenant");
        for(String extension:new String[]{"jpg","mp3","mp4"}) {
            String key=AssetStoragePolicy.key(tenant,"asset-"+extension,"file."+extension);
            String bucket=extension.equals("jpg")?"lynxad-douyin-image":extension.equals("mp3")?"lynxad-douyin-voice":"lynxad-douyin-video";
            storage.put(tenant,key,new ByteArrayInputStream(new byte[]{1}),1,"application/octet-stream");
            storage.getUrl(tenant,key);storage.open(tenant,key);
            verify(client).putObject(eq(bucket),eq(key),any(),eq(1L),anyString());
            verify(client).openObject(bucket,key);verify(client).presignedUrl(eq(bucket),eq(key),any());
            assertThrows(BizException.class,()->storage.getUrl(new TenantId("other"),key));
        }
    }
    @Test void persistedLegacyBucketWinsOverNewDefaults() {
        var props=new LynxAdProperties();props.getStorage().getOss().setBucket("changed-default");
        var repo=mock(AssetUploadRepository.class);var client=mock(ObjectStoreClient.class);
        String key="lynx-ad/tenant/creative/20261007/old.mp4";var now=Instant.now();
        var asset=new Asset("asset","tenant","user","request","VIDEO","file.mp4","video/mp4",1,key,"STORED","oss",null,null,null,false,null,null,null,null,null,now,now);
        when(repo.storageObject("tenant",key)).thenReturn(Optional.of(asset));
        when(repo.storageMetadata("tenant","asset")).thenReturn(new StorageMetadata("original-bucket",null,"LEGACY","LOCAL_UPLOAD",null,null,null));
        new OssStorageAdapter(props,client,repo).getUrl(new TenantId("tenant"),key);
        verify(client).presignedUrl(eq("original-bucket"),eq(key),any());
    }
}
