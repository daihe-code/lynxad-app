package com.lynx.ad.modules.delivery;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.application.ProductRecommendationService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRecommendationServiceTest {
    TenantId tenant=new TenantId("tenant");CreationQianchuanGateway qc=mock(CreationQianchuanGateway.class);
    DraftProductStore drafts=mock(DraftProductStore.class);AdPlanRepository plans=mock(AdPlanRepository.class);
    ProductRecommendationService service=new ProductRecommendationService(qc,drafts,plans);
    Product product(String id){return new Product(id,id,"折叠自行车 "+id,new BigDecimal("800"),null,"https://example.test/p","自行车",10L,List.of(),List.of());}
    @BeforeEach void setup(){
        when(qc.selected(any(),anyString(),anyString(),anyList())).thenAnswer(call->((List<String>)call.getArgument(3)).stream().map(this::product).toList());
        when(drafts.decorate(anyString(),anyString(),nullable(String.class),anyList())).thenAnswer(call->call.getArgument(3));
        when(qc.recommendationCandidates(any(),anyString(),anyString(),eq(1),eq(100))).thenReturn(new CreationQianchuanGateway.Page(List.of(product("100"),product("101")),2,1,100));
    }
    ProductRecommendationService.Result get(String account,String aweme,boolean refresh){return service.recommend(tenant,account,aweme,List.of("100"),null,refresh);}
    @Test void cacheAlwaysRechecksAuthorizationAndDraftOccupancy(){
        assertEquals(1,get("account","aweme",false).items().size());
        when(drafts.decorate(eq("tenant"),eq("account"),isNull(),anyList())).thenAnswer(call->((List<Product>)call.getArgument(3)).stream().map(p->p.withDraft(new DraftReference("other","其他草稿"))).toList());
        var cached=get("account","aweme",false);assertTrue(cached.cached());assertTrue(cached.items().isEmpty());
        verify(qc,times(2)).selected(tenant,"account","aweme",List.of("100"));verify(qc,times(1)).recommendationCandidates(tenant,"account","aweme",1,100);
        when(qc.selected(tenant,"account","aweme",List.of("100"))).thenThrow(new BizException(1211,"授权已取消"));
        assertThrows(BizException.class,()->get("account","aweme",false));
    }
    @Test void cachesAreIsolatedByTenantAccountAndAwemeAndCanBeRefreshed(){
        get("a","w",false);assertTrue(get("a","w",false).cached());assertFalse(get("b","w",false).cached());assertFalse(get("a","z",false).cached());
        service.recommend(new TenantId("other"),"a","w",List.of("100"),null,false);
        assertFalse(get("a","w",true).cached());verify(qc,times(2)).recommendationCandidates(tenant,"a","w",1,100);
        verify(qc).recommendationCandidates(new TenantId("other"),"a","w",1,100);
    }
    @Test void expiredCatalogIsFetchedAgain(){
        var now=Instant.parse("2026-10-21T00:00:00Z");ReflectionTestUtils.setField(service,"clock",Clock.fixed(now,ZoneOffset.UTC));get("a","w",false);
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(now.plusSeconds(61),ZoneOffset.UTC));assertFalse(get("a","w",false).cached());
        verify(qc,times(2)).recommendationCandidates(tenant,"a","w",1,100);
    }
    @Test void largeCatalogIsBoundedAndTruncatedResultIsReusable(){
        when(qc.recommendationCandidates(any(),anyString(),anyString(),anyInt(),eq(100))).thenAnswer(call->{int page=call.getArgument(3);var rows=new ArrayList<Product>();for(int i=0;i<100;i++)rows.add(product(""+(page*100+i)));return new CreationQianchuanGateway.Page(rows,1000,page,100);});
        var result=get("a","w",false);assertEquals(300,result.scannedCount());assertTrue(result.partial());assertEquals(1000,result.catalogTotal());assertTrue(get("a","w",false).cached());
        verify(qc,times(3)).recommendationCandidates(eq(tenant),eq("a"),eq("w"),anyInt(),eq(100));
    }
    @Test void interruptedLaterPageCanReturnExplicitPartialResultsButIsNotCached(){
        when(qc.recommendationCandidates(tenant,"a","w",1,100)).thenReturn(new CreationQianchuanGateway.Page(List.of(product("101")),200,1,100));
        when(qc.recommendationCandidates(tenant,"a","w",2,100)).thenThrow(new BizException(1406,"平台暂不可用"));
        var result=get("a","w",false);assertTrue(result.partial());assertEquals(1,result.items().size());assertTrue(result.message().contains("暂未载入"));
        assertFalse(get("a","w",false).cached());
    }
    @Test void invalidOrForeignDraftDoesNotQueryThePlatform(){
        when(plans.findById(tenant,new PlanId("foreign"))).thenReturn(Optional.empty());
        assertThrows(BizException.class,()->service.recommend(tenant,"a","w",List.of("100"),"foreign",false));
        var plan=mock(AdPlan.class);when(plan.accountId()).thenReturn(new AccountId("other-account"));when(plans.findById(tenant,new PlanId("wrong"))).thenReturn(Optional.of(plan));
        assertThrows(BizException.class,()->service.recommend(tenant,"a","w",List.of("100"),"wrong",false));verifyNoInteractions(qc);
    }
    @Test void invalidAndDuplicateSelectionNeverStartsCatalogScanning(){
        for(var ids:List.of(List.<String>of(),List.of("100","100"),List.of("bad"))){assertThrows(BizException.class,()->service.recommend(tenant,"a","w",ids,null,false));}
        verifyNoInteractions(qc);
    }
}
