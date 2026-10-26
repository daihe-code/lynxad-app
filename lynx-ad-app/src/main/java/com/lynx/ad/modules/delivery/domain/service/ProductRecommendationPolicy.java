package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import java.util.List;

/** Eligibility stays deterministic; combination relevance comes from the perception model. */
public final class ProductRecommendationPolicy {
    private ProductRecommendationPolicy() {}
    public record Suggestion(Product product,List<String> reasons,List<String> matchedProductIds) {}
    public static boolean eligible(Product p) {
        return p!=null&&p.qcProductId()!=null&&p.qcProductId().matches("[1-9][0-9]{0,18}")
                &&p.name()!=null&&!p.name().isBlank()&&p.draft()==null
                &&(p.stock()==null||p.stock()>0)&&(p.unavailableReasons()==null||p.unavailableReasons().isEmpty());
    }
}
