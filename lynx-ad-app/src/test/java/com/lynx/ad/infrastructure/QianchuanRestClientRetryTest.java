package com.lynx.ad.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
