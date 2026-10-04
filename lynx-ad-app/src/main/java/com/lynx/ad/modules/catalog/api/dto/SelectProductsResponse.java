package com.lynx.ad.modules.catalog.api.dto;

import java.util.List;

public record SelectProductsResponse(List<ProductVo> products, String analysis, String warn) {}
