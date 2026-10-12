package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.LiveAdManagementAdapter;
import com.lynx.ad.modules.identity.domain.model.Credential;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class QianchuanProductsContractTest {
    final ObjectMapper json=new ObjectMapper();final LynxAdProperties properties=new LynxAdProperties();HttpServer server;LiveAdManagementAdapter adapter;
    final Credential credential=new Credential("fixture-token",new AdvertiserId("9007199254740993"));final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    final List<String> paths=new ArrayList<>();final List<Map<String,String>> queries=new ArrayList<>();JsonNode body;boolean reject;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",ex->{
            assertEquals("fixture-token",ex.getRequestHeaders().getFirst("Access-Token"));String path=ex.getRequestURI().getPath();paths.add(path);var data=json.createObjectNode();
            if("GET".equals(ex.getRequestMethod())) {
                Map<String,String> query=new HashMap<>();for(String part:ex.getRequestURI().getRawQuery().split("&")){var pair=part.split("=",2);query.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}queries.add(query);
                int page=Integer.parseInt(query.get("page"));data.putArray("product_list").addObject().put("product_id",page==1?3841021345144111165L:3841023363610968621L).put("is_del",false);data.putObject("page_info").put("total_page",2);
            } else {assertEquals("POST",ex.getRequestMethod());body=json.readTree(ex.getRequestBody());}
            var response=json.createObjectNode().put("code",reject?40000:0).put("message",reject?"商品删除被拒绝":"OK");response.set("data",data);
            byte[] bytes=response.toString().getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();
        });server.start();properties.getQianchuan().getDelivery().setManagementBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/open_api/v1.0/qianchuan/uni_promotion/");
        adapter=new LiveAdManagementAdapter(new QianchuanRestClient(json,millis->{},HttpClient.newHttpClient()),new OceanJson(json),json,properties);
    }
    @AfterEach void stop() {server.stop(0);}
    @Test void nativeProductRowsUseSelectedIdDateFieldsAndAllPages() throws Exception {
        var rows=adapter.products(credential,"1878659667855539",today.minusDays(1),today);assertEquals(2,rows.size());assertEquals("3841021345144111165",rows.getFirst().path("product_id").asText());
        assertTrue(paths.stream().allMatch(p->p.equals("/open_api/v1.0/qianchuan/uni_promotion/ad/product/get/")));var q=queries.getFirst();assertEquals("9007199254740993",q.get("advertiser_id"));assertEquals("1878659667855539",q.get("ad_id"));assertEquals(today.minusDays(1).toString(),q.get("start_date"));assertEquals(today.toString(),q.get("end_date"));assertEquals("20",q.get("page_size"));assertEquals("2",queries.getLast().get("page"));assertTrue(json.readTree(q.get("fields")).toString().contains("product_cvr_rate_for_roi2"));
    }
    @Test void nativeDeletePreservesLargeNumericIdsAndOnlyDeletesSelectedProduct() {adapter.deleteProduct(credential,"1878659667855539","3841021345144111165");assertEquals("/open_api/v1.0/qianchuan/uni_promotion/ad/product/delete/",paths.getFirst());assertTrue(body.path("advertiser_id").isIntegralNumber());assertEquals("3841021345144111165",body.path("product_ids").get(0).asText());assertEquals(1,body.path("product_ids").size());assertEquals("1878659667855539",body.path("ad_id").asText());}
    @Test void rejectedDeleteDoesNotCountAsAcknowledged() {reject=true;assertThrows(BizException.class,()->adapter.deleteProduct(credential,"1878659667855539","3841021345144111165"));}
    @Test void invalidIdsAndTooLongDateRangeAreRejectedBeforeNativeCall() {assertThrows(BizException.class,()->adapter.deleteProduct(credential,"x","123"));assertThrows(BizException.class,()->adapter.products(credential,"1878659667855539",today.minusDays(180),today));assertTrue(paths.isEmpty());}
}
