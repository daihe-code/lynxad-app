package com.lynx.ad.modules.delivery.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import com.lynx.ad.modules.delivery.domain.model.ProductBasicAnalysis;
import com.lynx.ad.modules.delivery.domain.model.ProductBasicAnalysis.Issue;
import com.lynx.ad.modules.delivery.domain.model.ProductBasicAnalysis.ProductCheck;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Pure, local checks. Missing optional facts are warnings, not invented scores. */
public final class ProductBasicAnalysisService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ProductBasicAnalysisService() {}

    public static ProductBasicAnalysis analyze(List<Product> products) {
        var issues = new ArrayList<Issue>();
        var checks = new ArrayList<ProductCheck>();
        var ids = new HashSet<String>();
        if (products.isEmpty() || products.size() > 10)
            issues.add(new Issue(null, null, "ERROR", "PRODUCT_COUNT", "请选择 1–10 个不同商品"));
        for (var p : products) {
            var item = new ArrayList<Issue>();
            if (blank(p.qcProductId()) || !ids.add(p.qcProductId()))
                issue(item, p, "ERROR", "PRODUCT_ID", "商品标识缺失或重复，请重新选择商品");
            if (blank(p.name())) issue(item, p, "ERROR", "PRODUCT_NAME", "商品名称缺失，请完善商品信息");
            if (p.unavailableReasons() != null) for (String reason : p.unavailableReasons())
                if (!blank(reason)) issue(item, p, "ERROR", "UNAVAILABLE", "商品当前不可投：" + reason);
            if (p.stock() != null && p.stock() <= 0)
                issue(item, p, "ERROR", "OUT_OF_STOCK", "商品库存不足，请核实商品状态");
            var price = p.userPrice() != null ? p.userPrice() : p.price();
            if (price == null || price.signum() <= 0)
                issue(item, p, "WARNING", "PRICE_MISSING", "暂未取得有效价格，无法据此估算收益；可继续设置目标");
            if (p.priceInfo() != null && p.priceInfo().stale())
                issue(item, p, "WARNING", "PRICE_STALE", "价格使用历史快照，请在投放前核实最新价格");
            if (!imageUrl(p.imageUrl()))
                issue(item, p, "WARNING", "IMAGE_MISSING", "主图缺失或地址无效，AI 生成前需要补齐；也可选用已有素材");
            if (blank(p.category()))
                issue(item, p, "WARNING", "CATEGORY_MISSING", "商品类目缺失，可能影响 AI 分析的准确性");
            checks.add(new ProductCheck(p.qcProductId(), p.name(), List.copyOf(item)));
            issues.addAll(item);
        }
        boolean ready = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
        return new ProductBasicAnalysis(ready ? "PASSED" : "BLOCKED", ready, Instant.now().toString(),
                fingerprint(products), List.copyOf(checks), List.copyOf(issues));
    }

    /** Stable across price-refresh timestamps and decimal formatting; changes with selected facts. */
    public static String fingerprint(List<Product> products) {
        var facts = new ArrayList<Object>();
        for (var p : products) {
            var row = new LinkedHashMap<String,Object>();
            row.put("id", p.qcProductId()); row.put("name", p.name()); row.put("category", p.category());
            row.put("price", p.price() == null ? null : p.price().stripTrailingZeros().toPlainString());
            row.put("userPrice", p.userPrice() == null ? null : p.userPrice().stripTrailingZeros().toPlainString());
            row.put("image", p.imageUrl()); row.put("stock", p.stock()); row.put("reasons", p.unavailableReasons());
            if (p.priceInfo() != null) {
                row.put("minPrice", decimal(p.priceInfo().minPrice())); row.put("maxPrice", decimal(p.priceInfo().maxPrice()));
            }
            facts.add(row);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsString(facts).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Unable to fingerprint product facts", e); }
    }
    private static void issue(List<Issue> issues, Product p, String severity, String code, String message) {
        issues.add(new Issue(p.qcProductId(), p.name(), severity, code, message));
    }
    private static boolean blank(String text) { return text == null || text.isBlank(); }
    private static String decimal(java.math.BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }
    private static boolean imageUrl(String value) {
        if (blank(value)) return false;
        try {
            var uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
}
