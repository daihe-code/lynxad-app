package com.lynx.ad.application.creative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetOssRecoveryTest {
    final AssetUploadRepository repository=mock(AssetUploadRepository.class);
    final RoutedStoragePort router=mock(RoutedStoragePort.class);
    final StoragePort local=mock(StoragePort.class),oss=mock(StoragePort.class);
    final MediaFileInspector inspector=mock(MediaFileInspector.class);
    final TenantId tenant=new TenantId("tenant");
    byte[] bytes; AssetUploadService service;
    AssetUploadRepository.Asset asset(String mode) throws Exception {
        var now=Instant.now(); String md5=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(bytes));
        return new AssetUploadRepository.Asset("asset","tenant","user","request","IMAGE","image.png","image/png",bytes.length,"key","STORED",mode,null,null,md5,false,32,32,null,null,null,now,now);
    }
    @BeforeEach void setup() throws Exception {
        var out=new ByteArrayOutputStream(); javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out); bytes=out.toByteArray();
        when(router.forMode("local")).thenReturn(local); when(router.forMode("oss")).thenReturn(oss);
        when(local.open(tenant,"key")).thenAnswer(i->new ByteArrayInputStream(bytes));
        service=new AssetUploadService(repository,router,mock(OauthAccountRepository.class),new LynxAdProperties(),new ObjectMapper(),inspector);
    }
    @Test void retryPromotesLocalFileBeforeQueuingReviewAndPreservesOriginal() throws Exception {
        when(repository.require("tenant","asset")).thenReturn(asset("local"),asset("oss"),asset("oss"));
        when(oss.getUrl(tenant,"key")).thenReturn("https://oss.example/signed");
        var result=service.retryReview(tenant,"asset"); assertEquals("oss",result.storageMode());
        var order=inOrder(oss,repository);
        order.verify(oss).put(eq(tenant),eq("key"),any(InputStream.class),eq((long)bytes.length),eq("image/png"));
        order.verify(repository).promotedToOss("tenant","asset");
        order.verify(repository).queueReview("tenant","asset"); order.verify(repository).retryReview("tenant","asset");
        verify(local,never()).delete(any(),any()); verify(repository,never()).activate(any(),any());
    }
    @Test void failedOssTransferRetainsLocalRecordAndNeverStartsReview() throws Exception {
        when(repository.require("tenant","asset")).thenReturn(asset("local"));
        when(oss.put(any(),any(),any(),anyLong(),any())).thenThrow(new IllegalStateException("connection"));
        assertThrows(BizException.class,()->service.retryReview(tenant,"asset"));
        verify(repository,never()).promotedToOss(any(),any()); verify(repository,never()).queueReview(any(),any());
        verify(local,never()).delete(any(),any()); verify(repository,never()).storageFailed(any(),any());
    }
}
