package com.lynx.ad.modules.delivery;
import com.lynx.ad.modules.delivery.domain.model.CreationState.*;
import com.lynx.ad.modules.delivery.domain.service.ProductRecommendationPolicy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProductRecommendationPolicyTest {
    Product product(Long stock,List<String> reasons){return new Product("101","101","可搭配的跨类目商品",BigDecimal.TEN,null,null,"其他",stock,List.of(),reasons);}
    @Test void eligibilityDoesNotRequireMatchingCategoryNameOrPrice(){assertTrue(ProductRecommendationPolicy.eligible(product(null,List.of())));}
    @Test void occupiedOutOfStockAndPlatformUnavailableAreNotSelectable(){
        assertFalse(ProductRecommendationPolicy.eligible(product(0L,List.of())));
        assertFalse(ProductRecommendationPolicy.eligible(product(10L,List.of("当前不可投"))));
        assertFalse(ProductRecommendationPolicy.eligible(product(10L,List.of()).withDraft(new DraftReference("other","其他草稿"))));
        assertFalse(ProductRecommendationPolicy.eligible(null));
    }
}
