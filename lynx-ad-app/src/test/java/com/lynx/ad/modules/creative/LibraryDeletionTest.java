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
        var result=service.delete(tenant,List.of("ENGINE:local","CAROUSEL:bundle"),"account");
        assertEquals(2,result.deletedIds().size());verifyNoInteractions(library);verify(carousels).require(tenant.value(),"bundle");
        verify(assets).require(tenant.value(),"local");verify(assets).syncs(tenant.value(),"local");verifyNoMoreInteractions(assets,carousels);
        assertEquals("original-file",original.objectKey());
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
