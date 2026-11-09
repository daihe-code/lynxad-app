package com.lynx.ad.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
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

    @Test void businessServerFailureRetriesReadsWithLimitButNeverReplaysWrites() throws Exception {
        AtomicInteger hits=new AtomicInteger();
        start(ex->{
            int n=hits.incrementAndGet();
            byte[] body=(n<=2?"{\"code\":50000}":"{\"code\":0}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);ex.close();
        });
        assertEquals("{\"code\":0}",client().exchange(RequestSpec.get(url()).build(),String.class));assertEquals(3,hits.get());
        hits.set(0);assertEquals("{\"code\":50000}",client().exchange(RequestSpec.post(url()).json("{}").build(),String.class));assertEquals(1,hits.get());
        server.stop(0);hits.set(0);
        start(ex->{hits.incrementAndGet();byte[] bytes="{\"code\":50000}".getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();});
        assertEquals("{\"code\":50000}",client().exchange(RequestSpec.get(url()).build(),String.class));assertEquals(4,hits.get());
    }

    @Test void parameterFailureIsNotBlindlyRetriedByTheTransport() throws Exception {
        AtomicInteger hits=new AtomicInteger();
        start(ex->{hits.incrementAndGet();byte[] bytes="{\"code\":400153}".getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();});
        assertEquals("{\"code\":400153}",client().exchange(RequestSpec.get(url()).build(),String.class));assertEquals(1,hits.get());
    }

    @Test void upstreamRpcTimeoutRetriesOnlyReads() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        String failure = """
                {"code":40000,"message":"RPCError{Method:[MGetCarouselMetaInfo] ErrType:[RPC_FAILED] error_code=1204 reason=request timeout}"}
                """;
        String success = "{\"code\":0}";
        start(ex -> {
            byte[] body = (hits.incrementAndGet() == 1 ? failure : success).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        var client = client();
        assertEquals(success, client.exchange(RequestSpec.get(url()).build(), String.class));
        assertEquals(2, hits.get());
        hits.set(0);
        assertEquals(failure, client.exchange(RequestSpec.post(url()).json("{}").build(), String.class));
        assertEquals(1, hits.get());
    }

    @Test void platformHttp401WithoutReplayNeverExpiresLynxLogin() throws Exception {
        AtomicInteger hits=new AtomicInteger();
        start(ex->{hits.incrementAndGet();ex.sendResponseHeaders(401,-1);ex.close();});
        var failure=assertThrows(BizException.class,()->client().exchange(RequestSpec.get(url()).build(),String.class));
        assertEquals(1211,failure.code());assertEquals(1,hits.get());
        assertTrue(failure.getMessage().contains("千川授权"));
    }

    @Test void platformBusinessAuthFailureWithoutReplayUsesAccountRecovery() throws Exception {
        AtomicInteger hits=new AtomicInteger();
        start(ex->{hits.incrementAndGet();byte[] bytes="{\"code\":40102}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();});
        var failure=assertThrows(BizException.class,()->client().exchange(RequestSpec.get(url()).build().withoutReplay(),String.class));
        assertEquals(1211,failure.code());assertEquals(1,hits.get());
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
