package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.application.ProductCombinationService;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import com.lynx.ad.modules.delivery.infrastructure.persistence.ProductCombinationFactsStore;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ProductCombinationServiceTest {
    final ObjectMapper json=new ObjectMapper();final CreationEngineClient engine=mock(CreationEngineClient.class);
    final ProductCombinationFactsStore facts=mock(ProductCombinationFactsStore.class);
    final ProductCombinationService service=new ProductCombinationService(engine,facts,json);final TenantId tenant=new TenantId("tenant");
    final Product perfume=product("100","女士香水","香水",13),aroma=product("101","无火藤条香薰","家居香氛",16);
    Product product(String id,String name,String category,int price){return new Product(id,id,name,BigDecimal.valueOf(price),null,null,category,10L,List.of(),List.of());}
    com.fasterxml.jackson.databind.JsonNode output()throws Exception {var response=json.readTree("""
        {"method":"AI_COMBINATION_V1","items":[{"product_id":"101","matched_product_ids":["100"],"reasons":["AI组合建议：围绕香氛礼赠场景搭配个人用香与家居香氛"]}]}
        """);when(engine.post(anyString(),any())).thenReturn(response);return response;}
    @Test void recommendsDifferentCategoriesFromActualModelOutputAndNeverInventsRoiInputs()throws Exception {
        output();var result=service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        assertEquals(aroma,result.getFirst().product());assertEquals(List.of("100"),result.getFirst().matchedProductIds());
        var payload=ArgumentCaptor.forClass(Object.class);verify(engine).post(eq("/api/v1/capabilities/perception/products/combinations"),payload.capture());
        var body=json.valueToTree(payload.getValue());assertEquals("tenant",body.path("store_id").asText());assertEquals("101",body.path("candidates").get(0).path("id").asText());assertFalse(body.toString().contains("roi_goal"));
    }
    @Test void scopesCachesByTenantAndIncludesActualGoalEvidenceInTheFingerprint()throws Exception {
        output();service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);verify(engine,times(1)).post(any(),any());
        when(facts.goals(eq("tenant"),eq("account"),eq("aweme"),any())).thenReturn(Map.of("100",new ProductCombinationFactsStore.Goals(new BigDecimal("4.2"),new BigDecimal("4.8"))));
        service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);verify(engine,times(2)).post(any(),any());
        service.recommend(new TenantId("other"),"account","aweme",List.of(perfume),List.of(aroma),false);verify(engine,times(3)).post(any(),any());
        service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),true);verify(engine,times(4)).post(any(),any());
    }
    @Test void invalidModelResultNeverFallsBackToSimilarProducts()throws Exception {
        when(engine.post(any(),any())).thenReturn(json.createObjectNode());assertThrows(BizException.class,()->service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false));
        when(engine.post(any(),any())).thenThrow(new BizException(8002,"引擎异常"));assertThrows(BizException.class,()->service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false));
    }
    @Test void onlyRealCandidateIdsAndActualSelectionAnchorsCanBeReturned()throws Exception {
        when(engine.post(any(),any())).thenReturn(json.readTree("""
        {"method":"AI_COMBINATION_V1","items":[{"product_id":"999","matched_product_ids":["100"],"reasons":["跨店商品"]},{"product_id":"101","matched_product_ids":["foreign"],"reasons":["其他商品"]}]}
        """));assertTrue(service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false).isEmpty());
        clearInvocations(engine);assertTrue(service.recommend(tenant,"account","aweme",List.of(perfume),List.of(),false).isEmpty());verifyNoInteractions(engine);
    }
    @Test void switchingAnchorsDoesNotDiscardThePreviousRecommendationOrTurnItIntoAnEmptyResult()throws Exception {
        var response=output();
        when(engine.post(anyString(),any())).thenReturn(response,json.readTree("{\"method\":\"AI_COMBINATION_V1\",\"items\":[]}"));
        var first=service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        assertTrue(service.recommend(tenant,"account","aweme",List.of(aroma),List.of(perfume),false).isEmpty());
        assertEquals(first,service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false));
        verify(engine,times(2)).post(any(),any());
    }
    @Test void catalogOrderingAndNumericScaleDoNotChangeTheRecommendationContext()throws Exception {
        output();var third=product("102","护手霜","护肤",20);
        var first=service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma,third),false);
        var samePrice=new Product("100","100",perfume.name(),new BigDecimal("13.00"),null,null,perfume.category(),10L,List.of(),List.of());
        assertEquals(first,service.recommend(tenant,"account","aweme",List.of(samePrice),List.of(third,aroma),false));
        verify(engine,times(1)).post(any(),any());
    }
    @Test void cachedRankingUsesCurrentCandidateMetadataRatherThanTheOldProductSnapshot()throws Exception {
        output();service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        var latest=new Product("101","101",aroma.name(),aroma.price(),null,"https://example.test/new.jpg",aroma.category(),25L,List.of(),List.of());
        var result=service.recommend(tenant,"account","aweme",List.of(perfume),List.of(latest),false);
        assertEquals(latest,result.getFirst().product());verify(engine,times(1)).post(any(),any());
    }
    @Test void unchangedFactsRemainStableAcrossCatalogRefreshesAndExpireAfterTenMinutes()throws Exception {
        output();var now=Instant.parse("2026-11-07T00:00:00Z");
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(now,ZoneOffset.UTC));
        var first=service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(now.plusSeconds(61),ZoneOffset.UTC));
        assertEquals(first,service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false));
        verify(engine,times(1)).post(any(),any());
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(now.plusSeconds(600),ZoneOffset.UTC));
        service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        verify(engine,times(2)).post(any(),any());
    }
    @Test void actualCatalogChangesAndExplicitRefreshAreReevaluated()throws Exception {
        output();service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        var changed=product("101","无火香薰礼盒","家居香氛",30);
        service.recommend(tenant,"account","aweme",List.of(perfume),List.of(changed),false);
        service.recommend(tenant,"account","aweme",List.of(perfume),List.of(changed),true);
        verify(engine,times(3)).post(any(),any());
    }
    @Test void failedRefreshDoesNotReplaceAValidRecommendationWithAnEmptyResult()throws Exception {
        output();var first=service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        when(engine.post(any(),any())).thenThrow(new BizException(8002,"temporary failure"));
        assertThrows(BizException.class,()->service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),true));
        assertEquals(first,service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false));
        verify(engine,times(2)).post(any(),any());
    }
    @Test void cachesAreIsolatedByAccountAndAwemeEvenWithIdenticalProducts()throws Exception {
        output();service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        service.recommend(tenant,"other","aweme",List.of(perfume),List.of(aroma),false);
        service.recommend(tenant,"account","other",List.of(perfume),List.of(aroma),false);
        service.recommend(tenant,"account","aweme",List.of(perfume),List.of(aroma),false);
        verify(engine,times(3)).post(any(),any());
    }
}
