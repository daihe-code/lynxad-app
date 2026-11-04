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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QianchuanLibraryStatisticsContractTest {
    final ObjectMapper json=new ObjectMapper();
    final LynxAdProperties properties=new LynxAdProperties();
    final Credential credential=new Credential("fixture-token",new AdvertiserId("9007199254740993"),"account");
    final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    final String mid="7694974220593954879";
    final List<Map<String,String>> queries=new ArrayList<>();
    final List<String> paths=new ArrayList<>();
    HttpServer server;boolean filterable=true,time=true,foreign=false,duplicate=false,outside=false,noRows=false,exclude=false;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            var params=new LinkedHashMap<String,String>();
            for(var item:exchange.getRequestURI().getRawQuery().split("&")){var pair=item.split("=",2);params.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
            queries.add(params);paths.add(exchange.getRequestURI().getPath());
            var data=json.createObjectNode();
            if(exchange.getRequestURI().getPath().endsWith("config/get/")) {
                String topic=json.readTree(params.get("data_topics")).get(0).asText();
                var config=data.putArray("custom_config_datas").addObject().put("data_topic",topic);
                var dims=config.putArray("dimensions");dims.addObject().put("field","material_id").put("filterable",filterable).put("sortable",true).put("is_required",true);
                dims.addObject().put("field",topic.endsWith("IMAGE")?"roi2_material_image_name":"roi2_material_video_name").put("is_required",true);
                dims.addObject().put("field","search_input").put("filter_only",true).put("is_required",true);
                if(time)dims.addObject().put("field","stat_time_day").put("sortable",true);
                var metrics=config.putArray("metrics");
                for(String field:List.of("stat_cost_for_roi2","product_cvr_rate_for_roi2","product_ecpm_for_roi2","product_convert_rate_for_roi2","total_pay_order_gmv_for_roi2","total_cost_per_pay_order_for_roi2","total_prepay_and_pay_order_roi2")) {
                    var metric=metrics.addObject().put("field",field);
                    if(exclude && field.equals("product_cvr_rate_for_roi2"))metric.putArray("exclusion_dims").add("stat_time_day");
                }
            } else {
                boolean daily=params.get("dimensions").contains("stat_time_day");int page=Integer.parseInt(params.get("page"));
                var rows=data.putArray("rows");
                if(!noRows) {
                    var row=rows.addObject();var dimensions=row.putObject("dimensions");dimensions.putObject("material_id").put("Value",foreign?"999":mid);
                    if(daily)dimensions.putObject("stat_time_day").put("Value",(outside?today.plusDays(1):today.minusDays(2-page)).toString());
                    var metrics=row.putObject("metrics");metrics.putObject("stat_cost_for_roi2").put("Value",daily?page*10:100);
                    metrics.putObject("product_cvr_rate_for_roi2").put("Value",daily?page*8:8.11);
                    metrics.putObject("total_pay_order_gmv_for_roi2").put("Value",0);metrics.putObject("total_prepay_and_pay_order_roi2").put("Value","--");
                    if(duplicate)rows.add(row.deepCopy());
                }
                data.putObject("page_info").put("total_page",noRows?0:daily?2:1);
            }
            byte[] body=json.createObjectNode().put("code",0).set("data",data).toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        properties.getQianchuan().getReport().setCustomUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/report/uni_promotion/data/get/");
    }
    @AfterEach void stop(){server.stop(0);}
    LiveAdManagementAdapter adapter(){return new LiveAdManagementAdapter(new QianchuanRestClient(json,millis->{},HttpClient.newHttpClient()),new OceanJson(json),json,properties);}
    @ParameterizedTest @ValueSource(strings={"VIDEO","IMAGE","CAROUSEL"})
    void materialScopedSummaryAndDailyPaginationUseNativeRequiredDimensions(String type) throws Exception {
        var result=adapter().libraryMaterialReport(credential,type,mid,today.minusDays(6),today);
        String topic=switch(type){case "VIDEO"->"SITE_PROMOTION_PRODUCT_POST_DATA_VIDEO";case "IMAGE"->"SITE_PROMOTION_PRODUCT_POST_DATA_IMAGE";default->"ROI2_IMAGE_AGG_MATERIAL_ANALYSIS";};
        assertEquals("[\""+topic+"\"]",queries.getFirst().get("data_topics"));assertEquals(4,queries.size());
        for(var query:queries.subList(1,queries.size())) {
            assertEquals("9007199254740993",query.get("advertiser_id"));
            var filter=json.readTree(query.get("filters"));assertEquals(1,filter.size());assertEquals("material_id",filter.get(0).path("field").asText());
            assertEquals(mid,filter.get(0).path("values").get(0).asText());assertEquals(7,filter.get(0).path("operator").asInt());
            assertFalse(query.get("dimensions").contains("ad_id"));assertFalse(query.get("dimensions").contains("search_input"));
            assertTrue(query.get("dimensions").contains("name"));assertEquals("100",query.get("page_size"));
        }
        assertTrue(result.hasData());assertTrue(result.trendAvailable());assertEquals(100,result.summary().get("cost").intValueExact());
        assertEquals(8.11,result.summary().get("ctr").doubleValue()); // Not an average/sum of daily percentages.
        assertEquals(0,result.summary().get("payment").intValueExact());assertNull(result.summary().get("roi"));assertNull(result.summary().get("cpm"));
        assertEquals(2,result.trend().size());assertEquals(today.minusDays(1),result.trend().getFirst().date());assertEquals("2",queries.getLast().get("page"));
    }
    @Test void unavailableDailyDimensionPreservesSummary(){time=false;var result=adapter().libraryMaterialReport(credential,"VIDEO",mid,today,today);assertEquals(2,queries.size());assertFalse(result.trendAvailable());assertTrue(result.trend().isEmpty());assertEquals(100,result.summary().get("cost").intValueExact());}
    @Test void excludedDailyMetricIsNotRequestedOrInvented(){exclude=true;var result=adapter().libraryMaterialReport(credential,"VIDEO",mid,today.minusDays(6),today);assertTrue(queries.get(1).get("metrics").contains("product_cvr_rate_for_roi2"));assertFalse(queries.get(2).get("metrics").contains("product_cvr_rate_for_roi2"));assertNull(result.trend().getFirst().metrics().get("ctr"));}
    @Test void refusesAccountWideFallbackWhenMaterialCannotBeFiltered(){filterable=false;assertThrows(BizException.class,()->adapter().libraryMaterialReport(credential,"VIDEO",mid,today,today));assertEquals(1,queries.size());}
    @Test void noRowsAreMissingDataRatherThanZeros(){noRows=true;var result=adapter().libraryMaterialReport(credential,"VIDEO",mid,today,today);assertFalse(result.hasData());assertTrue(result.summary().values().stream().allMatch(Objects::isNull));}
    @Test void rejectsForeignMaterialReturnedByProvider(){foreign=true;assertThrows(BizException.class,()->adapter().libraryMaterialReport(credential,"VIDEO",mid,today.minusDays(6),today));}
    @Test void rejectsDuplicateGroupsInsteadOfDoubleCounting(){duplicate=true;assertThrows(BizException.class,()->adapter().libraryMaterialReport(credential,"VIDEO",mid,today.minusDays(6),today));}
    @Test void rejectsOutOfRangeTrendDates(){outside=true;assertThrows(BizException.class,()->adapter().libraryMaterialReport(credential,"VIDEO",mid,today.minusDays(6),today));}
}
