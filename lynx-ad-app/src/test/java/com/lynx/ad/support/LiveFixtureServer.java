package com.lynx.ad.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class LiveFixtureServer {

    public final HttpServer server;
    public final AtomicInteger createHits = new AtomicInteger();
    public final AtomicInteger statusHits = new AtomicInteger();
    public final AtomicInteger tokenHits = new AtomicInteger();
    public volatile boolean statusFail;
    public volatile boolean createFail;
    public volatile boolean libraryFail;
    public volatile boolean productFail;
    public volatile String createdAdId = "991001001";

    public LiveFixtureServer() throws java.io.IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void register(DynamicPropertyRegistry registry) {
        String b = base();
        registry.add("lynx.ad.mock.qianchuan", () -> "false");
        registry.add("lynx.ad.oauth.qianchuan.app-id", () -> "test-app");
        registry.add("lynx.ad.oauth.qianchuan.app-secret", () -> "test-secret");
        registry.add("lynx.ad.oauth.qianchuan.redirect-uri", () -> "http://localhost:8080/lynx-ad/api/v1/identity/oauth/callback");
        registry.add("lynx.ad.oauth.qianchuan.authorize-url", () -> "https://qianchuan.jinritemai.com/openapi/qc/audit/oauth.html");
        registry.add("lynx.ad.qianchuan.oauth.access-token-url", () -> b + "/oauth/token");
        registry.add("lynx.ad.qianchuan.oauth.refresh-token-url", () -> b + "/oauth/refresh");
        registry.add("lynx.ad.qianchuan.oauth.advertiser-get-url", () -> b + "/oauth/advertiser");
        registry.add("lynx.ad.qianchuan.oauth.aweme-authorized-url", () -> b + "/oauth/aweme");
        registry.add("lynx.ad.qianchuan.catalog.product-get-url", () -> b + "/catalog/product");
        registry.add("lynx.ad.qianchuan.delivery.suggest-budget-url", () -> b + "/delivery/budget");
        registry.add("lynx.ad.qianchuan.delivery.suggest-roi-url", () -> b + "/delivery/roi");
        registry.add("lynx.ad.qianchuan.delivery.management-base-url", () -> b + "/management/");
        registry.add("lynx.ad.qianchuan.delivery.compensate-status-url", () -> b + "/management/guarantees");
        registry.add("lynx.ad.qianchuan.delivery.ad-create-url", () -> b + "/delivery/create");
        registry.add("lynx.ad.qianchuan.delivery.ad-update-url", () -> b + "/delivery/update");
        registry.add("lynx.ad.qianchuan.delivery.ad-list-url", () -> b + "/delivery/list");
        registry.add("lynx.ad.qianchuan.delivery.ad-status-url", () -> b + "/delivery/status");
        registry.add("lynx.ad.qianchuan.report.custom-url", () -> b + "/report");
        registry.add("lynx.ad.qianchuan.material.video-upload-url", () -> b + "/material/upload");
        registry.add("lynx.ad.qianchuan.video-get-url", () -> b + "/library/video");
        registry.add("lynx.ad.qianchuan.image-get-url", () -> b + "/library/image");
        registry.add("lynx.ad.qianchuan.carousel-get-url", () -> b + "/library/carousel");
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws java.io.IOException {
        String path = ex.getRequestURI().getPath();
        String json;
        int status = 200;
        if (path.contains("/oauth/token") || path.contains("/oauth/refresh")) {
            tokenHits.incrementAndGet();
            json = "{\"code\":0,\"data\":{\"access_token\":\"live-access\",\"refresh_token\":\"live-refresh\",\"expires_in\":3600,\"advertiser_id\":\"991001\",\"advertiser_name\":\"夹具账户\"}}";
        } else if (path.contains("/oauth/advertiser")) {
            json = "{\"code\":0,\"data\":{\"list\":[{\"account_id\":991001,\"account_name\":\"夹具账户\",\"account_role\":\"ADVERTISER\",\"account_type\":\"QIANCHUAN\"}]}}";
        } else if (path.contains("/oauth/aweme")) {
            json = "{\"code\":0,\"data\":{\"list\":[{\"aweme_id\":\"aweme_live\",\"aweme_name\":\"夹具号\"}]}}";
        } else if (path.contains("/delivery/create")) {
            createHits.incrementAndGet();
            if (createFail) {
                status = 400;
                json = "{\"code\":400,\"message\":\"create fail\"}";
            } else {
                json = "{\"code\":0,\"data\":{\"ad_id\":\"" + createdAdId + "\"}}";
            }
        } else if (path.contains("/delivery/list")) {
            json = "{\"code\":0,\"data\":{\"ad_list\":[{\"ad_id\":\""
                    + createdAdId
                    + "\",\"name\":\"夹具计划\",\"status\":\"DELIVERING\",\"smart_bid_type\":\"SMART_BID_CONSERVATIVE\",\"budget\":300}],\"page_info\":{\"total_page\":1}}}";
        } else if (path.contains("/delivery/status")) {
            statusHits.incrementAndGet();
            if (statusFail) {
                status = 400;
                json = "{\"code\":400,\"message\":\"status fail\"}";
            } else {
                json = "{\"code\":0,\"data\":{}}";
            }
        } else if (path.contains("/delivery/budget")) {
            json = "{\"code\":0,\"data\":{\"suggest_budget\":300}}";
        } else if (path.contains("/delivery/roi")) {
            json = "{\"code\":0,\"data\":{\"suggest_roi\":2.5}}";
        } else if (path.contains("/report")) {
            json = "{\"code\":0,\"data\":{\"list\":[{\"stat_cost\":200000,\"show_cnt\":100,\"click_cnt\":10,\"convert_cnt\":1,\"pay_order_amount\":200000,\"roi\":2.0,\"ctr\":0.1}]}}";
        } else if (path.contains("/catalog/product")) {
            if (productFail) {
                status = 500;
                json = "{\"code\":500,\"message\":\"pool fail\"}";
            } else {
                json = "{\"code\":0,\"data\":{\"list\":[{\"product_id\":\"live_p01\",\"name\":\"真链面包\",\"price\":19.9}]}}";
            }
        } else if (path.contains("/library/")) {
            if (libraryFail) {
                status = 500;
                json = "{\"code\":500,\"message\":\"lib fail\"}";
            } else {
                json = "{\"code\":0,\"data\":{\"list\":[{\"material_id\":\"qc_vid_1\",\"object_id\":\"ext-1\",\"filename\":\"平台竖屏.mp4\",\"size\":2048,\"width\":720,\"height\":1280,\"duration\":15,\"image_mode\":\"VIDEO_VERTICAL\",\"url\":\"/u\",\"poster_url\":\"/p\"}]}}";
            }
        } else if (path.contains("/material/upload")) {
            json = "{\"code\":0,\"data\":{\"video_id\":\"vid_up_1\"}}";
        } else {
            json = "{\"code\":0,\"data\":{}}";
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
