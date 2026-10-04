package com.lynx.ad.modules.catalog.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record ProductAnalysis(
        List<String> tags,
        List<String> sellingPoints,
        List<String> audiences,
        List<String> risks,
        BigDecimal potentialScore) {}
