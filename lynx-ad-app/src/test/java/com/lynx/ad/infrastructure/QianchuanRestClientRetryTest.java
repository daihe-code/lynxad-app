package com.lynx.ad.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.identity.infrastructure.adapter.Qianchuan401ReplayInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QianchuanRestClientRetryTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesThreeTimesOn429ThenSuccess() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        start(ex -> {
            int n = hits.incrementAndGet();
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            if (n <= 3) {
                ex.sendResponseHeaders(429, -1);
            } else {
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        QianchuanRestClient client = client();
        String body = client.exchange(RequestSpec.get(url()).build(), String.class);
        assertEquals(4, hits.get());
        assertEquals("{\"ok\":true}", body);
    }

    @Test
    void fourXxDoesNotRetry() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        start(ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(400, -1);
            ex.close();
        });
        QianchuanRestClient client = client();
        assertThrows(BizException.class, () -> client.exchange(RequestSpec.get(url()).build(), String.class));
        assertEquals(1, hits.get());
    }
    @Test void businessThrottleRetriesReadsButNeverReplaysWrites() throws Exception {
        AtomicInteger hits=new AtomicInteger();
        start(ex->{
            int n=hits.incrementAndGet();
            byte[] body=(n<=2?"{\"code\":40100}":"{\"code\":0}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);ex.close();
        });
        assertEquals("{\"code\":0}",client().exchange(RequestSpec.get(url()).build(),String.class));assertEquals(3,hits.get());
        hits.set(0);assertEquals("{\"code\":40100}",client().exchange(RequestSpec.post(url()).json("{}").build(),String.class));assertEquals(1,hits.get());
    }

    @Test void businessErrorLogsParameterHelpWithoutCredentialsOrRequestQuery() throws Exception {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(QianchuanRestClient.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();logger.addAppender(appender);
        String body="{\"code\":40001,\"message\":\"请求参数设置错误，请先参考help_message自查\",\"request_id\":\"request-123\",\"help_message\":\"start_time格式错误 access_token=short-sensitive-token\\nAuthorization: Bearer another-sensitive-token\"}";
        try {
            start(ex->{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();});
            assertEquals(body,client().exchange(RequestSpec.get(url()+"?access_token=query-sensitive-token").header("Access-Token","header-sensitive-token").build(),String.class));
            String logs=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(logs.contains("code=40001"));assertTrue(logs.contains("request_id=request-123"));assertTrue(logs.contains("start_time格式错误"));
            assertFalse(logs.contains("short-sensitive-token"));assertFalse(logs.contains("another-sensitive-token"));assertFalse(logs.contains("query-sensitive-token"));assertFalse(logs.contains("header-sensitive-token"));
        } finally {logger.detachAppender(appender);appender.stop();}
    }

    @Test
    void invalidTokenSaysAccessTokenInvalid() throws Exception {
        start(json("{\"code\":40105,\"message\":\"bad\"}"));
        BizException ex = assertThrows(
                BizException.class, () -> client().exchange(RequestSpec.get(url()).build(), String.class));
        assertEquals(40105, ex.code());
        assertEquals("访问令牌无效", ex.getMessage());
        BizException parsed = assertThrows(
                BizException.class, () -> new OceanJson(new ObjectMapper()).dataOrThrow("{\"code\":40105}", 1406, "报表失败"));
        assertEquals("访问令牌无效", parsed.getMessage());
    }

    @Test
    void expiredTokenSaysAuthorizationExpired() throws Exception {
        start(json("{\"code\":40102,\"message\":\"expired\"}"));
        BizException ex = assertThrows(
                BizException.class, () -> client().exchange(RequestSpec.get(url()).build(), String.class));
        assertEquals(40102, ex.code());
        assertEquals("授权已失效", ex.getMessage());
    }

    @Test
    void replayedInvalidTokenAsksToReauthorize() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        start(ex -> {
            hits.incrementAndGet();
            write(ex, 200, "{\"code\":40105,\"message\":\"bad\"}");
        });
        Qianchuan401ReplayInterceptor interceptor = mock(Qianchuan401ReplayInterceptor.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Qianchuan401ReplayInterceptor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(interceptor);
        when(provider.getObject()).thenReturn(interceptor);
        QianchuanRestClient client = new QianchuanRestClient(
                new ObjectMapper(), millis -> {}, HttpClient.newHttpClient(), provider);
        BizException ex = assertThrows(
                BizException.class,
                () -> client.exchange(RequestSpec.get(url()).replayAccountId("acc-1").build(), String.class));
        assertEquals(1211, ex.code());
        assertEquals("访问令牌无效，请重新授权", ex.getMessage());
        assertEquals(2, hits.get());
    }

    private com.sun.net.httpserver.HttpHandler json(String body) {
        return ex -> write(ex, 200, body);
    }

    private static void write(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/qc", handler);
        server.start();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/qc";
    }

    private QianchuanRestClient client() {
        return new QianchuanRestClient(new ObjectMapper(), millis -> {}, HttpClient.newHttpClient());
    }
}
