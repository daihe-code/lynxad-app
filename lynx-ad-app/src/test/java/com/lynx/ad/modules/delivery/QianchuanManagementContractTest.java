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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QianchuanManagementContractTest {
    private HttpServer server;
    private final ObjectMapper json=new ObjectMapper();
    private final LynxAdProperties properties=new LynxAdProperties();
    private final List<JsonNode> requests=new ArrayList<>();
    private final List<String> requestPaths=new ArrayList<>();
    private final Credential credential=new Credential("test-token",new AdvertiserId("9007199254740993"));
    private QianchuanRestClient client() { return new QianchuanRestClient(json, millis->{},HttpClient.newHttpClient()); }
    private LiveAdManagementAdapter adapter() { return new LiveAdManagementAdapter(client(),new OceanJson(json),json,properties); }
    private void start(String response,int status) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            assertEquals("test-token",ex.getRequestHeaders().getFirst("Access-Token"));
            if("POST".equals(ex.getRequestMethod())) { requests.add(json.readTree(ex.getRequestBody()));requestPaths.add(ex.getRequestURI().getPath()); }
            byte[] data=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(status,data.length);ex.getResponseBody().write(data);ex.close();
        });server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort()+"/";
        properties.getQianchuan().getDelivery().setAdUpdateUrl(base+"update/");
        properties.getQianchuan().getDelivery().setAdBudgetUpdateUrl(base+"ad/budget/update/");
        properties.getQianchuan().getDelivery().setAdRoiUpdateUrl(base+"ad/roi2_goal/update/");
        properties.getQianchuan().getDelivery().setAdStatusUrl(base+"status/");
        properties.getQianchuan().getDelivery().setManagementBaseUrl(base);
    }
    @AfterEach void stop() { if(server!=null)server.stop(0); }
    @Test void automaticTaskUsesExistingNativeSceneAndExactLargeNumericIds()throws Exception{start("{\"code\":0,\"data\":{\"id\":9007199254740997}}",200);assertEquals("9007199254740997",adapter().automaticControl(credential,"9007199254740995","MATERIAL_ADD_BUDGET","灵投自动_test",new BigDecimal("100"),BigDecimal.ONE,List.of("9007199254740999")));assertTrue(requests.getFirst().path("ad_id").isIntegralNumber());assertTrue(requests.getFirst().path("material_ids").get(0).isIntegralNumber());assertEquals("MATERIAL_ADD_BUDGET",requests.getFirst().path("scene").asText());}
    @Test void automaticContentUsesDedicatedDeleteAndPartialCreativeUpdate()throws Exception{start("{\"code\":0,\"data\":{}}",200);var payload=json.createObjectNode().put("materialId","9007199254740999");adapter().automaticContent(credential,"9007199254740995","MATERIAL_REMOVE",payload);assertEquals("/ad/material/delete/",requestPaths.getFirst());assertTrue(requests.getFirst().path("material_ids").get(0).isIntegralNumber());var add=json.createObjectNode();add.putArray("creatives").addObject().put("product_id",new java.math.BigInteger("9007199254740993")).putArray("video_material").addObject().put("video_id","approved-object");adapter().automaticContent(credential,"9007199254740995","MATERIAL_ADD",add);assertEquals("/update/",requestPaths.get(1));assertFalse(requests.get(1).has("delivery_setting"));assertFalse(requests.get(1).has("name"));assertThrows(com.lynx.ad.common.exception.BizException.class,()->adapter().automaticContent(credential,"9007199254740995","PRODUCT_ADD",add));assertEquals(2,requests.size());}
    @Test void automaticWrite5xxIsNotRetried()throws Exception{start("temporary",503);assertThrows(BizException.class,()->adapter().automaticControl(credential,"9007199254740995","SMART_BOOST","task",new BigDecimal("100"),BigDecimal.ONE,List.of()));assertEquals(1,requests.size());}
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
    @Test void budgetOnlyUsesDedicatedEndpointAndPreservesIntegerIdsAndDecimalBudget() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740995,\"status\":\"SUCCESS\"}]}}",200);
        adapter().settings(credential,"9007199254740995",null,new BigDecimal("400.12"),null);
        assertEquals(List.of("/ad/budget/update/"),requestPaths);
        JsonNode body=requests.getFirst(),item=body.path("update_budget_infos").get(0);
        assertEquals(Set.of("advertiser_id","update_budget_infos"),keys(body));
        assertEquals("9007199254740993",body.path("advertiser_id").asText());assertTrue(body.path("advertiser_id").isIntegralNumber());
        assertEquals(Set.of("ad_id","budget"),keys(item));assertTrue(item.path("ad_id").isIntegralNumber());
        assertEquals("9007199254740995",item.path("ad_id").asText());assertEquals(new BigDecimal("400.12"),item.path("budget").decimalValue());
        assertEquals(1,body.path("update_budget_infos").size());
    }
    @Test void roiOnlyUsesDedicatedEndpointAndKeepsTheNetTransactionOptimizationType() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740995,\"status\":\"SUCCESS\"}]}}",200);
        adapter().settings(credential,"9007199254740995",null,null,new BigDecimal("1.60"),"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI");
        assertEquals(List.of("/ad/roi2_goal/update/"),requestPaths);
        JsonNode body=requests.getFirst(),item=body.path("update_roi2_infos").get(0);
        assertEquals(Set.of("advertiser_id","update_roi2_infos"),keys(body));assertTrue(body.path("advertiser_id").isIntegralNumber());
        assertEquals("9007199254740993",body.path("advertiser_id").asText());assertTrue(item.path("ad_id").isIntegralNumber());assertEquals("9007199254740995",item.path("ad_id").asText());
        assertEquals(Set.of("ad_id","roi2_goal","deep_external_action"),keys(item));assertEquals(0,new BigDecimal("1.60").compareTo(item.path("roi2_goal").decimalValue()));
        assertEquals("AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI",item.path("deep_external_action").asText());assertEquals(1,body.path("update_roi2_infos").size());
    }
    @Test void paymentRoiOptimizationIsNotConvertedToNetTransactionRoi() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":123,\"status\":\"SUCCESS\"}]}}",200);
        adapter().settings(credential,"123",null,null,new BigDecimal("2"),"AD_CONVERT_TYPE_LIVE_PAY_ROI");
        assertEquals("AD_CONVERT_TYPE_LIVE_PAY_ROI",requests.getFirst().path("update_roi2_infos").get(0).path("deep_external_action").asText());
    }
    @Test void rootSuccessWithFailedRoiReturnsThePlatformReasonWithoutRetry() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":123,\"status\":\"FAILED\",\"error_message\":\"ROI修改次数超限\"}]}}",200);
        BizException error=assertThrows(BizException.class,()->adapter().settings(credential,"123",null,null,new BigDecimal("1.6"),"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI"));
        assertTrue(error.getMessage().contains("ROI修改次数超限"));assertEquals(1,requests.size());
    }
    @Test void anotherPlansRoiAcknowledgementCannotConfirmThisPlan() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":124,\"status\":\"SUCCESS\"}]}}",200);
        BizException error=assertThrows(BizException.class,()->adapter().settings(credential,"123",null,null,new BigDecimal("1.6")));
        assertTrue(error.getMessage().contains("勿重复提交"));assertEquals(1,requests.size());
    }
    @Test void missingRoiAcknowledgementCannotReportSuccess() throws Exception {
        start("{\"code\":0,\"data\":{}}",200);
        assertThrows(BizException.class,()->adapter().settings(credential,"123",null,null,new BigDecimal("1.6")));
        assertEquals(1,requests.size());
    }
    @Test void unknownOptimizationTypeIsRejectedBeforeAnyBudgetOrRoiWrite() throws Exception {
        start("{\"code\":0,\"data\":{}}",200);
        assertThrows(BizException.class,()->adapter().settings(credential,"123",null,new BigDecimal("400"),new BigDecimal("1.6"),"NEW_UNSUPPORTED_ROI"));
        assertTrue(requests.isEmpty());
    }
    @Test void combinedBudgetAndRoiUpdateUsesTheTwoDedicatedEndpoints() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":123,\"status\":\"SUCCESS\"}]}}",200);
        adapter().settings(credential,"123",null,new BigDecimal("400"),new BigDecimal("1.6"),"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI");
        assertEquals(List.of("/ad/budget/update/","/ad/roi2_goal/update/"),requestPaths);
        assertEquals(Set.of("advertiser_id","update_budget_infos"),keys(requests.get(0)));assertEquals(Set.of("advertiser_id","update_roi2_infos"),keys(requests.get(1)));
    }
    @Test void partiallyAcceptedCombinedUpdateExplainsTheBudgetAlreadyChanged() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            requests.add(json.readTree(ex.getRequestBody()));requestPaths.add(ex.getRequestURI().getPath());
            String response=requests.size()==1?"{\"code\":0,\"data\":{\"results\":[{\"ad_id\":123,\"status\":\"SUCCESS\"}]}}":"{\"code\":0,\"data\":{\"results\":[{\"ad_id\":123,\"status\":\"FAILED\",\"error_message\":\"ROI不可修改\"}]}}";
            byte[] data=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close();
        });server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        properties.getQianchuan().getDelivery().setAdBudgetUpdateUrl(base+"/budget");properties.getQianchuan().getDelivery().setAdRoiUpdateUrl(base+"/roi");
        BizException error=assertThrows(BizException.class,()->adapter().settings(credential,"123",null,new BigDecimal("400"),new BigDecimal("1.6"),"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI"));
        assertTrue(error.getMessage().contains("预算已接受修改"));assertTrue(error.getMessage().contains("ROI不可修改"));assertEquals(2,requests.size());
    }
    @Test void rootSuccessWithFailedBudgetReportsThePlatformReasonWithoutRetry() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740995,\"status\":\"FAILED\",\"error_message\":\"预算修改次数超限\"}]}}",200);
        BizException error=assertThrows(BizException.class,()->adapter().settings(credential,"9007199254740995",null,new BigDecimal("400"),null));
        assertTrue(error.getMessage().contains("预算修改次数超限"));assertEquals(1,requests.size());
    }
    @Test void anotherPlansBudgetAcknowledgementCannotConfirmThisPlan() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740997,\"status\":\"SUCCESS\"}]}}",200);
        BizException error=assertThrows(BizException.class,()->adapter().settings(credential,"9007199254740995",null,new BigDecimal("400"),null));
        assertTrue(error.getMessage().contains("勿重复提交"));assertEquals(1,requests.size());
    }
    @Test void missingBudgetAcknowledgementCannotReportSuccess() throws Exception {
        start("{\"code\":0,\"data\":{}}",200);
        assertThrows(BizException.class,()->adapter().settings(credential,"9007199254740995",null,new BigDecimal("400"),null));
        assertEquals(1,requests.size());
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
    @Test void taskListsRequireSceneAndPreserveLargeTaskAndMaterialIdsAcrossPages() throws Exception {
        List<Map<String,String>> calls=new ArrayList<>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            assertEquals("GET",ex.getRequestMethod());Map<String,String> params=new HashMap<>();
            for(String pair:ex.getRequestURI().getRawQuery().split("&")) {var parts=pair.split("=",2);params.put(parts[0],URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}
            calls.add(params);String scene=params.get("scene");
            assertTrue(Set.of("SMART_BOOST","MATERIAL_ADD_BUDGET").contains(scene));
            assertEquals("9007199254740993",params.get("advertiser_id"));assertEquals("9007199254740995",params.get("ad_id"));
            String response="{\"code\":0,\"data\":{\"task_list\":[{\"id\":9007199254740997,\"task_status\":\"OFFLINE_TIME\",\"material_list\":[{\"material_id\":7694973799313178650}]}],\"page_info\":{\"total_page\":2}}}";
            byte[] body=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);ex.close();
        });server.start();properties.getQianchuan().getDelivery().setManagementBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        var rows=adapter().controls(credential,"9007199254740995");assertEquals(4,rows.size());assertEquals(4,calls.size());
        assertEquals(List.of("SMART_BOOST","SMART_BOOST","MATERIAL_ADD_BUDGET","MATERIAL_ADD_BUDGET"),calls.stream().map(p->p.get("scene")).toList());
        assertEquals(List.of("1","2","1","2"),calls.stream().map(p->p.get("page")).toList());
        assertTrue(rows.getFirst().path("id").isTextual());assertEquals("9007199254740997",rows.getFirst().path("id").asText());
        assertTrue(rows.getFirst().path("material_list").get(0).path("material_id").isTextual());assertEquals("7694973799313178650",rows.getFirst().path("material_list").get(0).path("material_id").asText());
        assertEquals("MATERIAL_ADD_BUDGET",rows.getLast().path("scene").asText());
        assertThrows(BizException.class,()->adapter().controls(credential,"9007199254740995","SEARCH"));assertEquals(4,calls.size());
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
    @Test void taskStatisticsRequestSelectedDatesAndTaskMetricsOnEveryPage() throws Exception {
        List<Map<String,String>> calls=new ArrayList<>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            Map<String,String> params=new HashMap<>();
            for(String pair:ex.getRequestURI().getRawQuery().split("&")) {var parts=pair.split("=",2);params.put(parts[0],URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}
            calls.add(params);
            String response="{\"code\":0,\"data\":{\"task_list\":[{\"id\":7694973799313178650,\"create_time\":\"2026-10-10 23:39:33\",\"budget\":100,\"duration\":5,\"metrics\":{\"show_cnt_for_roi2_assist\":0,\"stat_cost_for_roi2_assist\":12.34,\"ctr_for_roi2_assist\":null}}],\"page_info\":{\"total_page\":2}}}";
            byte[] body=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);ex.close();
        });server.start();properties.getQianchuan().getDelivery().setManagementBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        var end=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));var start=end.minusDays(179);
        var rows=adapter().controls(credential,"9007199254740995","MATERIAL_ADD_BUDGET",start,end);
        assertEquals(2,calls.size());assertEquals(2,rows.size());
        for(var params:calls) {
            assertEquals(start+" 00:00:00",params.get("start_time"));assertEquals(end+" 23:59:59",params.get("end_time"));
            assertEquals("MATERIAL_ADD_BUDGET",params.get("scene"));assertEquals("9007199254740995",params.get("ad_id"));assertEquals("9007199254740993",params.get("advertiser_id"));
            var fields=json.readTree(params.get("fields"));assertEquals(12,fields.size());
            assertTrue(fields.toString().contains("show_cnt_for_roi2_assist"));assertTrue(fields.toString().contains("total_prepay_and_pay_settle_roi2_1h_assist"));
        }
        assertTrue(rows.getFirst().path("id").isTextual());assertEquals("7694973799313178650",rows.getFirst().path("id").asText());
        assertEquals(0,rows.getFirst().path("metrics").path("show_cnt_for_roi2_assist").asInt());
        assertEquals(new BigDecimal("12.34"),rows.getFirst().path("metrics").path("stat_cost_for_roi2_assist").decimalValue());
        assertTrue(rows.getFirst().path("metrics").path("ctr_for_roi2_assist").isNull());
        assertThrows(BizException.class,()->adapter().controls(credential,"9007199254740995","SMART_BOOST",start.minusDays(1),end));
        assertThrows(BizException.class,()->adapter().controls(credential,"9007199254740995","SMART_BOOST",start,end.plusDays(1)));
        assertThrows(BizException.class,()->adapter().controls(credential,"9007199254740995","SEARCH",start,end));
        assertEquals(2,calls.size(),"无效日期和未接入工具不能调用平台");
    }
    @Test void unknownStatusAndMissingNumbersAreNotConvertedToDeliveryOrZero() throws Exception {
        assertEquals(PlanStatus.UNKNOWN,QianchuanPlanMapping.status("OFFLINE_NEW_REASON"));
        assertEquals(PlanStatus.PAUSED,QianchuanPlanMapping.status("DISABLE"));
        assertNull(QianchuanPlanMapping.number(json.readTree("{}"),"stat_cost"));
        assertEquals(BigDecimal.ZERO,QianchuanPlanMapping.number(json.readTree("{\"stat_cost\":{\"Value\":\"0\"}}"),"stat_cost"));
    }
    @Test void deletionPostsOnlySelectedIntegerIdAndRequiresItsBooleanAcknowledgement() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740995,\"flag\":true}]}}",200);
        new LiveDeliveryAdapters(client(),properties,new OceanJson(json)).updateStatus(credential,new QcPlanId("9007199254740995"),PlanAction.DELETE);
        var body=requests.getFirst();assertEquals("/status/",requestPaths.getFirst());
        assertEquals(Set.of("advertiser_id","ad_ids","opt_status"),keys(body));assertEquals("DELETE",body.path("opt_status").asText());
        assertTrue(body.path("advertiser_id").isIntegralNumber());assertEquals("9007199254740993",body.path("advertiser_id").asText());
        assertEquals(1,body.path("ad_ids").size());assertTrue(body.path("ad_ids").get(0).isIntegralNumber());
        assertEquals("9007199254740995",body.path("ad_ids").get(0).asText());assertEquals(1,requests.size());
    }
    @Test void rejectedDeletionPreservesPlatformReasonAndDoesNotRetry() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740995,\"flag\":false,\"error\":{\"error_message\":\"无删除权限\"}}]}}",200);
        var error=assertThrows(BizException.class,()->new LiveDeliveryAdapters(client(),properties,new OceanJson(json))
                .updateStatus(credential,new QcPlanId("9007199254740995"),PlanAction.DELETE));
        assertEquals("无删除权限",error.getMessage());assertEquals(1,requests.size());
    }
    @Test void wrongIdAcknowledgementCannotConfirmDeletion() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740997,\"flag\":true}]}}",200);
        assertThrows(BizException.class,()->new LiveDeliveryAdapters(client(),properties,new OceanJson(json))
                .updateStatus(credential,new QcPlanId("9007199254740995"),PlanAction.DELETE));assertEquals(1,requests.size());
    }
    @Test void stringFlagCannotConfirmDeletion() throws Exception {
        start("{\"code\":0,\"data\":{\"results\":[{\"ad_id\":9007199254740995,\"flag\":\"true\"}]}}",200);
        assertThrows(BizException.class,()->new LiveDeliveryAdapters(client(),properties,new OceanJson(json))
                .updateStatus(credential,new QcPlanId("9007199254740995"),PlanAction.DELETE));assertEquals(1,requests.size());
    }
    @Test void planListSendsRequiredFieldsAndDatesAndReadsBothBidModesAcrossPages() throws Exception {
        List<Map<String,String>> queries=new ArrayList<>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            Map<String,String> params=new HashMap<>();
            for(String part:ex.getRequestURI().getRawQuery().split("&")) {
                String[] pair=part.split("=",2);params.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
            }
            queries.add(params);
            int page=Integer.parseInt(params.get("page"));
            boolean custom=params.get("filtering").contains("SMART_BID_CUSTOM");
            String response="{\"code\":0,\"data\":{\"ad_list\":[{\"ad_info\":{\"id\":"+(custom?"9007199254740999":page==1?"9007199254740995":"9007199254740997")+",\"name\":\"平台计划\",\"status\":\"DISABLE\",\"budget\":300}}],\"page_info\":{\"total_page\":"+(custom?1:2)+"}}}";
            byte[] data=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close();
        });server.start();
        properties.getQianchuan().getDelivery().setAdListUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        var rows=new LiveDeliveryAdapters(client(),properties,new OceanJson(json)).list(credential,null);
        assertEquals(3,rows.size());assertEquals("9007199254740995",rows.getFirst().qcPlanId());
        var day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        for(var params:queries) {
            assertEquals("9007199254740993",params.get("advertiser_id"));
            assertEquals(day+" 00:00:00",params.get("start_time"));assertEquals(day+" 23:59:59",params.get("end_time"));
            assertEquals("[\"stat_cost\"]",params.get("fields"));assertEquals("VIDEO_PROM_GOODS",params.get("marketing_goal"));
        }
        assertEquals(SmartBidType.CUSTOM,rows.getLast().bidType());
    }
    @Test void reportSendsRequiredEmptyFiltersAndStableOrder() throws Exception {
        var query=new java.util.concurrent.atomic.AtomicReference<Map<String,String>>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            Map<String,String> params=new HashMap<>();
            for(String part:ex.getRequestURI().getRawQuery().split("&")) {
                String[] pair=part.split("=",2);params.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
            }
            query.set(params);byte[] data="{\"code\":0,\"data\":{\"rows\":[],\"page_info\":{\"total_page\":0}}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close();
        });server.start();properties.getQianchuan().getReport().setCustomUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        assertTrue(adapter().report(credential,java.time.LocalDate.of(2026,10,8)).isEmpty());
        assertEquals("[]",query.get().get("filters"));assertEquals("ad_id",json.readTree(query.get().get("order_by")).get(0).path("field").asText());
        assertEquals("[\"ad_id\"]",query.get().get("dimensions"));assertEquals("2026-10-08 00:00:00",query.get().get("start_time"));
    }
    @Test void rangeReportUsesInclusive180DayBoundsAndConsumesAllPages() throws Exception {
        List<Map<String,String>> queries=new ArrayList<>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            Map<String,String> params=new HashMap<>();
            for(String part:ex.getRequestURI().getRawQuery().split("&")) {
                String[] pair=part.split("=",2);params.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
            }
            queries.add(params);
            String response="{\"code\":0,\"data\":{\"rows\":[{\"dimensions\":{\"ad_id\":{\"Value\":"+params.get("page")+"}},\"metrics\":{\"stat_cost\":0}}],\"page_info\":{\"total_page\":2}}}";
            byte[] data=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close();
        });server.start();properties.getQianchuan().getReport().setCustomUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        var end=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));var start=end.minusDays(179);
        assertEquals(2,adapter().report(credential,start,end).size());assertEquals(2,queries.size());
        for(var query:queries) {
            assertEquals(start+" 00:00:00",query.get("start_time"));assertEquals(end+" 23:59:59",query.get("end_time"));
            assertEquals("[\"ad_id\"]",query.get("dimensions"));assertEquals("SITE_PROMOTION_PRODUCT_AD",query.get("data_topic"));
        }
        assertThrows(BizException.class,()->adapter().report(credential,start.minusDays(1),end));assertEquals(2,queries.size());
    }
    @ParameterizedTest @ValueSource(strings={"PAUSE","ENABLE","DISABLE","DELETE"})
    void controlActionsUseDedicatedEndpointWithExactNumericIdsAndExplicitAcknowledgement(String operation) throws Exception {
        start("{\"code\":0,\"data\":{\"task_ids\":[1878743575303385],\"errors\":[]}}",200);
        adapter().controlStatus(credential,"1878743575303385",operation);
        assertEquals(List.of("/ad/control_task/status/update/"),requestPaths);var body=requests.getFirst();
        assertEquals(Set.of("advertiser_id","task_ids","opt_type"),keys(body));assertEquals(operation,body.path("opt_type").asText());
        assertEquals("9007199254740993",body.path("advertiser_id").asText());assertTrue(body.path("advertiser_id").isIntegralNumber());
        assertEquals(1,body.path("task_ids").size());assertTrue(body.path("task_ids").get(0).isIntegralNumber());assertEquals("1878743575303385",body.path("task_ids").get(0).asText());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"task_ids\":[1878743575303386]}","{\"task_ids\":[\"1878743575303385\"]}"})
    void missingOrOtherTaskAcknowledgementNeverConfirmsActionOrRetries(String data) throws Exception {
        start("{\"code\":0,\"data\":"+data+"}",200);
        var error=assertThrows(BizException.class,()->adapter().controlStatus(credential,"1878743575303385","PAUSE"));
        assertTrue(error.getMessage().contains("勿重复提交"));assertEquals(1,requests.size());
    }
    @Test void taskFailureOverridesSuccessIdsAndSurfacesNativeReason() throws Exception {
        start("{\"code\":0,\"data\":{\"task_ids\":[1878743575303385],\"errors\":[{\"task_id\":1878743575303385,\"error_message\":\"任务已结束，无法暂停\"}]}}",200);
        var error=assertThrows(BizException.class,()->adapter().controlStatus(credential,"1878743575303385","PAUSE"));
        assertTrue(error.getMessage().contains("任务已结束，无法暂停"));assertEquals(1,requests.size());
    }
    @Test void deletedTasksAreRemovedEvenIfThePlatformStillIncludesTheirRows() throws Exception {
        start("{\"code\":0,\"data\":{\"task_list\":[{\"id\":1878743575303385,\"task_status\":\"DELETED\"},{\"id\":1878743133481112,\"task_status\":\"FROZEN\"}],\"page_info\":{\"total_page\":1}}}",200);
        var rows=adapter().controls(credential,"1878663839175696","SMART_BOOST");assertEquals(1,rows.size());assertEquals("1878743133481112",rows.getFirst().path("id").asText());
    }
    @Test void taskReportUsesConfiguredPhysicalPlanTaskAndSceneFiltersAndExcludesUnsupportedHourlyMetrics() throws Exception {
        List<Map<String,String>> queries=new ArrayList<>();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            Map<String,String> params=new HashMap<>();for(String part:ex.getRequestURI().getRawQuery().split("&")) {String[] pair=part.split("=",2);params.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}queries.add(params);
            String response=ex.getRequestURI().getPath().contains("config") ? """
              {"code":0,"data":{"custom_config_datas":[{"data_topic":"SITE_PROMOTION_PRODUCT_POST_ASSIST_TASK","dimensions":[
                {"field":"assist_aid","filterable":true,"filter_config":{"operator":7,"type":1}},
                {"field":"ad_id","filter_only":true,"filterable":true,"filter_config":{"operator":7,"type":2}},
                {"field":"assist_task_scene","filterable":true,"filter_config":{"operator":7,"type":1}},
                {"field":"stat_time_day"},{"field":"stat_time_hour"}],"metrics":[
                {"field":"stat_cost_for_roi2_assist"},{"field":"total_pay_order_gmv_for_roi2_assist"},
                {"field":"total_prepay_and_pay_settle_roi2_1h_assist","exclusion_dims":["stat_time_hour"]}]}]}}
              """ : "{\"code\":0,\"data\":{\"rows\":[],\"page_info\":{\"total_page\":1}}}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();
        });server.start();properties.getQianchuan().getReport().setCustomUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/uni_promotion/data/get/");
        var day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));var result=adapter().controlReport(credential,"1878663839175696","1878743575303385","SMART_BOOST",day.minusDays(6),day,"HOUR");
        assertEquals(3,queries.size());assertEquals("[\"SITE_PROMOTION_PRODUCT_POST_ASSIST_TASK\"]",queries.getFirst().get("data_topics"));
        assertEquals("stat_time_hour",result.timeDimension());assertEquals(3,result.metricFields().size());assertTrue(result.trend().isEmpty());
        for(var q:queries.subList(1,3)) {
            var filters=json.readTree(q.get("filters"));assertEquals(3,filters.size());
            assertEquals("ad_id",filters.get(0).path("field").asText());assertEquals(2,filters.get(0).path("type").asInt());assertEquals("1878663839175696",filters.get(0).path("values").get(0).asText());
            assertEquals("assist_aid",filters.get(1).path("field").asText());assertEquals("1878743575303385",filters.get(1).path("values").get(0).asText());
            assertEquals("assist_task_scene",filters.get(2).path("field").asText());assertEquals("1",filters.get(2).path("values").get(0).asText());assertEquals(7,filters.get(2).path("operator").asInt());
            assertEquals(day.minusDays(6)+" 00:00:00",q.get("start_time"));assertEquals(day+" 23:59:59",q.get("end_time"));assertTrue(q.get("metrics").contains("total_pay_order_gmv_for_roi2_assist"));assertFalse(q.get("dimensions").contains("ad_id"));
        }
        assertTrue(queries.get(1).get("metrics").contains("total_prepay_and_pay_settle_roi2_1h_assist"));assertFalse(queries.get(2).get("metrics").contains("total_prepay_and_pay_settle_roi2_1h_assist"));
        assertThrows(BizException.class,()->adapter().controlReport(credential,"1878663839175696","1878743575303385","SMART_BOOST",day.minusDays(7),day,"HOUR"));assertEquals(3,queries.size());
        adapter().controlReport(credential,"1878663839175696","1878743575303385","MATERIAL_ADD_BUDGET",day.minusDays(179),day,"DAY");
        assertEquals(6,queries.size());assertTrue(queries.get(5).get("dimensions").contains("stat_time_day"));assertEquals("2",json.readTree(queries.get(4).get("filters")).get(2).path("values").get(0).asText());
        assertThrows(BizException.class,()->adapter().controlReport(credential,"1878663839175696","1878743575303385","SMART_BOOST",day.minusDays(180),day,"DAY"));assertEquals(6,queries.size());
    }
    private Set<String> keys(JsonNode node) { Set<String> keys=new HashSet<>();node.fieldNames().forEachRemaining(keys::add);return keys; }
}
