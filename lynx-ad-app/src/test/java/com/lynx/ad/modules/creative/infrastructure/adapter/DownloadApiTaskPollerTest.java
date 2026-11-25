package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DownloadApiTaskPoller} 的单测：只用本地回环 HTTP 服务与内存 JSON，
 * 不访问抖音、不访问任何已部署的下载接口实例。
 */
class DownloadApiTaskPollerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLAY_URL = "https://example.invalid/video/7703757088427036553.mp4";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retryableFailuresContainTransientReasonsOnly() {
        assertTrue(DouyinLinkVideoDownloader.RETRYABLE_FAILURES.contains(LinkImportFailure.VIDEOINFO_MISSING));
        assertTrue(DouyinLinkVideoDownloader.RETRYABLE_FAILURES.contains(LinkImportFailure.DOWNLOAD_API_FAILED));
        assertTrue(DouyinLinkVideoDownloader.RETRYABLE_FAILURES.contains(LinkImportFailure.SHARE_PAGE_FETCH));
        assertFalse(DouyinLinkVideoDownloader.RETRYABLE_FAILURES.contains(LinkImportFailure.VIDEO_PRIVATE));
        assertFalse(DouyinLinkVideoDownloader.RETRYABLE_FAILURES.contains(LinkImportFailure.VIDEO_DELETED));
        assertFalse(DouyinLinkVideoDownloader.RETRYABLE_FAILURES.contains(LinkImportFailure.LOGIN_REQUIRED));
    }

    @Test
    void taskIdPrefersNestedDataTaskId() throws Exception {
        JsonNode root = JSON.readTree("""
                {"success": true, "data": {"task_id": "v5-task-1", "state": "queued"}}""");
        assertEquals("v5-task-1", DownloadApiTaskPoller.taskId(root));
    }

    @Test
    void taskIdFallsBackToTopLevelTaskId() throws Exception {
        JsonNode root = JSON.readTree("""
                {"task_id": "legacy-task-2", "state": "queued"}""");
        assertEquals("legacy-task-2", DownloadApiTaskPoller.taskId(root));
    }

    @Test
    void taskIdReturnsNullWhenAbsent() throws Exception {
        JsonNode root = JSON.readTree("""
                {"success": true, "data": {"video_url": "https://example.invalid/x.mp4"}}""");
        assertNull(DownloadApiTaskPoller.taskId(root));
    }

    @Test
    void playUrlFromTaskReturnsNullWhileQueuedOrRunning() throws Exception {
        assertNull(DownloadApiTaskPoller.playUrlFromTask(JSON.readTree(
                "{\"success\": true, \"data\": {\"task_id\": \"t\", \"state\": \"queued\"}}")));
        assertNull(DownloadApiTaskPoller.playUrlFromTask(JSON.readTree(
                "{\"success\": true, \"data\": {\"task_id\": \"t\", \"state\": \"running\"}}")));
    }

    @Test
    void playUrlFromTaskReturnsVideoUrlWhenDone() throws Exception {
        JsonNode root = JSON.readTree("""
                {"success": true, "data": {"task_id": "t", "state": "done",
                  "data": {"media": {"video": {"url": "https://example.invalid/video/7703757088427036553.mp4"}}}}}""");
        assertEquals(PLAY_URL, DownloadApiTaskPoller.playUrlFromTask(root));
    }

    @Test
    void playUrlFromTaskThrowsWhenDoneWithoutVideoUrl() throws Exception {
        JsonNode root = JSON.readTree(
                "{\"success\": true, \"data\": {\"task_id\": \"t\", \"state\": \"done\", \"data\": {}}}");
        LinkImportFailure failure = assertThrows(LinkImportFailure.class,
                () -> DownloadApiTaskPoller.playUrlFromTask(root));
        assertEquals(LinkImportFailure.DOWNLOAD_API_FAILED, failure.reason());
    }

    @Test
    void playUrlFromTaskThrowsWithUpstreamCodeWhenFailed() throws Exception {
        JsonNode root = JSON.readTree("""
                {"success": true, "data": {"task_id": "t", "state": "failed",
                  "error": {"code": "NOT_FOUND", "message": "video not found", "retryable": false}}}""");
        LinkImportFailure failure = assertThrows(LinkImportFailure.class,
                () -> DownloadApiTaskPoller.playUrlFromTask(root));
        assertEquals(LinkImportFailure.DOWNLOAD_API_FAILED, failure.reason());
        assertTrue(failure.detail().contains("NOT_FOUND"), failure.detail());
    }

    @Test
    void pollForPlayUrlPollsUntilDone() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> {
            int call = calls.incrementAndGet();
            String body = call < 3
                    ? "{\"success\": true, \"data\": {\"task_id\": \"t\", \"state\": \"queued\"}}"
                    : "{\"success\": true, \"data\": {\"task_id\": \"t\", \"state\": \"done\","
                    + "\"data\": {\"media\": {\"video\": {\"url\": \"" + PLAY_URL + "\"}}}}}";
            sendJson(exchange, 200, body);
        });
        String playUrl = DownloadApiTaskPoller.pollForPlayUrl(
                HttpClient.newHttpClient(), JSON, baseUrl(), null, "t", 5, 10L);
        assertEquals(PLAY_URL, playUrl);
        assertEquals(3, calls.get());
    }

    @Test
    void pollForPlayUrlThrowsWhenTaskNotFound() throws Exception {
        startServer(exchange -> sendJson(exchange, 404,
                "{\"success\": false, \"error\": {\"code\": \"TASK_NOT_FOUND\", \"message\": \"task not found\"}}"));
        LinkImportFailure failure = assertThrows(LinkImportFailure.class, () ->
                DownloadApiTaskPoller.pollForPlayUrl(
                        HttpClient.newHttpClient(), JSON, baseUrl(), null, "gone", 3, 10L));
        assertEquals(LinkImportFailure.DOWNLOAD_API_FAILED, failure.reason());
    }

    @Test
    void pollForPlayUrlThrowsOnTimeout() throws Exception {
        startServer(exchange -> sendJson(exchange, 200,
                "{\"success\": true, \"data\": {\"task_id\": \"t\", \"state\": \"running\"}}"));
        LinkImportFailure failure = assertThrows(LinkImportFailure.class, () ->
                DownloadApiTaskPoller.pollForPlayUrl(
                        HttpClient.newHttpClient(), JSON, baseUrl(), null, "t", 3, 10L));
        assertTrue(failure.detail().contains("timeout"), failure.detail());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void startServer(Consumer<HttpExchange> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/tasks/", exchange -> {
            try {
                handler.accept(exchange);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        server.start();
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
