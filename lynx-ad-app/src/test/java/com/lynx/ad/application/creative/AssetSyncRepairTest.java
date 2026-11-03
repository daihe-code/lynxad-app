package com.lynx.ad.application.creative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.domain.port.StorageObject;
import com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Asset;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetSyncRepairTest {
    final TenantId tenant=new TenantId("tenant");final AssetUploadRepository repo=mock(AssetUploadRepository.class);
    final Map<String,Asset> rows=new HashMap<>();final Map<String,byte[]> objects=new HashMap<>();Asset source;AssetUploadService files;
    @BeforeEach void setup() throws Exception {
        var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(800,800,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",out);
        var bytes=out.toByteArray();var now=Instant.now();String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(bytes));
        source=new Asset("original","tenant","owner","original-request","IMAGE","product.png","image/png",bytes.length,"original-key","STORED","local",null,null,hash,true,800,800,null,null,null,now,now);rows.put(source.id(),source);objects.put(source.objectKey(),bytes);
        var storage=new StoragePort(){public StorageObject put(TenantId t,String key,InputStream in,String type){try{objects.put(key,in.readAllBytes());return null;}catch(IOException ex){throw new UncheckedIOException(ex);}}public byte[] read(TenantId t,String key){return objects.get(key);}public String getUrl(TenantId t,String key){return "";}public void delete(TenantId t,String key){objects.remove(key);}};
        when(repo.request(anyString(),anyString())).thenAnswer(call->rows.values().stream().filter(a->a.requestId().equals(call.getArgument(1))).findFirst());
        when(repo.require(anyString(),anyString())).thenAnswer(call->rows.get(call.getArgument(1)));
        doAnswer(call->{Asset a=call.getArgument(0);rows.put(a.id(),a);return null;}).when(repo).insert(any());
        when(repo.approved("tenant","original")).thenReturn(true);when(repo.claimUpload(anyString(),anyString())).thenReturn(true);
        doAnswer(call->{Asset a=call.getArgument(0);rows.put(a.id(),new Asset(a.id(),a.tenantId(),a.userId(),a.requestId(),a.kind(),a.filename(),a.contentType(),a.sizeBytes(),a.objectKey(),"STORED",a.storageMode(),null,null,call.getArgument(1),a.isAigc(),call.getArgument(2),call.getArgument(3),null,null,null,a.createdAt(),a.updatedAt()));return null;}).when(repo).stored(any(),anyString(),any(),any(),any());
        files=new AssetUploadService(repo,storage,mock(OauthAccountRepository.class),new LynxAdProperties(),new ObjectMapper(),new MediaFileInspector("not-needed-for-image",5));
    }
    @Test void repairIsPrivateIdempotentAndUsesOriginalApprovedContentWithoutPaidReview() throws Exception {
        assertSame(source,files.syncPayload(tenant,source,false));
        var repaired=files.syncPayload(tenant,source,true);assertNotEquals(source.id(),repaired.id());assertEquals("compatible.jpg",repaired.filename());assertTrue(repaired.isAigc());assertEquals(800,repaired.width());assertEquals(800,repaired.height());
        assertSame(repaired,files.syncPayload(tenant,source,false));assertSame(repaired,files.syncPayload(tenant,source,true));assertSame(source,rows.get("original"));assertEquals(2,rows.size());
        var order=inOrder(repo);order.verify(repo).approveTechnicalRepair("tenant","original",repaired.id());order.verify(repo).stored(any(),eq(repaired.md5()),eq(800),eq(800),isNull());
        verify(repo,times(1)).insert(any());verify(repo).carouselComponent("tenant",repaired.id());verify(repo,never()).queueReview(anyString(),anyString());
        assertSame(repaired,files.syncPayloadForSignature(tenant,source,repaired.md5()));assertNull(files.syncPayloadForSignature(tenant,source,"b".repeat(32)));
    }
    @Test void unapprovedSourceNeverConvertsAndLostConversionClaimNeverMarksOtherWorkerFailed() throws Exception {
        when(repo.approved("tenant","original")).thenReturn(false);assertThrows(BizException.class,()->files.syncPayload(tenant,source,true));verify(repo,never()).insert(any());
        when(repo.approved("tenant","original")).thenReturn(true);when(repo.claimUpload(anyString(),anyString())).thenReturn(false);
        assertThrows(BizException.class,()->files.syncPayload(tenant,source,true));verify(repo,never()).storageFailed(any(),anyString());verify(repo,never()).stored(any(),any(),any(),any(),any());
    }
    @Test void changedSourceCannotInheritTheOriginalContentApproval() {
        objects.put(source.objectKey(),new byte[]{1,2,3});assertThrows(BizException.class,()->files.syncPayload(tenant,source,true));verify(repo,never()).insert(any());verify(repo,never()).approveTechnicalRepair(any(),any(),any());
    }
}
