package com.lynx.ad.modules.catalog.api.dto;

import java.util.List;

public record SelectProductsRequest(String awemeId, List<String> productIds) {}
