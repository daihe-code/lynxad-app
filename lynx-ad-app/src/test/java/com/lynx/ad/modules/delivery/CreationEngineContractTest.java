package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CreationEngineContractTest {
    @Test void generationServerErrorIsAmbiguousAndNeverAutomaticallyReplayed() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();var receivedKey=new AtomicReference<String>();
        server.createContext("/api/v1/capabilities/generation/jobs",e->{calls.incrementAndGet();receivedKey.set(e.getRequestHeaders().getFirst("X-API-Key"));e.getRequestBody().readAllBytes();e.sendResponseHeaders(500,-1);e.close();});server.start();
        try {
            var props=new LynxAdProperties();props.getAi().getReview().setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
            var client=new CreationEngineClient(props,new ObjectMapper(),"test-engine-key");
            assertEquals(8003,assertThrows(BizException.class,()->client.post("/api/v1/capabilities/generation/jobs",java.util.Map.of("store_id","tenant"))).code());
            assertEquals(1,calls.get());
            assertEquals("test-engine-key",receivedKey.get());
        }finally{server.stop(0);}
    }
    @Test void redirectsTimeoutResponsesAndOversizedPostReceiptsAreNotSafeToRepeat()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var status=new AtomicInteger(302);var calls=new AtomicInteger();var large=new java.util.concurrent.atomic.AtomicBoolean();
        server.createContext("/revision",e->{calls.incrementAndGet();e.getRequestBody().readAllBytes();byte[] body=large.get()?new byte[8*1024*1024+1]:"{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);e.sendResponseHeaders(status.get(),body.length);e.getResponseBody().write(body);e.close();});server.start();
        try {
            var props=new LynxAdProperties();props.getAi().getReview().setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());var client=new CreationEngineClient(props,new ObjectMapper(),"test-engine-key");
            for(int code:new int[]{302,408,503}){status.set(code);assertEquals(8003,assertThrows(BizException.class,()->client.post("/revision",java.util.Map.of("store_id","tenant"))).code());}
            status.set(422);assertEquals(8002,assertThrows(BizException.class,()->client.post("/revision",java.util.Map.of("store_id","tenant"))).code());
            status.set(200);large.set(true);assertEquals(8003,assertThrows(BizException.class,()->client.post("/revision",java.util.Map.of("store_id","tenant"))).code());assertEquals(8002,assertThrows(BizException.class,()->client.get("/revision")).code());assertEquals(6,calls.get());
        }finally{server.stop(0);}
    }
}
