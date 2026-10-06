package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.*;
import com.lynx.ad.modules.identity.domain.model.Credential;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class QianchuanManagementContractTest {
    private HttpServer server;
    private final ObjectMapper json=new ObjectMapper();
    private final LynxAdProperties properties=new LynxAdProperties();
    private final List<JsonNode> requests=new ArrayList<>();
    private final Credential credential=new Credential("test-token",new AdvertiserId("9007199254740993"));
    private QianchuanRestClient client() { return new QianchuanRestClient(json, millis->{},HttpClient.newHttpClient()); }
    private LiveAdManagementAdapter adapter() { return new LiveAdManagementAdapter(client(),new OceanJson(json),json,properties); }
    private void start(String response,int status) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            assertEquals("test-token",ex.getRequestHeaders().getFirst("Access-Token"));
            if("POST".equals(ex.getRequestMethod())) requests.add(json.readTree(ex.getRequestBody()));
            byte[] data=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(status,data.length);ex.getResponseBody().write(data);ex.close();
        });server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort()+"/";
        properties.getQianchuan().getDelivery().setAdUpdateUrl(base+"update/");
        properties.getQianchuan().getDelivery().setAdStatusUrl(base+"status/");
        properties.getQianchuan().getDelivery().setManagementBaseUrl(base);
    }
    @AfterEach void stop() { if(server!=null)server.stop(0); }
    @Test void partialUpdatePreservesUnchangedCreativesAndIntegerIds() throws Exception {
        start("{\"code\":0,\"data\":{}}",200);
        adapter().settings(credential,"9007199254740995","自定义\"计划",new BigDecimal("350.12"),null);
        JsonNode body=requests.getFirst();
        assertTrue(body.path("advertiser_id").isIntegralNumber());assertEquals("9007199254740993",body.path("advertiser_id").asText());
        assertEquals("9007199254740995",body.path("ad_id").asText());assertEquals("自定义\"计划",body.path("name").asText());
        assertEquals(new BigDecimal("350.12"),body.path("delivery_setting").path("budget").decimalValue());
        assertEquals(Set.of("ad_id","advertiser_id","name","delivery_setting"),keys(body));
        assertEquals(Set.of("budget"),keys(body.path("delivery_setting")));
    }
    @Test void rootSuccessDoesNotMeanStatusOperationSucceeded() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740995,\"flag\":false,\"error_msg\":\"权限不足\"}]}}",200);
        var adapter=new LiveDeliveryAdapters(client(),properties,new OceanJson(json));
        assertThrows(BizException.class,()->adapter.updateStatus(credential,new QcPlanId("9007199254740995"),PlanAction.DISABLE));
        assertEquals(1,requests.size());
        assertThrows(BizException.class,()->adapter.updateStatus(credential,new QcPlanId("9007199254740995"),PlanAction.FINISH));
        assertEquals(1,requests.size(),"结束不能被误发成删除");
    }
    @Test void uncertainTaskFailureNeverRetriesPost() throws Exception {
        start("{\"code\":500,\"message\":\"uncertain\"}",500);
        assertThrows(BizException.class,()->adapter().control(credential,"9007199254740995","SMART_BOOST","起量",new BigDecimal("150"),new BigDecimal("0.5"),List.of()));
        assertEquals(1,requests.size());
    }
    @Test void taskRequiresAcknowledgedIdAndUsesDedicatedScene() throws Exception {
        start("{\"code\":0,\"data\":{}}",200);
        assertThrows(BizException.class,()->adapter().control(credential,"9007199254740995","MATERIAL_ADD_BUDGET","追投",new BigDecimal("150"),new BigDecimal("1"),List.of("9007199254740997")));
        var body=requests.getFirst();assertEquals("MATERIAL_ADD_BUDGET",body.path("scene").asText());
        assertEquals("VIDEO",body.path("material_type").asText());assertTrue(body.path("material_ids").get(0).isIntegralNumber());
        assertFalse(body.has("creative_setting"));
    }
    @Test void pagingReadsAllPagesAndRejectsMalformedPayload() throws Exception {
        AtomicInteger pages=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            int page=pages.incrementAndGet();
            String response=page<=2?"{\"code\":0,\"data\":{\"ad_material_infos\":[{\"material_id\":"+page+"}],\"page_info\":{\"total_page\":2}}}":"{\"code\":0,\"data\":{}}";
            byte[] data=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close();
        });server.start();properties.getQianchuan().getDelivery().setManagementBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        assertEquals(2,adapter().materials(credential,"1").size());assertEquals(2,pages.get());
        assertThrows(BizException.class,()->adapter().materials(credential,"1"));
    }
    @Test void unknownStatusAndMissingNumbersAreNotConvertedToDeliveryOrZero() throws Exception {
        assertEquals(PlanStatus.UNKNOWN,QianchuanPlanMapping.status("OFFLINE_NEW_REASON"));
        assertEquals(PlanStatus.PAUSED,QianchuanPlanMapping.status("DISABLE"));
        assertNull(QianchuanPlanMapping.number(json.readTree("{}"),"stat_cost"));
        assertEquals(BigDecimal.ZERO,QianchuanPlanMapping.number(json.readTree("{\"stat_cost\":{\"Value\":\"0\"}}"),"stat_cost"));
    }
    private Set<String> keys(JsonNode node) { Set<String> keys=new HashSet<>();node.fieldNames().forEachRemaining(keys::add);return keys; }
}
