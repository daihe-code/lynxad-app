package com.lynx.ad.modules.delivery.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

/** A completed engine request may contain only basic facts after model failure. */
public final class ProductAnalysisOutcome {
    private ProductAnalysisOutcome() {}

    public static boolean failed(JsonNode analysis) {
        if (analysis == null) return false;
        var processing=analysis.path("processing");
        return !processing.path("inference_error").asText("").isBlank()
                || "FAILED".equals(processing.path("inference_status").asText());
    }

    public static String failureMessage(JsonNode analysis) {
        String error=analysis.path("processing").path("inference_error").asText("");
        if (error.contains("Timeout")) return "商品智能分析超时，尚未生成有效结果，请重新分析。";
        if ("JSONDecodeError".equals(error))
            return "商品智能分析返回格式异常，尚未生成有效结果，请重新分析。";
        if (java.util.Set.of("ValueError","ValidationError").contains(error))
            return "商品智能分析结果校验未通过，尚未生成有效结果，请重新分析。";
        return "商品智能分析暂不可用，尚未生成有效结果，请重新分析。";
    }
}
