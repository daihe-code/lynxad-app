package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.creative.application.LibraryDeletionService;
import com.lynx.ad.modules.creative.application.LibraryQueryService;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.LiveMaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.*;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class LibraryDeletionTest {
    final TenantId tenant=new TenantId("owner");
    final AssetUploadRepository assets=mock(AssetUploadRepository.class);
    final CarouselRepository carousels=mock(CarouselRepository.class);
    final CreativeTaskRepository packages=mock(CreativeTaskRepository.class);
    final PlatformMaterialCatalog catalog=mock(PlatformMaterialCatalog.class);
    final LibraryDeletionRepository deletions=mock(LibraryDeletionRepository.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    final MaterialLibraryPort library=mock(MaterialLibraryPort.class);
    final LibraryDeletionService service=new LibraryDeletionService(assets,carousels,packages,catalog,deletions,credentials,library);
    final Set<String> hidden=new HashSet<>();
    LibraryDeletionTest() {
        when(deletions.hidden(tenant.value())).thenReturn(hidden);
        doAnswer(i->{hidden.addAll(i.getArgument(1));return null;}).when(deletions).hide(eq(tenant.value()),anyCollection());
        when(credentials.getUsableCredential(tenant,new AccountId("account")))
                .thenReturn(new Credential("private-token",new AdvertiserId("1869861871552519"),"account"));
        when(library.delete(any())).thenReturn(Set.of());
    }
    String remote(MaterialType type,String object,String mid) {
        String id="EXT:account:"+type+":m:"+mid;
        when(catalog.find(tenant.value(),"account",type,"m:"+mid)).thenReturn(Optional.of(
                new PlatformMaterialCatalog.Material(tenant.value(),"account","1869861871552519",type,"m:"+mid,object,mid,
                        "file",100,720,1280,18,"VERTICAL","NONE",Instant.now())));
        return id;
    }
    AssetUploadRepository.Asset asset(String id,String kind) {
        var asset=new AssetUploadRepository.Asset(id,tenant.value(),"user",id,kind,"file", "application/octet-stream",100,
                "original-file","STORED","engine_oss",null,null,"md5",true,720,1280,null,null,null,Instant.now(),Instant.now());
        when(assets.require(tenant.value(),id)).thenReturn(asset);return asset;
    }
    AssetUploadRepository.Sync receipt(String id,String object,String material,String status) {
        return new AssetUploadRepository.Sync("receipt-"+id,tenant.value(),id,"account","1869861871552519",status,object,material,
                "request","PASS",1,null,null,null);
    }

    @Test void partialDeletionOnlyHidesConfirmedFilesAndRetryDoesNotRepeatSuccessfulOnes() {
        String a=remote(MaterialType.VIDEO,"video-a","100"),b=remote(MaterialType.VIDEO,"video-b","200");
        when(library.delete(any())).thenReturn(Set.of("video-b"),Set.of());
        var result=service.delete(tenant,List.of(a,b),"account");
        assertEquals(List.of(a),result.deletedIds());assertEquals(b,result.failures().getFirst().id());
        assertTrue(hidden.contains(a));assertFalse(hidden.contains(b));
        assertTrue(hidden.contains("EXT:account:VIDEO:o:video-a"));
        var second=service.delete(tenant,List.of(a,b),"account");assertEquals(Set.of(a,b),new HashSet<>(second.deletedIds()));
        var queries=ArgumentCaptor.forClass(MaterialLibraryPort.DeleteQuery.class);verify(library,times(2)).delete(queries.capture());
        assertEquals(List.of("video-a","video-b"),queries.getAllValues().getFirst().objectIds());
        assertEquals(List.of("video-b"),queries.getAllValues().getLast().objectIds());
        service.delete(tenant,List.of(a,b),"account");verify(library,times(2)).delete(any());
    }
    @Test void nativeFileIdsAreResolvedServerSideAndVideoImageCallsStaySeparate() {
        String a=remote(MaterialType.VIDEO,"actual-video-id","1881234567890123456"),b=remote(MaterialType.IMAGE,"actual-image-id","1881234567890123457");
        service.delete(tenant,List.of(a,b),"account");
        var queries=ArgumentCaptor.forClass(MaterialLibraryPort.DeleteQuery.class);verify(library,times(2)).delete(queries.capture());
        assertEquals(Set.of(List.of("actual-video-id"),List.of("actual-image-id")),new HashSet<>(queries.getAllValues().stream().map(MaterialLibraryPort.DeleteQuery::objectIds).toList()));
    }
    @Test void tamperedOrCrossTenantIdentityFailsBeforeAnyProviderOrLocalMutation() {
        String a=remote(MaterialType.VIDEO,"video-a","100");
        assertThrows(BizException.class,()->service.delete(tenant,List.of(a,"EXT:other-account:VIDEO:m:200"),"account"));
        verifyNoInteractions(library);verify(deletions,never()).hide(anyString(),anyCollection());
        assertThrows(BizException.class,()->service.delete(new TenantId("other-tenant"),List.of(a),"account"));
        verifyNoInteractions(library);
    }
    @Test void providerFailureOrChangedAdvertiserDoesNotHideAnySelectedItem() {
        String a=remote(MaterialType.VIDEO,"video-a","100");
        when(library.delete(any())).thenThrow(new BizException(1523,"provider-failure"));
        assertEquals(1,service.delete(tenant,List.of(a),"account").failures().size());assertTrue(hidden.isEmpty());
        when(credentials.getUsableCredential(tenant,new AccountId("account"))).thenReturn(new Credential("token",new AdvertiserId("different")));
        assertEquals(1,service.delete(tenant,List.of(a),"account").failures().size());verify(library,times(1)).delete(any());
    }
    @Test void localOnlyAssetsAndCarouselsKeepTheirFilesAndComponents() {
        var original=asset("local","IMAGE");when(assets.syncs(tenant.value(),"local")).thenReturn(List.of());
        var bundle=bundle("bundle","local");
        var result=service.delete(tenant,List.of("ENGINE:local","CAROUSEL:bundle"),"account");
        assertEquals(2,result.deletedIds().size());verifyNoInteractions(library);verify(carousels).require(tenant.value(),"bundle");
        verify(carousels).parts(bundle);verify(carousels).platformReceipts(bundle);
        verify(assets,times(2)).require(tenant.value(),"local");verify(assets,times(2)).syncs(tenant.value(),"local");verifyNoMoreInteractions(assets,carousels);
        assertEquals("original-file",original.objectKey());
    }
    CarouselRepository.Bundle bundle(String id,String... images) {
        var bundle=new CarouselRepository.Bundle(id,tenant.value(),"user","request","图文",List.of("account"),"NOT_REQUIRED",Instant.now());
        when(carousels.require(tenant.value(),id)).thenReturn(bundle);
        var parts=new ArrayList<CarouselRepository.Part>();
        for(int n=0;n<images.length;n++)parts.add(new CarouselRepository.Part("IMAGE",n,images[n]));
        parts.add(new CarouselRepository.Part("AUDIO",0,"audio"));parts.add(new CarouselRepository.Part("TEXT",0,"text"));
        when(carousels.parts(bundle)).thenReturn(parts);return bundle;
    }
    @Test void carouselDeletesOnlyItsImageFileIdsAndRetainsSourcesAudioTextAndPlanBindings() {
        var original=asset("image-a","IMAGE");asset("image-b","IMAGE");var group=bundle("bundle","image-a","image-b");
        when(assets.syncs(tenant.value(),"image-a")).thenReturn(List.of(receipt("image-a","native-a","101","UPLOADED")));
        when(assets.syncs(tenant.value(),"image-b")).thenReturn(List.of(receipt("image-b","native-b","102","UPLOADED")));
        when(carousels.platformReceipts(group)).thenReturn(List.of(new CarouselRepository.PlatformReceipt("account","native-carousel")));
        when(catalog.findObject(tenant.value(),"account",MaterialType.CAROUSEL,"native-carousel")).thenReturn(Optional.of(
                new PlatformMaterialCatalog.Material(tenant.value(),"account","1869861871552519",MaterialType.CAROUSEL,"m:501","native-carousel","501","图文",100,720,1280,null,"CAROUSEL","NONE",Instant.now())));
        assertEquals(List.of("CAROUSEL:bundle"),service.delete(tenant,List.of("CAROUSEL:bundle"),"account").deletedIds());
        verify(library).delete(argThat(q->q.type()==MaterialType.IMAGE && q.objectIds().equals(List.of("native-a","native-b"))));
        verify(assets,never()).require(anyString(),eq("audio"));verify(assets,never()).require(anyString(),eq("text"));
        assertEquals("original-file",original.objectKey());assertTrue(hidden.contains("EXT:account:CAROUSEL:m:501"));
        assertTrue(hidden.contains("EXT:account:IMAGE:o:native-a"));
    }
    @Test void carouselPartialFailureRetainsCardAndRetryOnlyDeletesTheRemainingImage() {
        asset("a","IMAGE");asset("b","IMAGE");bundle("bundle","a","b");
        when(assets.syncs(tenant.value(),"a")).thenReturn(List.of(receipt("a","native-a","101","UPLOADED")));
        when(assets.syncs(tenant.value(),"b")).thenReturn(List.of(receipt("b","native-b","102","UPLOADED")));
        when(library.delete(any())).thenReturn(Set.of("native-b"),Set.of());
        var first=service.delete(tenant,List.of("CAROUSEL:bundle"),"account");
        assertTrue(first.deletedIds().isEmpty());assertEquals("CAROUSEL:bundle",first.failures().getFirst().id());assertFalse(hidden.contains("CAROUSEL:bundle"));
        assertEquals(List.of("CAROUSEL:bundle"),service.delete(tenant,List.of("CAROUSEL:bundle"),"account").deletedIds());
        var queries=ArgumentCaptor.forClass(MaterialLibraryPort.DeleteQuery.class);verify(library,times(2)).delete(queries.capture());
        assertEquals(List.of("native-b"),queries.getAllValues().getLast().objectIds());
    }
    @Test void sharedCarouselImagesAreDeduplicatedAndFailuresAffectAllReferencingSelections() {
        asset("shared","IMAGE");bundle("one","shared");bundle("two","shared");
        when(assets.syncs(tenant.value(),"shared")).thenReturn(List.of(receipt("shared","native-shared","100","UPLOADED")));
        when(library.delete(any())).thenReturn(Set.of("native-shared"));
        var result=service.delete(tenant,List.of("CAROUSEL:one","CAROUSEL:two"),"account");
        assertTrue(result.deletedIds().isEmpty());assertEquals(2,result.failures().size());
        verify(library).delete(argThat(q->q.objectIds().equals(List.of("native-shared"))));
    }
    @Test void carouselUsesCanonicalImageAndOnlyTheSelectedAccount() {
        var image=asset("original","IMAGE");
        var alias=new AssetUploadRepository.Asset("alias",tenant.value(),"user","alias","IMAGE","file","image/jpeg",100,"alias-file","STORED","oss",null,null,"md5",false,720,1280,null,image.id(),null,Instant.now(),Instant.now());
        when(assets.require(tenant.value(),"alias")).thenReturn(alias);bundle("bundle","alias");
        var other=new AssetUploadRepository.Sync("other",tenant.value(),image.id(),"other-account","123","RUNNING","other-native","100","request","PASS",1,null,null,null);
        when(assets.syncs(tenant.value(),image.id())).thenReturn(List.of(other,receipt(image.id(),"native-image","101","UPLOADED")));
        assertEquals(1,service.delete(tenant,List.of("CAROUSEL:bundle"),"account").deletedIds().size());
        verify(library).delete(argThat(q->q.accountId().equals("account") && q.objectIds().equals(List.of("native-image"))));
    }
    @Test void missingOrUnconfirmedCarouselImagesCannotBeReportedAsDeleted() {
        bundle("empty");assertThrows(BizException.class,()->service.delete(tenant,List.of("CAROUSEL:empty"),"account"));
        asset("image","IMAGE");bundle("pending","image");when(assets.syncs(tenant.value(),"image")).thenReturn(List.of(receipt("image","native","100","UNCERTAIN")));
        assertThrows(BizException.class,()->service.delete(tenant,List.of("CAROUSEL:pending"),"account"));verifyNoInteractions(library);assertTrue(hidden.isEmpty());
    }
    @Test void externalCarouselIsResolvedThroughItsOwnAccountBeforeDeletingImages() {
        String id=remote(MaterialType.CAROUSEL,"native-carousel","1881234567890123456");
        when(library.carouselImages(any())).thenReturn(List.of(new MaterialLibraryPort.CarouselImage("native-image","201")));
        assertEquals(List.of(id),service.delete(tenant,List.of(id),"account").deletedIds());
        verify(library).carouselImages(argThat(q->q.type()==MaterialType.CAROUSEL && q.advertiserId().equals("1869861871552519") && q.materialId().equals("1881234567890123456")));
        verify(library).delete(argThat(q->q.type()==MaterialType.IMAGE && q.objectIds().equals(List.of("native-image"))));
    }
    @Test void externalCarouselWithNoImagesNeverHidesTheCard() {
        String id=remote(MaterialType.CAROUSEL,"native-carousel","501");
        when(library.carouselImages(any())).thenReturn(List.of());
        assertThrows(BizException.class,()->service.delete(tenant,List.of(id),"account"));verify(library,never()).delete(any());assertTrue(hidden.isEmpty());
    }
    @Test void externalCarouselCrossTenantOrChangedAdvertiserCannotResolveOrDeleteImages() {
        String id=remote(MaterialType.CAROUSEL,"native-carousel","501");
        assertThrows(BizException.class,()->service.delete(new TenantId("other-tenant"),List.of(id),"account"));
        when(credentials.getUsableCredential(tenant,new AccountId("account"))).thenReturn(new Credential("token",new AdvertiserId("123")));
        assertThrows(BizException.class,()->service.delete(tenant,List.of(id),"account"));
        verifyNoInteractions(library);assertTrue(hidden.isEmpty());
    }
    @Test void imageComponentDeletesAreBatchedToOfficialHundredItemLimit() {
        var imageIds=new ArrayList<String>();var ids=new ArrayList<String>();
        for(int i=0;i<4;i++) {
            imageIds.clear();for(int n=0;n<35;n++){var id="image-"+i+"-"+n;imageIds.add(id);asset(id,"IMAGE");when(assets.syncs(tenant.value(),id)).thenReturn(List.of(receipt(id,"native-"+id,"100","UPLOADED")));}
            bundle("bundle-"+i,imageIds.toArray(String[]::new));ids.add("CAROUSEL:bundle-"+i);
        }
        assertEquals(4,service.delete(tenant,ids,"account").deletedIds().size());
        var queries=ArgumentCaptor.forClass(MaterialLibraryPort.DeleteQuery.class);verify(library,times(2)).delete(queries.capture());
        assertEquals(List.of(100,40),queries.getAllValues().stream().map(q->q.objectIds().size()).toList());
    }

    @Test void carouselLookupUsesOfficialMaterialFilterAndStringImageIds()throws Exception {
        var rest=mock(QianchuanRestClient.class);var json=new ObjectMapper();var adapter=new LiveMaterialLibraryPort(rest,new LynxAdProperties(),new OceanJson(json));
        var query=new MaterialLibraryPort.MaterialLibraryQuery("1869861871552519",MaterialType.CAROUSEL,null,null,1,100,"token",null,"1881234567890123456");
        when(rest.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"carousels\":[{\"material_id\":1881234567890123456,\"images\":[{\"image_id\":\"image-a\",\"image_material_id\":201},{\"image_id\":\"image-b\"}]}]}}");
        assertEquals(List.of(new MaterialLibraryPort.CarouselImage("image-a","201"),new MaterialLibraryPort.CarouselImage("image-b",null)),adapter.carouselImages(query));
        var spec=ArgumentCaptor.forClass(RequestSpec.class);verify(rest).exchange(spec.capture(),eq(String.class));
        assertTrue(spec.getValue().url().startsWith("https://api.oceanengine.com/open_api/v1.0/qianchuan/carousel/get/?advertiser_id=1869861871552519"));
        String raw=spec.getValue().url().split("filtering=")[1];var filter=json.readTree(java.net.URLDecoder.decode(raw,java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("1881234567890123456",filter.path("material_ids").get(0).asText());assertTrue(filter.path("material_ids").get(0).isIntegralNumber());
        for(String reply:List.of("{\"code\":0,\"data\":{\"carousels\":[]}}","{\"code\":0,\"data\":{\"carousels\":[{\"material_id\":1,\"images\":[{\"image_id\":\"other\"}]}]}}","{\"code\":0,\"data\":{\"carousels\":[{\"material_id\":1881234567890123456,\"images\":[{\"image_id\":201}]}]}}")) {
            when(rest.exchange(any(),eq(String.class))).thenReturn(reply);assertThrows(BizException.class,()->adapter.carouselImages(query));
        }
    }
    @Test void carouselCatalogReadsOfficialCarouselsArrayAndTotalCount() {
        var rest=mock(QianchuanRestClient.class);var adapter=new LiveMaterialLibraryPort(rest,new LynxAdProperties(),new OceanJson(new ObjectMapper()));
        when(rest.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"carousels\":[{\"material_id\":1881234567890123456,\"filename\":\"图文\",\"images\":[{\"image_id\":\"image\"}]}],\"page_info\":{\"total_count\":1}}}");
        var page=adapter.list(new MaterialLibraryPort.MaterialLibraryQuery("123",MaterialType.CAROUSEL,null,null,1,20,"token"));
        assertEquals("1881234567890123456",page.items().getFirst().materialId());assertEquals(false,page.hasMore());
    }
    @Test void uploadedAssetsUseReceiptFileIdAndActiveUploadsCannotBeDeleted() {
        asset("local","VIDEO");when(assets.syncs(tenant.value(),"local")).thenReturn(List.of(receipt("local","provider-video","123","RUNNING")));
        assertThrows(BizException.class,()->service.delete(tenant,List.of("ENGINE:local"),"account"));verifyNoInteractions(library);
        when(assets.syncs(tenant.value(),"local")).thenReturn(List.of(receipt("local","provider-video","123","UPLOADED")));
        assertEquals(List.of("ENGINE:local"),service.delete(tenant,List.of("ENGINE:local"),"account").deletedIds());
        verify(library).delete(argThat(q->q.objectIds().equals(List.of("provider-video"))));
    }
    @ParameterizedTest @ValueSource(strings={"VIDEO","IMAGE"})
    void adapterUsesOfficialFileDeleteEndpointAndParsesPartialFailure(String kind)throws Exception {
        var rest=mock(QianchuanRestClient.class);var props=new LynxAdProperties();var json=new ObjectMapper();
        var adapter=new LiveMaterialLibraryPort(rest,props,new OceanJson(json));
        String field=kind.equals("VIDEO")?"video":"image";
        when(rest.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{\"fail_"+field+"_ids\":[\"file-b\"]}}");
        assertEquals(Set.of("file-b"),adapter.delete(new MaterialLibraryPort.DeleteQuery("1869861871552519",MaterialType.valueOf(kind),List.of("file-a","file-b"),"private-token","account")));
        var spec=ArgumentCaptor.forClass(RequestSpec.class);verify(rest).exchange(spec.capture(),eq(String.class));
        var request=spec.getValue();assertEquals("POST",request.method());
        assertEquals("https://api.oceanengine.com/open_api/v1.0/qianchuan/file/"+field+"/delete/",request.url());
        var body=json.readTree(request.body());assertTrue(body.path("advertiser_id").isIntegralNumber());
        assertEquals(List.of("file-a","file-b"),json.convertValue(body.path(field+"_ids"),List.class));
        assertFalse(body.has("material_ids"));assertEquals("private-token",request.headers().get("Access-Token"));assertEquals("account",request.replayAccountId());
    }
    @Test void adapterRejectsMalformedOrBusinessFailureReceipts() {
        var rest=mock(QianchuanRestClient.class);var adapter=new LiveMaterialLibraryPort(rest,new LynxAdProperties(),new OceanJson(new ObjectMapper()));
        var query=new MaterialLibraryPort.DeleteQuery("123",MaterialType.VIDEO,List.of("file"),"token","account");
        for(String reply:List.of("{\"code\":50000,\"message\":\"failed\"}","{\"code\":0}","{\"code\":0,\"data\":{\"fail_video_ids\":\"file\"}}","{\"code\":0,\"data\":{\"fail_video_ids\":[\"other-file\"]}}")) {
            when(rest.exchange(any(),eq(String.class))).thenReturn(reply);assertThrows(BizException.class,()->adapter.delete(query));
        }
        when(rest.exchange(any(),eq(String.class))).thenReturn("{\"code\":0,\"data\":{}}");assertTrue(adapter.delete(query).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void persistedDeletionsSurviveRefreshInBothDatabaseDialectsWithoutAffectingOtherTenants(String mode)throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:library_delete_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        try(var c=ds.getConnection()) { ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V49__material_library_deletion.sql")); }
        var repo=new LibraryDeletionRepository(jdbc,mode.equals("MySQL")?"mysql":"postgresql");
        jdbc.execute("CREATE TABLE creative_carousel_platform(tenant_id VARCHAR(40),carousel_id VARCHAR(40),account_id VARCHAR(40),qc_carousel_id VARCHAR(40),status VARCHAR(20))");
        jdbc.update("INSERT INTO creative_carousel_platform VALUES(?,?,?,?,?)",tenant.value(),"group","account","native-carousel","SUCCEEDED");
        jdbc.update("INSERT INTO creative_carousel_platform VALUES(?,?,?,?,?)","other-tenant","group","other-account","private-carousel","SUCCEEDED");
        jdbc.update("INSERT INTO creative_carousel_platform VALUES(?,?,?,?,?)",tenant.value(),"group","pending-account","unconfirmed-carousel","SENDING");
        var groups=new CarouselRepository(jdbc);
        var group=new CarouselRepository.Bundle("group",tenant.value(),"user","request","图文",List.of("account"),"APPROVED",Instant.now());
        assertEquals(List.of(new CarouselRepository.PlatformReceipt("account","native-carousel")),groups.platformReceipts(group));
        repo.hide(tenant.value(),List.of("ENGINE:local","EXT:account:VIDEO:o:video-a"));repo.hide(tenant.value(),List.of("ENGINE:local"));
        assertEquals(2,repo.hidden(tenant.value()).size());assertTrue(repo.hidden("other-tenant").isEmpty());
        var local=asset("local","VIDEO");
        when(packages.listPackagesByTenant(tenant)).thenReturn(List.of());when(assets.list(tenant.value())).thenReturn(List.of(local));
        var oauth=mock(OauthQueryService.class);when(oauth.hasBoundQianchuan(tenant)).thenReturn(true);
        var bindings=mock(PlanCreativeBindingQuery.class);when(bindings.usedKeys(tenant)).thenReturn(Set.of());
        var queries=new LibraryQueryService(packages,mock(StoragePort.class),library,oauth,bindings,mock(ProductQueryService.class));
        queries.uploadDependencies(assets,null,null);queries.catalogDependency(catalog);queries.deletionDependency(repo);
        when(catalog.list(tenant.value())).thenReturn(List.of(new PlatformMaterialCatalog.Material(tenant.value(),"account","123",MaterialType.VIDEO,"m:100","video-a","100","remote.mp4",100,720,1280,18,"VERTICAL","NONE",Instant.now())));
        for(boolean picker:List.of(false,true)) {var response=queries.query(tenant,"VIDEO",null,null,null,"ALL",null,1,8,picker);assertEquals(0,response.pageInfo().total());assertEquals(0,response.tabCounts().video());}
        // A later platform catalog snapshot still cannot resurrect the successfully deleted file.
        assertTrue(queries.query(tenant,"VIDEO",null,null,null,"ALL",null,1,8).items().isEmpty());
        jdbc.execute("SHUTDOWN");
    }
}
