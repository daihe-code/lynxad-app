package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.creative.application.LibraryQueryService;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LibrarySelectionTest {
    @Test void filtersBeforePaginationAndDoesNotHideOlderReusableAssets() {
        var tenant=new TenantId("picker-tenant");
        var repository=mock(CreativeTaskRepository.class);var uploads=mock(AssetUploadRepository.class);
        var platform=mock(MaterialLibraryPort.class);var oauth=mock(OauthQueryService.class);
        var bindings=mock(PlanCreativeBindingQuery.class);var storage=mock(StoragePort.class);
        var recent=Instant.now().minusSeconds(60);var old=recent.minusSeconds(30*86400L);
        var assets=new ArrayList<AssetUploadRepository.Asset>();
        for(int i=0;i<18;i++)assets.add(asset("rejected-"+i,tenant,"IMAGE",recent));
        for(int i=0;i<5;i++)assets.add(asset("usable-"+i,tenant,"IMAGE",old));
        assets.add(asset("video",tenant,"VIDEO",recent));
        when(repository.listPackagesByTenant(tenant)).thenReturn(List.of());
        when(oauth.hasBoundQianchuan(tenant)).thenReturn(true);when(bindings.usedKeys(tenant)).thenReturn(Set.of());
        when(uploads.list(tenant.value())).thenReturn(assets);
        when(uploads.aiReview(eq(tenant.value()),anyString())).thenAnswer(invocation->{
            String id=invocation.getArgument(1);return new AssetUploadRepository.Review(id,tenant.value(),
                id.startsWith("rejected")?"REJECTED":"APPROVED",null,null,null,List.of(),null,0,recent);
        });
        when(storage.getUrl(eq(tenant),anyString())).thenAnswer(i->"/api/v1/assets/"+i.getArgument(1));
        var service=new LibraryQueryService(repository,storage,platform,oauth,bindings,mock(ProductQueryService.class));
        service.uploadDependencies(uploads,mock(com.lynx.ad.modules.identity.domain.port.OauthAccountRepository.class),
            mock(com.lynx.ad.modules.identity.application.QianchuanCredentialFacade.class));
        var first=service.query(tenant,"IMAGE",null,null,null,"ALL",null,1,2,true);
        verify(storage,times(2)).getUrl(eq(tenant),anyString());
        var second=service.query(tenant,"IMAGE",null,null,null,"ALL",null,2,2,true);
        assertEquals(5,first.pageInfo().total());assertEquals(5,first.tabCounts().image());
        assertEquals(2,first.items().size());assertEquals(2,second.items().size());
        assertTrue(first.items().stream().allMatch(i->i.assetId().startsWith("usable-")));
        assertTrue(Collections.disjoint(first.items().stream().map(i->i.id()).toList(),second.items().stream().map(i->i.id()).toList()));
        assertEquals("usable-0",service.query(tenant,"IMAGE",null,null,null,"ALL","usable-0",1,18,true).items().getFirst().assetId());
        verifyNoInteractions(platform);
        var other=new TenantId("other-tenant");
        assertEquals(0,service.query(other,"IMAGE",null,null,null,"ALL",null,1,18,true).pageInfo().total());
        verify(uploads).list(other.value());
    }
    private static AssetUploadRepository.Asset asset(String id,TenantId tenant,String kind,Instant created) {
        return new AssetUploadRepository.Asset(id,tenant.value(),"user",id,kind,id+".png","image/png",1000,
            id,"STORED","local",null,null,"md5",false,800,800,null,null,null,created,created);
    }
}
