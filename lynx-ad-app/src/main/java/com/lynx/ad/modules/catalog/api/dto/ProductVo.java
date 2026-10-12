package com.lynx.ad.modules.catalog.api.dto;

import java.math.BigDecimal;

public record ProductVo(String productId, String qcProductId, String name, BigDecimal price, BigDecimal userPrice, String imageUrl,
                        com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice priceInfo) {
    public ProductVo(String productId,String qcProductId,String name,BigDecimal price,BigDecimal userPrice,String imageUrl) {
        this(productId,qcProductId,name,price,userPrice,imageUrl,null);
    }
}
