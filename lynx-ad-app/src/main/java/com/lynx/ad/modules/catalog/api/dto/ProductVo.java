package com.lynx.ad.modules.catalog.api.dto;

import java.math.BigDecimal;

public record ProductVo(String productId, String qcProductId, String name, BigDecimal price, BigDecimal userPrice, String imageUrl) {}
