package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 自托管 Douyin_TikTok_Download_API（v5）异步任务轮询。
 *
 * <p>v5 默认是异步接口：提交解析请求后只返回 202 与任务 id（{@code data.task_id}），
 * 结果要到 {@code GET /api/v1/tasks/{task_id}} 查询，任务状态为 {@code queued}、
 * {@code running}、{@code done}、{@code failed}；完成时结果在 {@code data.data} 里，
 * 失败时仍返回 200，错误在 {@code data.error} 里（见上游
 * {@code documents/zh/11-api.md}“异步模型”）。旧版同步接口（v4 或 {@code ?wait=}）
 * 会直接返回视频地址，不走这里。
 *
 * <p>2026-11-25 之前本项目明确不轮询异步任务，v5 实例因此一次都走不通；
 * 而免登录 H5 路由与 aweme 详情接口在服务端都会被抖音 Argus 拦截，
 * 没有可用的备用路由时链接导入必然失败。本类把 v5 异步任务补成同步等待，
 * 但它本身不能替代部署：仍需一台已部署并配好抖音身份的自托管实例。
 */
public final class DownloadApiTaskPoller {

    /** v5 任务查询路径（见上游 11-api.md“异步模型”）。 */
    public static final String TASK_PATH_PREFIX = "/api/v1/tasks/";

    /** 默认最多轮询次数。 */
    static final int DEFAULT_MAX_POLLS = 10;

    /** 默认轮询间隔；10 次约 20 秒，与上游 ?wait= 上限 30 秒同量级。 */
    static final long DEFAULT_POLL_INTERVAL_MILLIS = 2000L;

    private DownloadApiTaskPoller() {
    }

    /**
     * 从备用接口的提交响应中取出任务 id：v5 放在 {@code data.task_id}，
     * 旧版放在顶层 {@code task_id}；都没有时返回 null。
     */
    public static String taskId(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        String id = root.path("data").path("task_id").asText(null);
        if (id == null || id.isBlank()) {
            id = root.path("task_id").asText(null);
        }
        return id == null || id.isBlank() ? null : id;
    }

    /**
     * 解析一次任务查询响应。
     *
     * @return 任务已完成时返回视频地址；仍在排队或执行中时返回 null，调用方继续轮询；
     *         任务失败时抛 {@link LinkImportFailure}（原因码 {@code DOWNLOAD_API_FAILED}）。
     */
    public static String playUrlFromTask(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw fail("备用下载接口任务查询返回内容无法解析，未能获取该视频。", "download api task not an object");
        }
        JsonNode data = root.path("data");
        if (!data.isObject()) {
            data = root;
        }
        String state = data.path("state").asText("");
        if ("failed".equals(state)) {
            JsonNode error = data.path("error");
            String code = error.path("code").asText("DOWNLOAD_API_FAILED");
            String message = error.path("message").asText("");
            throw fail("备用下载接口处理失败（" + code + "），未能获取该视频。",
                    "download api task failed: " + code + (message.isBlank() ? "" : " " + message));
        }
        if ("done".equals(state)) {
            JsonNode result = data.path("data");
            if (!result.isObject() && !result.isArray()) {
                result = data;
            }
            String playUrl = DouyinLinkVideoDownloader.findVideoUrl(result);
            if (playUrl == null) {
                throw fail("备用下载接口任务已完成，但未返回视频地址。",
                        "download api task done without play url");
            }
            return playUrl;
        }
        return null;
    }

    /**
     * 轮询任务直到完成并返回视频地址；超时或任务不存在时抛错。
     *
     * @param baseUrl 备用接口根地址，末尾斜杠可有可无
     * @param apiKey 备用接口密钥，可为空
     */
    public static String pollForPlayUrl(HttpClient http, ObjectMapper json,
            String baseUrl, String apiKey, String taskId) {
        return pollForPlayUrl(http, json, baseUrl, apiKey, taskId,
                DEFAULT_MAX_POLLS, DEFAULT_POLL_INTERVAL_MILLIS);
    }

    /** 与 {@link #pollForPlayUrl(HttpClient, ObjectMapper, String, String, String)} 相同，仅供测试调小轮询次数与间隔。 */
    static String pollForPlayUrl(HttpClient http, ObjectMapper json,
            String baseUrl, String apiKey, String taskId, int maxPolls, long pollIntervalMillis) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI uri = URI.create(base + TASK_PATH_PREFIX + URLEncoder.encode(taskId, StandardCharsets.UTF_8));
        for (int attempt = 0; attempt < maxPolls; attempt++) {
            HttpResponse<String> response = get(http, uri, apiKey);
            if (response.statusCode() == 404) {
                throw fail("备用下载接口任务已不存在或结果已过期，请重新提交。",
                        "download api task not found: " + taskId);
            }
            if (response.statusCode() != 200) {
                throw fail("备用下载接口任务查询返回 " + response.statusCode() + "，未能获取该视频。",
                        "download api task HTTP " + response.statusCode());
            }
            JsonNode root;
            try {
                root = json.readTree(response.body());
            } catch (Exception ex) {
                throw fail("备用下载接口任务查询返回内容无法解析，未能获取该视频。",
                        "download api task parse failed");
            }
            if (root.path("success").isBoolean() && !root.path("success").asBoolean()) {
                JsonNode error = root.path("error");
                String code = error.path("code").asText("DOWNLOAD_API_FAILED");
                String message = error.path("message").asText("");
                throw fail("备用下载接口任务查询失败（" + code + "），未能获取该视频。",
                        "download api task error: " + code + (message.isBlank() ? "" : " " + message));
            }
            String playUrl = playUrlFromTask(root);
            if (playUrl != null) {
                return playUrl;
            }
            if (attempt + 1 < maxPolls) {
                sleep(pollIntervalMillis);
            }
        }
        throw fail("备用下载接口任务超时未完成，请稍后重试。",
                "download api task timeout: " + taskId);
    }

    private static HttpResponse<String> get(HttpClient http, URI uri, String apiKey) {
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").header("User-Agent", "lynxad-app");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey.strip());
        }
        try {
            return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw fail("备用下载接口任务查询被中断，请稍后重试。", "download api task interrupted");
        } catch (IOException ex) {
            throw fail("备用下载接口暂时无法访问，未能获取该视频。", "download api task io: " + ex.getMessage());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw fail("备用下载接口任务等待被中断，请稍后重试。", "download api task wait interrupted");
        }
    }

    private static LinkImportFailure fail(String message, String detail) {
        return new LinkImportFailure(LinkImportFailure.DOWNLOAD_API_FAILED, message, detail);
    }
}
