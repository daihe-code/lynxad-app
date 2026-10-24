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

class QianchuanInsightsContractTest {
    HttpServer server;final ObjectMapper json=new ObjectMapper();final LynxAdProperties properties=new LynxAdProperties();
    final Credential credential=new Credential("fixture-token",new AdvertiserId("9007199254740993"));
    final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));final List<Map<String,String>> queries=new ArrayList<>();final List<String> paths=new ArrayList<>();
    boolean filterable=true,hour=true;String guaranteeId="9007199254740995";
    boolean netAmounts=true,excludeHourlyNet=false;
    Map<String,String> query(String text) { Map<String,String> result=new LinkedHashMap<>();for(String item:text.split("&")){var pair=item.split("=",2);result.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}return result; }
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",ex->{
            assertEquals("GET",ex.getRequestMethod());assertEquals("fixture-token",ex.getRequestHeaders().getFirst("Access-Token"));
            String path=ex.getRequestURI().getPath();var query=query(ex.getRequestURI().getRawQuery());paths.add(path);queries.add(query);
            var data=json.createObjectNode();
            if(path.endsWith("config/get/")) {
                var topic=data.putArray("custom_config_datas").addObject();topic.put("data_topic","SITE_PROMOTION_PRODUCT_AD");
                var dims=topic.putArray("dimensions");var ad=dims.addObject().put("field","ad_id").put("filterable",filterable);ad.putObject("filter_config").put("operator",6).put("type",1);
                dims.addObject().put("field","stat_time_day");if(hour)dims.addObject().put("field","stat_time_hour");
                var metrics=topic.putArray("metrics");var netRoi=metrics.addObject().put("name","净成交ROI").put("field","actual_net_roi");
                if(excludeHourlyNet)netRoi.putArray("exclusion_dims").add("stat_time_hour");
                if(netAmounts)metrics.addObject().put("name","净成交金额（元）").put("field","actual_net_amount");
                metrics.addObject().put("name","1小时内退款率").put("field","actual_refund_rate");
                metrics.addObject().put("name","其他指标").put("field","unrelated_field");
            } else if(path.endsWith("data/get/")) {
                var dims=json.readTree(query.get("dimensions"));var row=data.putArray("rows").addObject();row.putObject("dimensions").putObject("ad_id").put("Value","9007199254740995");row.putObject("metrics").put("stat_cost",1);
                boolean trend=dims.size()==2;int page=Integer.parseInt(query.get("page"));
                if(trend)row.withObject("/dimensions").put(dims.get(1).asText(),today+" "+(page==1?"01:00:00":"02:00:00"));
                data.putObject("pagination").put("total_page",trend?2:1);
            } else if(path.endsWith("log_search/")) {data.putArray("logs");data.putObject("page_info").put("total_number",0);}
            else if(path.endsWith("compensate_status/get/"))data.putArray("list").addObject().put("ad_id",guaranteeId).put("compensate_status","DEFAULT");
            byte[] body=json.createObjectNode().put("code",0).set("data",data).toString().getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);ex.close();
        });server.start();String base="http://127.0.0.1:"+server.getAddress().getPort()+"/";
        properties.getQianchuan().getReport().setCustomUrl(base+"report/uni_promotion/data/get/");
        properties.getQianchuan().getDelivery().setOperationLogUrl(base+"tools/log_search/");properties.getQianchuan().getDelivery().setCompensateStatusUrl(base+"ad/compensate_status/get/");
    }
    LiveAdManagementAdapter adapter() { return new LiveAdManagementAdapter(new QianchuanRestClient(json,millis->{},HttpClient.newHttpClient()),new OceanJson(json),json,properties); }
    @AfterEach void stop() { server.stop(0); }
    @Test void nativeConfigurationSelectsActualFieldsAndOperatorAndPaginatesTrend() throws Exception {
        var result=adapter().insightReport(credential,"9007199254740995",today,today);
        assertEquals("/report/uni_promotion/config/get/",paths.getFirst());assertEquals("[\"SITE_PROMOTION_PRODUCT_AD\"]",queries.getFirst().get("data_topics"));
        assertEquals("9007199254740993",queries.getFirst().get("advertiser_id"));var filter=json.readTree(queries.get(1).get("filters")).get(0);
        assertEquals("9007199254740995",filter.path("values").get(0).asText());assertEquals(6,filter.path("operator").asInt());assertEquals(1,filter.path("type").asInt());
        assertEquals("actual_net_roi",result.metricFields().get("netRoi"));assertEquals("stat_time_hour",result.timeDimension());assertEquals(2,result.trend().size());assertEquals("2",queries.getLast().get("page"));
        assertEquals(today+" 00:00:00",queries.get(1).get("start_time"));assertEquals(today+" 23:59:59",queries.get(1).get("end_time"));
    }
    @Test void multiDayReportsUseDayDimension() { var value=adapter().insightReport(credential,"9007199254740995",today.minusDays(6),today);assertEquals("stat_time_day",value.timeDimension()); }
    @Test void calendarUsesDailyDimensionEvenWhenThePeriodIsOneDay() throws Exception {
        var result=adapter().insightReport(credential,"9007199254740995",today,today,true);
        assertEquals("stat_time_day",result.timeDimension());assertEquals("stat_time_day",json.readTree(queries.get(2).get("dimensions")).get(1).asText());
        assertEquals(today+" 00:00:00",queries.get(2).get("start_time"));
    }
    @Test void missingHourDimensionUsesActualDayDimension() { hour=false;assertEquals("stat_time_day",adapter().insightReport(credential,"9007199254740995",today,today).timeDimension()); }
    @Test void unfilterablePlanCannotQueryWholeAccountAsSinglePlan() { filterable=false;assertThrows(BizException.class,()->adapter().insightReport(credential,"9007199254740995",today,today));assertEquals(1,paths.size()); }
    @Test void platformLogsUseExactNumericObjectAndDateBounds() { adapter().operationLogs(credential,"9007199254740995",today,today,2,20);var q=queries.getFirst();assertEquals("AD",q.get("object_type"));assertEquals("9007199254740995",q.get("object_id"));assertEquals("2",q.get("page"));assertEquals("20",q.get("page_size")); }
    @Test void excessiveIntervalAndLogPageSizeAreRejectedBeforeRequest() { assertThrows(BizException.class,()->adapter().operationLogs(credential,"9007199254740995",today.minusDays(180),today,1,20));assertThrows(BizException.class,()->adapter().operationLogs(credential,"9007199254740995",today,today,1,21));assertTrue(paths.isEmpty()); }
    @Test void guaranteeMustMatchSelectedId() { guaranteeId="123";assertThrows(BizException.class,()->adapter().guaranteeDetail(credential,"9007199254740995")); }
    @Test void netListReportDiscoversPlatformFieldsAndQueriesAllPlansInOneReport() throws Exception {
        var result=adapter().netReport(credential,today.minusDays(6),today);
        assertEquals(Map.of("netRoi","actual_net_roi","netGmv","actual_net_amount","refundRate","actual_refund_rate"),result.metricFields());
        var request=queries.getLast();var metrics=json.readTree(request.get("metrics"));
        assertTrue(metrics.toString().contains("actual_net_roi"));assertTrue(metrics.toString().contains("actual_net_amount"));assertTrue(metrics.toString().contains("actual_refund_rate"));
        assertFalse(metrics.toString().contains("unrelated_field"));assertEquals("[\"ad_id\"]",request.get("dimensions"));assertEquals("[]",request.get("filters"));
        assertEquals(today.minusDays(6)+" 00:00:00",request.get("start_time"));assertEquals(2,paths.size());
    }
    @Test void netListDoesNotAskForUnavailableMetricsOrSubstitutePaymentFields() throws Exception {
        netAmounts=false;var result=adapter().netReport(credential,today,today);
        assertFalse(result.metricFields().containsKey("netGmv"));assertFalse(queries.getLast().get("metrics").contains("actual_net_amount"));
        assertTrue(queries.getLast().get("metrics").contains("total_pay_order_gmv_include_coupon_for_roi2"));
    }
    @Test void overviewTrendBatchesPlansAndPaginatesTheSupportedHourlyReport() throws Exception {
        var result=adapter().planTrendReport(credential,today,today,true,false);
        assertEquals("stat_time_hour",result.timeDimension());assertEquals(2,result.rows().size());assertEquals("actual_net_roi",result.metricFields().get("netRoi"));
        assertEquals("[\"ad_id\",\"stat_time_hour\"]",queries.getLast().get("dimensions"));assertEquals("[]",queries.getLast().get("filters"));
        assertEquals(3,paths.size());assertEquals("2",queries.getLast().get("page"));
    }
    @Test void overviewTrendRespectsMetricTimeExclusionsAndDailyFallback() {
        excludeHourlyNet=true;var hourly=adapter().planTrendReport(credential,today,today,true,false);
        assertFalse(hourly.metricFields().containsKey("netRoi"));assertFalse(queries.getLast().get("metrics").contains("actual_net_roi"));
        hour=false;var daily=adapter().planTrendReport(credential,today,today,true,false);
        assertEquals("stat_time_day",daily.timeDimension());assertEquals("actual_net_roi",daily.metricFields().get("netRoi"));assertNotNull(daily.warning());
        assertEquals("stat_time_day",adapter().planTrendReport(credential,today.minusDays(6),today,false,false).timeDimension());
    }
}
