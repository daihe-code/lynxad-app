package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.MonitoringEngineClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.*;

class MonitoringEngineClientTest {
    final ObjectMapper json=new ObjectMapper();HttpServer server;MonitoringEngineClient client;
    final AtomicReference<JsonNode> received=new AtomicReference<>();final AtomicReference<String> serviceKey=new AtomicReference<>(),storeId=new AtomicReference<>();
    UnaryOperator<ObjectNode> transform=UnaryOperator.identity();int status=200;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/monitoring/external/observations:batch",e->{
            var body=(ObjectNode)json.readTree(e.getRequestBody());received.set(body);serviceKey.set(e.getRequestHeaders().getFirst("X-Monitoring-Key"));storeId.set(e.getRequestHeaders().getFirst("X-Store-ID"));
            var response=body.deepCopy();response.put("source","qianchuan_live");response.put("mode","OBSERVE");response.put("currency","CNY");response.put("execution_allowed",false);response.put("state","WAITING_DATA");response.put("observed_at","2026-10-12T12:30:00+08:00");response.putArray("reasons");response.putArray("recommendations");
            byte[] result=json.writeValueAsBytes(transform.apply(response));e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,result.length);e.getResponseBody().write(result);e.close();
        });server.start();var props=new LynxAdProperties();props.getAi().getReview().setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());client=new MonitoringEngineClient(props,json,"test-monitor-key","test-api-key");
    }
    @AfterEach void stop(){server.stop(0);}
    String payload() {return "{\"request_id\":\"request\",\"store_id\":\"tenant\",\"account_id\":\"account\",\"plan_id\":\"1878663839175696\",\"logical_plan_id\":\"root\",\"policy\":{\"mode\":\"OBSERVE\"},\"policy_version\":1,\"observed_at\":\"2026-10-12T12:30:00+08:00\"}";}
    @Test void serviceCredentialsAndTenantBindingsTravelWithActualHttpRequest() {
        assertEquals("WAITING_DATA",client.submit("tenant",payload()).path("state").asText());assertEquals("tenant",storeId.get());assertEquals("test-monitor-key",serviceKey.get());assertTrue(received.get().path("plan_id").isTextual());
    }
    @Test void wrongOutgoingTenantNeverContactsEngine() {assertThrows(BizException.class,()->client.submit("other",payload()));assertNull(received.get());}
    @Test void mismatchedIncomingScopeAndExecutionPermissionAreRejected() {
        for(String field:new String[]{"store_id","account_id","plan_id","logical_plan_id","request_id","policy_version","currency","source","mode","execution_allowed"}) {
            transform=row->{if(field.equals("policy_version"))row.put(field,2);else if(field.equals("execution_allowed"))row.put(field,true);else row.put(field,"wrong");return row;};
            assertThrows(BizException.class,()->client.submit("tenant",payload()),field);
        }
    }
    @Test void unavailableEngineReturnsRetryableErrorWithoutLeakingItsBody() {
        status=503;var error=assertThrows(BizException.class,()->client.submit("tenant",payload()));assertTrue(error.getMessage().contains("HTTP 503"));assertFalse(error.getMessage().contains("test-monitor-key"));
    }
    @Test void missingCredentialDisablesRealMonitor() {var props=new LynxAdProperties();props.getAi().getReview().setBaseUrl("http://127.0.0.1");assertFalse(new MonitoringEngineClient(props,json,"","").ready());}
    @Test void wrongObservationTimeCannotMakeOldDataLookFresh() {transform=row->{row.put("observed_at","2026-10-11T12:30:00+08:00");return row;};assertThrows(BizException.class,()->client.submit("tenant",payload()));}
    @Test void automaticPermissionMustMatchTheOutgoingAuthorization(){String auto=payload().replace("OBSERVE","AUTO_LIMITED");assertThrows(BizException.class,()->client.submit("tenant",auto));transform=row->{row.put("mode","AUTO_LIMITED");row.put("execution_allowed",true);return row;};assertTrue(client.submit("tenant",auto).path("execution_allowed").asBoolean());}
}
