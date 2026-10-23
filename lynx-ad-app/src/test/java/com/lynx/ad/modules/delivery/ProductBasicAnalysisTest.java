package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.ProductBasicAnalysisService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductBasicAnalysisTest {
    static CreationState.Product product(String id, String name, BigDecimal price, String image, String category, Long stock, List<String> reasons) {
        return new CreationState.Product(id,id,name,price,null,image,category,stock,List.of(),reasons);
    }
    static CreationState.Product valid(String id) {
        return product(id,"折叠自行车",new BigDecimal("524.00"),"https://assets.test/bike.jpg","自行车",10L,List.of());
    }
    @Test void validatesProductFactsWithoutProducingMarketScores() {
        var result=ProductBasicAnalysisService.analyze(List.of(valid("123")));
        assertTrue(result.canContinue());assertEquals("PASSED",result.status());assertTrue(result.issues().isEmpty());
        var json=new ObjectMapper().valueToTree(result);
        for(String field:List.of("estimated_roi","competitor_count","search_heat_7d","audience_match","dimensions"))assertFalse(json.has(field));
    }
    @Test void missingOptionalFactsWarnWithoutBlockingGoalEditing() {
        var result=ProductBasicAnalysisService.analyze(List.of(product("123","商品",null,null,null,null,List.of())));
        assertTrue(result.canContinue());
        assertEquals(List.of("PRICE_MISSING","IMAGE_MISSING","CATEGORY_MISSING"),result.issues().stream().map(i->i.code()).toList());
        assertTrue(result.issues().stream().allMatch(i->"WARNING".equals(i.severity())));
    }
    @Test void unavailableOutOfStockAndNamelessProductsRemainBlocked() {
        for(var p:List.of(product("123","商品",null,null,null,0L,List.of()),
                product("123","商品",null,null,null,10L,List.of("审核中")),product("123","",null,null,null,10L,List.of()))) {
            var result=ProductBasicAnalysisService.analyze(List.of(p));
            assertFalse(result.canContinue());assertTrue(result.issues().stream().anyMatch(i->"ERROR".equals(i.severity())));
        }
    }
    @Test void selectionMustBeNonemptyDistinctAndAtMostTenProducts() {
        for(var list:List.of(List.<CreationState.Product>of(),List.of(valid("123"),valid("123")),
                java.util.stream.IntStream.range(0,11).mapToObj(i->valid(String.valueOf(i+1))).toList()))
            assertFalse(ProductBasicAnalysisService.analyze(list).canContinue());
    }
    @Test void fingerprintTracksFactsButNotDecimalFormatting() {
        var original=valid("123");
        var equivalent=product("123",original.name(),new BigDecimal("524"),original.imageUrl(),original.category(),original.stock(),List.of());
        assertEquals(ProductBasicAnalysisService.fingerprint(List.of(original)),ProductBasicAnalysisService.fingerprint(List.of(equivalent)));
        assertNotEquals(ProductBasicAnalysisService.fingerprint(List.of(original)),ProductBasicAnalysisService.fingerprint(List.of(valid("456"))));
    }
    @Test void malformedOrRelativeImageAddressesWarnWithoutCrashingTheWizard() {
        for(String image:List.of("/image.jpg","not a url","data:image/png;base64,x","https://user:password@example.test/image.jpg")) {
            var result=ProductBasicAnalysisService.analyze(List.of(product("123","商品",new BigDecimal("50"),image,"类目",10L,List.of())));
            assertTrue(result.canContinue());assertEquals("IMAGE_MISSING",result.issues().getFirst().code());
        }
    }
    @Test void stalePriceIsExplicitAndOldStateRemainsReadable() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();
        var old=json.readValue("{\"analysisStatus\":\"SUCCEEDED\"}",CreationState.class);
        assertNull(old.basicAnalysis);assertNull(old.analysisRequestId);assertEquals(0,old.analysisStartedAt);
        var p=valid("123");
        var stale=new com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice(p.price(),p.price(),null,"QIANCHUAN_PRODUCT",java.time.Instant.now(),true);
        old.products.add(new CreationState.Product(p.productId(),p.qcProductId(),p.name(),p.price(),null,p.imageUrl(),p.category(),p.stock(),List.of(),List.of(),stale,null));
        var view=com.lynx.ad.modules.delivery.api.dto.CreationView.of(old);
        assertTrue(view.basicAnalysis().canContinue());assertEquals("PRICE_STALE",view.basicAnalysis().issues().getFirst().code());
    }
}
