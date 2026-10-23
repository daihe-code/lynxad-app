package com.lynx.ad.modules.delivery;

import com.lynx.ad.modules.delivery.domain.model.CreationState.*;
import com.lynx.ad.modules.delivery.domain.service.ProductRecommendationPolicy;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductRecommendationPolicyTest {
    Product product(String id,String name,String category,int price){return new Product(id,id,name,new BigDecimal(price),null,"https://example.test/"+id,category,10L,List.of("热卖"),List.of());}
    Product seed=product("100","20寸折叠自行车","运动户外/自行车",800);
    @Test void recommendsOtherActualProductsWithReasonsAndAnchorIds(){
        var related=product("101","轻便折叠自行车","自行车",750);
        var result=ProductRecommendationPolicy.recommend(List.of(seed),List.of(seed,related,related,product("102","咖啡豆","食品",800)),6);
        assertEquals(List.of("101"),result.stream().map(r->r.product().qcProductId()).toList());
        assertEquals(List.of("100"),result.getFirst().matchedProductIds());assertTrue(result.getFirst().reasons().stream().anyMatch(r->r.contains("价格带")));
    }
    @Test void samePriceOrMarketingTagsAloneNeverMakeProductsRelevant(){
        assertTrue(ProductRecommendationPolicy.recommend(List.of(seed),List.of(product("102","咖啡豆","食品",800)),6).isEmpty());
        assertTrue(ProductRecommendationPolicy.recommend(List.of(product("100","新款男女学生包邮正品","其他",100)),List.of(product("101","新款男女学生包邮正品","其他",100)),6).isEmpty());
        assertTrue(ProductRecommendationPolicy.recommend(List.of(product("100","便携收纳水杯","水杯",100)),List.of(product("101","便携收纳鞋架","鞋架",100)),6).isEmpty());
    }
    @Test void nameKeywordsWorkWithoutCategoryButNoForecastIsInvented(){
        var result=ProductRecommendationPolicy.recommend(List.of(product("100","折叠自行车","",800)),List.of(product("101","自行车头盔","",200)),6);
        assertEquals(1,result.size());assertTrue(result.getFirst().reasons().getFirst().contains("关键词"));
        assertFalse(result.toString().contains("ROI"));
    }
    @Test void unavailableOutOfStockAndOccupiedProductsAreExcluded(){
        var base=product("101","折叠自行车","自行车",800);
        var stock=new Product("102","102",base.name(),base.price(),null,base.imageUrl(),base.category(),0L,List.of(),List.of());
        var rejected=new Product("103","103",base.name(),base.price(),null,base.imageUrl(),base.category(),10L,List.of(),List.of("不可投"));
        var occupied=base.withDraft(new DraftReference("another","其他草稿"));
        assertTrue(ProductRecommendationPolicy.recommend(List.of(seed),List.of(seed,stock,rejected,occupied),6).isEmpty());
    }
    @Test void mixedSelectionDoesNotImplyAllProductsBelongTogether(){
        var cup=product("200","保温水杯","水杯",99);
        var result=ProductRecommendationPolicy.recommend(List.of(seed,cup),List.of(product("201","随行保温水杯","水杯",89)),6);
        assertEquals(List.of("200"),result.getFirst().matchedProductIds());
    }
    @Test void stableRankingAndSixItemLimitDoNotDependOnCatalogOrder(){
        var candidates=new ArrayList<Product>();for(int i=101;i<=110;i++)candidates.add(product(""+i,"折叠自行车","自行车",800));
        var first=ProductRecommendationPolicy.recommend(List.of(seed),candidates,50);Collections.reverse(candidates);
        assertEquals(6,first.size());assertEquals(first,ProductRecommendationPolicy.recommend(List.of(seed),candidates,50));
    }
    @Test void manualPriceOverridesUnavailablePlatformPrice(){
        var p=new Product("101","101","折叠自行车",null,new BigDecimal("750"),"https://example.test/p","自行车",null,List.of(),List.of());
        assertTrue(ProductRecommendationPolicy.recommend(List.of(seed),List.of(p),6).getFirst().reasons().stream().anyMatch(r->r.contains("价格带")));
    }
}
