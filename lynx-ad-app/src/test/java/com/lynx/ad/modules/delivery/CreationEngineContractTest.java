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
import org.junit.jupiter.api.Test;

class CreationEngineContractTest {
    @Test void generationServerErrorIsAmbiguousAndNeverAutomaticallyReplayed() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
        server.createContext("/api/v1/capabilities/generation/jobs",e->{calls.incrementAndGet();e.getRequestBody().readAllBytes();e.sendResponseHeaders(500,-1);e.close();});server.start();
        try {
            var props=new LynxAdProperties();props.getAi().getReview().setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
            var client=new CreationEngineClient(props,new ObjectMapper());
            assertEquals(8003,assertThrows(BizException.class,()->client.post("/api/v1/capabilities/generation/jobs",java.util.Map.of("store_id","tenant"))).code());
            assertEquals(1,calls.get());
        }finally{server.stop(0);}
    }
}
