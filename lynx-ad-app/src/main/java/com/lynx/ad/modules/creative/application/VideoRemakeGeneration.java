package com.lynx.ad.modules.creative.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成优先的复刻进度与提示：从引擎任务详情中提取警告、单分镜重试与生成进度。
 *
 * <p>引擎（lynxd）把生成前门控降级为警告后，警告只写在任务结果的
 * {@code generation_first_warnings} 里；单分镜抽查失败重试一次后记在
 * {@code shot_retries} 里。本类只做提取与呈现，不改动任何计费逻辑。
 */
public final class VideoRemakeGeneration {

    private VideoRemakeGeneration() {
    }

    /** 单分镜一次抽查重试的记录：分镜、原因、重排指令、重试结果。 */
    public record ShotRetry(String shot, int shotIndex, String reason, String status, String replan) {
    }

    /** 生成前门控降级为警告后留下的提示（参考语义、合规命中、首帧检查等），只展示、不判失败。 */
    public static List<String> generationWarnings(JsonNode data) {
        var warnings = resultNode(data, "generation_first_warnings");
        var items = new ArrayList<String>();
        if (warnings.isArray()) {
            for (var warning : warnings) {
                var detail = warning.isTextual() ? warning.asText() : warning.path("detail").asText(null);
                if (detail != null && !detail.isBlank()) {
                    items.add(detail);
                }
            }
        }
        return List.copyOf(items);
    }

    /** 每个分镜最多一次的抽查重试记录。 */
    public static List<ShotRetry> shotRetries(JsonNode data) {
        var retries = resultNode(data, "shot_retries");
        var items = new ArrayList<ShotRetry>();
        if (retries.isArray()) {
            for (var retry : retries) {
                items.add(new ShotRetry(retry.path("shot").asText(""),
                        retry.path("shot_index").asInt(0),
                        retry.path("reason").asText(""),
                        retry.path("status").asText(""),
                        retry.path("replan").asText(null)));
            }
        }
        return List.copyOf(items);
    }

    /** 已完成的生成步骤：参考已分析 → 分镜已规划 → 生成中 → 抽查中（后一步完成则前一步也算完成）。 */
    public static List<String> progressSteps(JsonNode data) {
        var generating = hasResultFieldMatching(data, "shot_\\d+_video") || stageStartsWith(data, "shot_");
        var checking = hasResultFieldMatching(data, "shot_\\d+_text_region") || hasResultField(data, "delivery")
                || stageAtLeast(data, "narration");
        var planned = hasResultField(data, "plan") || stageAtLeast(data, "planning_product") || generating || checking;
        var analysed = hasResultField(data, "analysis") || hasResultField(data, "semantics")
                || stageAtLeast(data, "understanding_reference") || planned;
        var done = new ArrayList<String>();
        if (analysed) {
            done.add("reference_analysed");
        }
        if (planned) {
            done.add("shots_planned");
        }
        if (generating) {
            done.add("generating");
        }
        if (checking) {
            done.add("checking");
        }
        return List.copyOf(done);
    }

    private static JsonNode resultNode(JsonNode data, String field) {
        var node = data.path(field);
        if ((node.isMissingNode() || node.isNull()) && data.has("result")) {
            node = data.path("result").path(field);
        }
        return node;
    }

    private static boolean hasResultField(JsonNode data, String field) {
        return data.has(field) || data.path("result").has(field);
    }

    private static boolean hasResultFieldMatching(JsonNode data, String pattern) {
        for (var node : List.of(data, data.path("result"))) {
            if (!node.isObject()) {
                continue;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (fields.next().getKey().matches(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final List<String> STAGES = List.of("preparing_product_images", "importing_product_image",
            "analyzing_reference", "analysis_partial", "understanding_reference", "planning_product", "plan",
            "planning_portrait_boards", "narration", "render_build", "outputs");

    private static boolean stageAtLeast(JsonNode data, String stage) {
        var target = STAGES.indexOf(stage);
        var current = STAGES.indexOf(data.path("stage").asText(""));
        return target >= 0 && current >= target;
    }

    private static boolean stageStartsWith(JsonNode data, String prefix) {
        return data.path("stage").asText("").startsWith(prefix);
    }
}
