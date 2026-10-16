package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.creative.application.AssetPreviewService;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.adapter.LiveMaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetPreviewTest {
    final TenantId tenant = new TenantId("owner");
    final AccountId accountId = new AccountId("bound-account");
    final AssetUploadRepository assets = mock(AssetUploadRepository.class);
    final OauthAccountRepository accounts = mock(OauthAccountRepository.class);
    final QianchuanCredentialFacade credentials = mock(QianchuanCredentialFacade.class);
    final MaterialLibraryPort library = mock(MaterialLibraryPort.class);
    final AssetPreviewService service = new AssetPreviewService(assets, accounts, credentials, library);
    AssetPreviewTest() {
        var now=Instant.now();
        when(assets.require(tenant.value(),"asset")).thenReturn(new AssetUploadRepository.Asset("asset",tenant.value(),"user","req","VIDEO",
            "generated.mp4","video/mp4",100,"key","STORED","oss",null,null,null,true,720,1280,null,null,null,now,now));
        when(assets.syncs(tenant.value(),"asset")).thenReturn(List.of(sync("UPLOADED")));
        var account=mock(OauthAccount.class);
        when(account.platform()).thenReturn(Platform.QIANCHUAN);when(account.status()).thenReturn("ACTIVE");
        when(accounts.findById(tenant,accountId)).thenReturn(Optional.of(account));
        when(credentials.getUsableCredential(tenant,accountId)).thenReturn(new Credential("private-token",new AdvertiserId("123"),accountId.value()));
    }
    @Test void exactReceiptIsUsedAndPreviewCacheStillChecksAuthorization() {
        when(library.list(any())).thenReturn(new MaterialLibraryPort.MaterialPage(List.of(
            material("unrelated","other","https://example.com/wrong.mp4"), material("video-id","material-id","https://example.com/right.mp4"))));
        assertEquals("https://example.com/right.mp4",service.preview(tenant,"asset").playUrl());
        assertEquals("QIANCHUAN",service.preview(tenant,"asset").source());
        var query=ArgumentCaptor.forClass(MaterialLibraryPort.MaterialLibraryQuery.class);
        verify(library,times(1)).list(query.capture());
        assertEquals("123",query.getValue().advertiserId());assertEquals("video-id",query.getValue().objectId());
        assertFalse(query.getValue().toString().contains("private-token"));
        when(accounts.findById(tenant,accountId)).thenReturn(Optional.empty());
        assertEquals("UNAVAILABLE",service.preview(tenant,"asset").source());
    }
    @Test void sameFilenameDoesNotMatchAndMalformedOrMissingUrlsAreUnavailable() {
        when(library.list(any())).thenReturn(new MaterialLibraryPort.MaterialPage(List.of(material("other","other","https://example.com/wrong.mp4"))));
        assertEquals("UNAVAILABLE",service.preview(tenant,"asset").source());
        var fresh=new AssetPreviewService(assets,accounts,credentials,library);
        when(library.list(any())).thenReturn(new MaterialLibraryPort.MaterialPage(List.of(material("video-id","material-id","javascript:alert(1)"))));
        assertEquals("UNAVAILABLE",fresh.preview(tenant,"asset").source());
    }
    @Test void rejectsOtherTenantBeforeReadingAnyPlatformCredentials() {
        var other=new TenantId("other");when(assets.require(other.value(),"asset")).thenThrow(new BizException(1522,"not owned"));
        assertThrows(BizException.class,()->service.preview(other,"asset"));verifyNoInteractions(accounts,credentials,library);
    }
    @Test void unsyncedAndReassignedAccountDoNotQueryPlatform() {
        when(assets.syncs(tenant.value(),"asset")).thenReturn(List.of(sync("FAILED")));
        assertEquals("UNAVAILABLE",service.preview(tenant,"asset").source());verifyNoInteractions(library);
        when(assets.syncs(tenant.value(),"asset")).thenReturn(List.of(sync("UPLOADED")));
        when(credentials.getUsableCredential(tenant,accountId)).thenReturn(new Credential("private-token",new AdvertiserId("999"),accountId.value()));
        assertEquals("UNAVAILABLE",service.preview(tenant,"asset").source());verifyNoInteractions(library);
    }
    @Test void platformFailureDoesNotExposeSensitiveResponseOrChangeUploadState() {
        when(library.list(any())).thenThrow(new BizException(1506,"provider payload private-token"));
        var preview=service.preview(tenant,"asset");assertEquals("UNAVAILABLE",preview.source());
        assertFalse(preview.message().contains("private-token"));verify(assets).require(tenant.value(),"asset");verify(assets).syncs(tenant.value(),"asset");verifyNoMoreInteractions(assets);
    }
    @Test void liveQueriesUseOneEncodedIdFilterAndMapActualVideoObjectId() throws Exception {
        for(var type:List.of(MaterialType.VIDEO,MaterialType.IMAGE)) {
            var rest=mock(QianchuanRestClient.class);
            when(rest.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"list\":[{\"id\":\"object&key\",\"material_id\":\"456\",\"url\":\"https://example.com/media\"}]}}");
            var live=new LiveMaterialLibraryPort(rest,new LynxAdProperties(),new OceanJson(new ObjectMapper()));
            var result=live.list(new MaterialLibraryPort.MaterialLibraryQuery("123",type,null,null,1,20,"private-token","object&key","456"));
            assertEquals("object&key",result.items().getFirst().objectId());assertEquals("456",result.items().getFirst().materialId());
            var request=ArgumentCaptor.forClass(RequestSpec.class);verify(rest).exchange(request.capture(),eq(String.class));
            var url=request.getValue().url();var filter=new ObjectMapper().readTree(URLDecoder.decode(url.substring(url.indexOf("&filtering=")+11),StandardCharsets.UTF_8));
            assertEquals(1,filter.size());assertEquals("object&key",filter.path(type==MaterialType.VIDEO?"video_ids":"image_ids").get(0).asText());
            assertFalse(url.contains("private-token"));
        }
    }
    @Test void materialOnlyReceiptUsesNumericIdFilter() throws Exception {
        var rest=mock(QianchuanRestClient.class);
        when(rest.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"list\":[]}}");
        var live=new LiveMaterialLibraryPort(rest,new LynxAdProperties(),new OceanJson(new ObjectMapper()));
        live.list(new MaterialLibraryPort.MaterialLibraryQuery("123",MaterialType.IMAGE,null,null,1,20,"private-token",null,"1878743575303385"));
        var request=ArgumentCaptor.forClass(RequestSpec.class);verify(rest).exchange(request.capture(),eq(String.class));
        var url=request.getValue().url();var filter=new ObjectMapper().readTree(URLDecoder.decode(url.substring(url.indexOf("&filtering=")+11),StandardCharsets.UTF_8));
        assertTrue(filter.path("material_ids").get(0).isIntegralNumber());assertEquals(1878743575303385L,filter.path("material_ids").get(0).asLong());
    }
    @Test void liveCatalogUsesProviderPageInfoRatherThanAssumingFullPage() {
        var rest=mock(QianchuanRestClient.class);
        when(rest.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"list\":[{\"image_id\":\"first\"}],\"page_info\":{\"total_page\":2}}}");
        var live=new LiveMaterialLibraryPort(rest,new LynxAdProperties(),new OceanJson(new ObjectMapper()));
        assertTrue(live.list(new MaterialLibraryPort.MaterialLibraryQuery("123",MaterialType.IMAGE,null,null,1,20,"token")).hasMore());
    }
    private AssetUploadRepository.Sync sync(String status) {
        return new AssetUploadRepository.Sync("sync",tenant.value(),"asset",accountId.value(),"123",status,"video-id","material-id",null,"PASS",1,null,null,null);
    }
    private MaterialLibraryPort.PlatformMaterial material(String object,String id,String url) {
        return new MaterialLibraryPort.PlatformMaterial(object,id,"generated.mp4",100,720,1280,20,"VIDEO_VERTICAL","EXTERNAL",Instant.now(),url,"",true,"NONE");
    }
}
