package com.lynx.ad.modules.delivery;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.application.ProductRecommendationService;
import com.lynx.ad.modules.delivery.application.ProductCombinationService;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import com.lynx.ad.modules.delivery.domain.service.ProductRecommendationPolicy.Suggestion;
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
    @Test void excludesTheEntireSelectionBeforeLimitingRecommendationsToSix(){
        var candidates=new ArrayList<Product>();for(int i=100;i<=110;i++)candidates.add(product(""+i));
        when(qc.recommendationCandidates(tenant,"account","aweme",1,100)).thenReturn(new CreationQianchuanGateway.Page(candidates,11,1,100));
        var result=service.recommend(tenant,"account","aweme",List.of("100"),List.of("100","101","102","103"),null,false);
        assertEquals(6,result.items().size());
        assertTrue(result.items().stream().noneMatch(item->List.of("100","101","102","103").contains(item.product().qcProductId())));
        assertEquals(List.of("100"),result.sourceProductIds());
    }
    TenantId tenant=new TenantId("tenant");CreationQianchuanGateway qc=mock(CreationQianchuanGateway.class);
    DraftProductStore drafts=mock(DraftProductStore.class);AdPlanRepository plans=mock(AdPlanRepository.class);
    ProductCombinationService combinations=mock(ProductCombinationService.class);CreationStore creations=mock(CreationStore.class);
    ProductRecommendationService service=new ProductRecommendationService(qc,drafts,plans,combinations,creations);
    Product product(String id){return new Product(id,id,"折叠自行车 "+id,new BigDecimal("800"),null,"https://example.test/p","自行车",10L,List.of(),List.of());}
    @BeforeEach void setup(){
        when(combinations.recommend(any(),anyString(),anyString(),anyList(),anyList(),anyBoolean())).thenAnswer(call->{List<Product> seeds=call.getArgument(3);List<Product> candidates=call.getArgument(4);return candidates.stream().limit(6).map(p->new Suggestion(p,List.of("AI组合建议：用途互补，可共同测试"),List.of(seeds.getFirst().qcProductId()))).toList();});
        when(qc.selected(any(),anyString(),anyString(),anyList())).thenAnswer(call->((List<String>)call.getArgument(3)).stream().map(this::product).toList());
        when(drafts.decorate(anyString(),anyString(),nullable(String.class),anyList())).thenAnswer(call->call.getArgument(3));
        when(qc.refreshPrices(any(),anyString(),anyString(),anyList())).thenAnswer(call->call.getArgument(3));
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
        verify(qc,times(2)).refreshPrices(eq(tenant),eq("a"),eq("w"),argThat(rows->rows.size()==6));
    }
    @Test void finalRecommendationsReceivePricesEvenWhenCandidateAndRankingAreCached(){
        var unpriced=new Product("101","101","瑜伽袜",null,null,"https://example.test/p","袜子",10L,List.of(),List.of());
        when(qc.recommendationCandidates(tenant,"account","aweme",1,100)).thenReturn(new CreationQianchuanGateway.Page(List.of(unpriced,product("102")),2,1,100));
        when(combinations.recommend(any(),anyString(),anyString(),anyList(),anyList(),anyBoolean())).thenReturn(List.of(new Suggestion(unpriced,List.of("AI组合建议：运动穿着搭配"),List.of("100"))));
        var quote=new com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice(new BigDecimal("19.90"),new BigDecimal("29.90"),null,"QIANCHUAN_AVAILABLE",Instant.now(),false);
        var priced=new Product("101","101",unpriced.name(),quote.minPrice(),new BigDecimal("25.00"),unpriced.imageUrl(),unpriced.category(),unpriced.stock(),unpriced.tags(),unpriced.unavailableReasons(),quote,null);
        when(qc.refreshPrices(tenant,"account","aweme",List.of(unpriced))).thenReturn(List.of(priced));
        var first=get("account","aweme",false);var cached=get("account","aweme",false);
        assertFalse(first.cached());assertTrue(cached.cached());
        for(var result:List.of(first,cached)) {
            var row=result.items().getFirst();assertEquals(priced,row.product());
            assertEquals(List.of("AI组合建议：运动穿着搭配"),row.reasons());assertEquals(List.of("100"),row.matchedProductIds());
            assertEquals(new BigDecimal("19.90"),row.product().price());assertEquals(new BigDecimal("29.90"),row.product().priceInfo().maxPrice());
            assertEquals(new BigDecimal("25.00"),row.product().userPrice());
        }
        verify(qc,times(1)).recommendationCandidates(tenant,"account","aweme",1,100);
        verify(qc,times(2)).refreshPrices(tenant,"account","aweme",List.of(unpriced));
        assertNull(unpriced.price());assertNull(unpriced.priceInfo());
    }
    @Test void emptyRecommendationsDoNotQueryPrices(){
        when(combinations.recommend(any(),anyString(),anyString(),anyList(),anyList(),anyBoolean())).thenReturn(List.of());
        assertTrue(get("account","aweme",false).items().isEmpty());
        verify(qc,never()).refreshPrices(any(),anyString(),anyString(),anyList());
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
    @Test void recommendationsAcceptTheCurrentPublishedCompositionWorkingDraft(){
        var work=new CreationState();work.accountId="account";work.awemeId="aweme";work.publishedEdit=new CreationState.PublishedEdit();work.publishedEdit.targetPlanId="original";
        when(creations.get("tenant","working")).thenReturn(work);
        when(plans.findById(tenant,new PlanId("original"))).thenReturn(Optional.of(mock(AdPlan.class)));
        assertEquals("AI_COMBINATION_V1",service.recommend(tenant,"account","aweme",List.of("100"),"working",false).method());
        clearInvocations(qc);work.publishedEdit.status="SAVING";
        assertThrows(BizException.class,()->service.recommend(tenant,"account","aweme",List.of("100"),"working",false));verifyNoInteractions(qc);
    }
}
