package com.lynx.ad.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.infrastructure.adapter.AiEngineAssetReviewAdapter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class AiEngineAssetReviewContractTest {
    private HttpServer server;
    private final ObjectMapper mapper=new ObjectMapper();
    private final AtomicReference<String> response=new AtomicReference<>(),body=new AtomicReference<>(),path=new AtomicReference<>(),method=new AtomicReference<>();
    private AiEngineAssetReviewAdapter adapter;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/capabilities/review",exchange->{
            path.set(exchange.getRequestURI().getPath());
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertNull(exchange.getRequestHeaders().getFirst("Access-Token"));
            assertNull(exchange.getRequestHeaders().getFirst("Upgrade"));
            byte[] data=response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(exchange.getRequestMethod().equals("POST") && (path.get().endsWith("video") || path.get().endsWith("audio")) ? 202 : 200,data.length);
            exchange.getResponseBody().write(data); exchange.close();
        }); server.start();
        var props=new LynxAdProperties(); props.getAi().getReview().setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
        props.getAi().getReview().setMediaBaseUrl("http://127.0.0.1:8080");
        adapter=new AiEngineAssetReviewAdapter(props,mapper);
    }
    @AfterEach void close() { server.stop(0); }
    private String verdict(String type,String decision,boolean passed) { return "{\"material_type\":\""+type+"\",\"review_id\":\"rev_test\",\"status\":\"succeeded\",\"decision\":\""+decision+"\",\"passed\":"+passed+",\"summary\":\"审核结论\",\"reasons\":[{\"message\":\"检查结果\"}]}"; }
    @Test void imageUsesSignedMediaUrlAndExplicitApproval() throws Exception {
        response.set(verdict("image","approved",true));
        var result=adapter.submit(AssetKind.IMAGE,"/lynx-ad/media?token=signature",null,"tenant-a","asset-a");
        assertEquals("APPROVED",result.status()); assertEquals("检查结果",result.reasons().getFirst());
        var input=mapper.readTree(body.get()); assertEquals("http://127.0.0.1:8080/lynx-ad/media?token=signature",input.path("material_url").asText());
        assertEquals("tenant-a",input.path("store_id").asText()); assertFalse(input.has("access_token"));
        response.set(verdict("image","approved",false));
        assertEquals("FAILED",adapter.submit(AssetKind.IMAGE,"https://oss.example/image",null,"tenant-a","asset-a").status());
    }
    @Test void videoAndAudioPersistJobAndPollFixedEndpoint() {
        response.set("{\"material_type\":\"video\",\"status\":\"queued\",\"review_id\":\"rev_test\",\"poll_url\":\"https://untrusted.invalid/path\"}");
        assertEquals("PENDING",adapter.submit(AssetKind.VIDEO,"https://oss.example/video",null,"tenant-a","asset-a").status());
        response.set("{\"material_type\":\"video\",\"status\":\"processing\",\"review_id\":\"rev_test\"}");
        assertEquals("PENDING",adapter.poll(AssetKind.VIDEO,"rev_test").status());
        response.set("{\"material_type\":\"audio\",\"status\":\"succeeded\",\"review_id\":\"rev_test\",\"result\":"+verdict("audio","rejected",false)+"}");
        assertEquals("REJECTED",adapter.poll(AssetKind.AUDIO,"rev_test").status());
        assertEquals("/api/v1/capabilities/review/audio/rev_test",path.get());
        assertEquals("",body.get());
    }
    @Test void bothAsyncTypesCreateThenPollUntilFinalVerdict() {
        for(var kind:java.util.List.of(AssetKind.VIDEO,AssetKind.AUDIO)) {
            String type=kind.name().toLowerCase(java.util.Locale.ROOT);
            response.set("{\"material_type\":\""+type+"\",\"status\":\"queued\",\"review_id\":\"rev_test\"}");
            var created=adapter.submit(kind,"https://oss.example/material",null,"tenant-a","asset-a");
            assertEquals("PENDING",created.status()); assertEquals("rev_test",created.reviewId());
            assertEquals("POST",method.get()); assertEquals("/api/v1/capabilities/review/"+type,path.get());
            response.set("{\"material_type\":\""+type+"\",\"status\":\"processing\",\"review_id\":\"rev_test\"}");
            assertEquals("PENDING",adapter.poll(kind,created.reviewId()).status()); assertEquals("GET",method.get());
            response.set("{\"material_type\":\""+type+"\",\"status\":\"succeeded\",\"review_id\":\"rev_test\",\"result\":"+verdict(type,"approved",true)+"}");
            assertEquals("APPROVED",adapter.poll(kind,created.reviewId()).status());
            assertEquals("GET",method.get()); assertEquals("/api/v1/capabilities/review/"+type+"/rev_test",path.get());
        }
    }
    @Test void asyncCreationMustSupplyJobIdAndCannotDirectlyReleaseSync() {
        response.set(verdict("audio","approved",true));
        assertEquals("PENDING",adapter.submit(AssetKind.AUDIO,"https://oss.example/audio",null,"tenant-a","asset-a").status());
        response.set("{\"material_type\":\"audio\",\"status\":\"queued\"}");
        assertThrows(BizException.class,()->adapter.submit(AssetKind.AUDIO,"https://oss.example/audio",null,"tenant-a","asset-a"));
        response.set("{\"material_type\":\"video\",\"status\":\"processing\",\"review_id\":\"rev_other\"}");
        assertThrows(BizException.class,()->adapter.poll(AssetKind.VIDEO,"rev_test"));
    }
    @Test void textSendsDecodedContentAndDoesNotApproveManualReviewOrProcessingErrors() throws Exception {
        response.set(verdict("text","manual_review",false));
        assertEquals("MANUAL_REVIEW",adapter.submit(AssetKind.TEXT,null,"文案内容","tenant-a","asset-a").status());
        assertEquals("文案内容",mapper.readTree(body.get()).path("content_text").asText());
        assertFalse(mapper.readTree(body.get()).has("material_url"));
        response.set("{\"material_type\":\"text\",\"status\":\"failed\",\"error\":{\"message\":\"模型未配置\"}}");
        assertEquals("FAILED",adapter.submit(AssetKind.TEXT,null,"内容","tenant-a","asset-a").status());
        assertThrows(BizException.class,()->adapter.submit(AssetKind.TEXT,null,"x".repeat(50001),"tenant-a","asset-a"));
    }
    @Test void malformedOrMismatchedResponsesFailClosed() {
        response.set("{\"material_type\":\"image\",\"status\":\"succeeded\",\"passed\":true}");
        assertEquals("FAILED",adapter.submit(AssetKind.IMAGE,"https://oss.example/image",null,"tenant-a","asset-a").status());
        response.set(verdict("text","approved",true));
        assertThrows(BizException.class,()->adapter.submit(AssetKind.IMAGE,"https://oss.example/image",null,"tenant-a","asset-a"));
        response.set("<html>proxy failure</html>");
        assertThrows(BizException.class,()->adapter.submit(AssetKind.IMAGE,"https://oss.example/image",null,"tenant-a","asset-a"));
    }
}
