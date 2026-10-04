package com.lynx.ad.aiclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.aiclient.live.LiveVideoGenClient;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VideoGenPollTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void pollLoopsInsideThisProcess() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", ex -> {
            int n = hits.incrementAndGet();
            String json = n < 3
                    ? "{\"status\":\"RUNNING\"}"
                    : "{\"status\":\"SUCCESS\",\"url\":\"\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        LynxAdProperties props = new LynxAdProperties();
        props.getAi().getKeling().setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        props.getAi().getKeling().setAccessKey("ak");
        props.getAi().getKeling().setSecret("sk");
        props.getAi().getLlm().setApiKey("k");
        props.getAi().getKeling().setPollMs(1);
        props.getAi().getKeling().setMaxPoll(10);
        LiveVideoGenClient client = new LiveVideoGenClient(
                props, new QianchuanRestClient(new ObjectMapper(), ms -> {}, HttpClient.newHttpClient()), new ObjectMapper());
        VideoJobStatus st = client.poll("job-9");
        assertEquals("SUCCESS", st.status());
        assertTrue(hits.get() >= 3);
        assertFalse(st.url() != null && st.url().contains("kling"));
    }

    private static void assertTrue(boolean v) {
        org.junit.jupiter.api.Assertions.assertTrue(v);
    }
}
