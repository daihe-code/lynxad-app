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

class QianchuanMaterialsContractTest {
    final ObjectMapper json=new ObjectMapper();final LynxAdProperties properties=new LynxAdProperties();HttpServer server;LiveAdManagementAdapter adapter;
    final Credential credential=new Credential("fixture",new AdvertiserId("9007199254740993"));final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    final List<String> paths=new ArrayList<>();final List<Map<String,String>> queries=new ArrayList<>();
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",ex->{
            assertEquals("GET",ex.getRequestMethod());assertEquals("fixture",ex.getRequestHeaders().getFirst("Access-Token"));String path=ex.getRequestURI().getPath();paths.add(path);
            var q=new HashMap<String,String>();for(String part:ex.getRequestURI().getRawQuery().split("&")){var pair=part.split("=",2);q.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}queries.add(q);int page=Integer.parseInt(q.get("page"));
            var data=json.createObjectNode();String field=path.contains("ad/material")?"ad_material_infos":path.contains("attributes")?"materials":"list";data.putArray(field).addObject().put("material_id",7694973799313178650L);data.putObject("page_info").put("total_page",path.contains("ad/material")?2:1);
            byte[] bytes=json.createObjectNode().put("code",0).set("data",data).toString().getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();
        });server.start();properties.getQianchuan().getDelivery().setManagementBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/open_api/v1.0/qianchuan/uni_promotion/");adapter=new LiveAdManagementAdapter(new QianchuanRestClient(json,millis->{},HttpClient.newHttpClient()),new OceanJson(json),json,properties);
    }
    @AfterEach void stop() {server.stop(0);}
    @Test void nativeReportSendsMandatoryFilteringCorrectSelectedIdDatesFieldsAndAllPages() throws Exception {var rows=adapter.materialReport(credential,"1878659667855539",today.minusDays(1),today,"CAROUSEL");assertEquals(2,rows.size());assertEquals("2",queries.getLast().get("page"));var q=queries.getFirst();assertEquals("9007199254740993",q.get("advertiser_id"));assertEquals("1878659667855539",q.get("ad_id"));var f=json.readTree(q.get("filtering"));assertEquals("CAROUSEL",f.path("material_type").asText());assertEquals("ALL",f.path("material_status").asText());assertEquals(today.minusDays(1).toString(),f.path("start_date").asText());assertEquals(today.toString(),f.path("end_date").asText());assertTrue(q.get("fields").contains("stat_cost_for_roi2"));assertEquals("/open_api/v1.0/qianchuan/uni_promotion/ad/material/get/",paths.getFirst());}
    @Test void metadataUsesQianchuanNativeIdsWithoutPrecisionLossAndChunks() throws Exception {var ids=new ArrayList<String>();for(int i=0;i<51;i++)ids.add(String.valueOf(7694973799313178650L+i));adapter.materialMetadata(credential,"VIDEO",ids);assertEquals(2,paths.size());assertEquals("/open_api/v1.0/qianchuan/video/get/",paths.getFirst());assertEquals(50,json.readTree(queries.getFirst().get("filtering")).path("material_ids").size());assertEquals(ids.getFirst(),json.readTree(queries.getFirst().get("filtering")).path("material_ids").get(0).asText());}
    @Test void attributeLookupUsesQianchuanAccountAndExactMaterialIds() throws Exception {adapter.materialAttributes(credential,List.of("7694973799313178650"));assertEquals("/open_api/2/file/material_attributes/list/",paths.getFirst());assertEquals("QIANCHUAN",queries.getFirst().get("account_type"));assertEquals("9007199254740993",queries.getFirst().get("account_id"));assertEquals("7694973799313178650",json.readTree(queries.getFirst().get("filtering")).path("material_ids").get(0).asText());}
    @Test void emptyMetadataAndUnsupportedSubtypeNeverListEntireMaterialLibrary() {assertTrue(adapter.materialMetadata(credential,"VIDEO",List.of()).isEmpty());assertTrue(adapter.materialMetadata(credential,"TITLE",List.of("123")).isEmpty());assertTrue(adapter.materialAttributes(credential,List.of()).isEmpty());assertTrue(paths.isEmpty());}
    @Test void titleUsesSupportedStatisticsInsteadOfRejectingExposureAndSettlementFields() throws Exception {adapter.materialReport(credential,"1878659667855539",today,today,"TITLE");var fields=json.readTree(queries.getFirst().get("fields"));assertTrue(fields.toString().contains("stat_cost_for_roi2"));assertFalse(fields.toString().contains("product_show_count_for_roi2"));assertFalse(fields.toString().contains("total_order_settle_amount_for_roi2_1h"));}
    @Test void badTypesIdsAndOverlongDatesAreRejectedBeforeRequest() {assertThrows(BizException.class,()->adapter.materialReport(credential,"1878659667855539",today,today,"AIGC"));assertThrows(BizException.class,()->adapter.materialReport(credential,"x",today,today,"VIDEO"));assertThrows(BizException.class,()->adapter.materialReport(credential,"1878659667855539",today.minusDays(180),today,"VIDEO"));assertTrue(paths.isEmpty());}
}
