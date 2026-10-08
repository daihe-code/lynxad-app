package com.lynx.ad.infrastructure;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingStorageAdapterTest {
    @Test void newAssetStorageCanBeOssWhileExistingLocalMediaStillWorks() {
        var local=mock(StoragePort.class); var oss=mock(StoragePort.class);
        var router=new RoutingStorageAdapter("local",local,oss); var tenant=new TenantId("t");
        when(local.getUrl(tenant,"old")).thenReturn("/local/old"); when(oss.getUrl(tenant,"new")).thenReturn("https://oss.example/new");
        assertEquals("/local/old",router.getUrl(tenant,"old"));
        assertEquals("https://oss.example/new",RoutedStoragePort.select(router,"oss").getUrl(tenant,"new"));
        assertSame(local,RoutedStoragePort.select(router,"local"));
    }
    @Test void proxyUploadDoesNotRequireBrowserCorsAndMissingOssFailsBeforeTransfer() {
        var properties=new LynxAdProperties(); properties.getStorage().getOss().setBucket("bucket");
        var client=mock(ObjectStoreClient.class); var adapter=new OssStorageAdapter(properties,client);
        assertNull(adapter.beginUpload(new TenantId("t"),"lynx-ad/t/creative/file.mp4",10,"video/mp4"));
        verifyNoInteractions(client);
        assertThrows(BizException.class,()->new UnconfiguredOssStorageAdapter().beginUpload(new TenantId("t"),"key",10,"video/mp4"));
    }
}
