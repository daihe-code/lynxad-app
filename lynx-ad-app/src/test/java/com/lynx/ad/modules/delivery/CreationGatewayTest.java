package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreationGatewayTest {
    HttpServer server;final ObjectMapper json=new ObjectMapper();final LynxAdProperties p=new LynxAdProperties();
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);final OauthAccountRepository accounts=mock(OauthAccountRepository.class);
    final Credential credential=new Credential("test-token",new AdvertiserId("9007199254740993"),"a");final AtomicInteger requests=new AtomicInteger();
    CreationQianchuanGateway gateway;String query;
    @BeforeEach void prepare(){
        p.getMock().setQianchuan(false);var bound=mock(OauthAccount.class);when(bound.platform()).thenReturn(Platform.QIANCHUAN);when(bound.status()).thenReturn("ACTIVE");when(bound.awemes()).thenReturn(List.of(new Aweme(new AwemeId("123"),"测试",null,AwemeType.SELF,true,null)));
        when(accounts.findById(new TenantId("t"),new AccountId("a"))).thenReturn(Optional.of(bound));when(credentials.getUsableCredential(any(),any())).thenReturn(credential);
        gateway=new CreationQianchuanGateway(new QianchuanRestClient(json,millis->{},HttpClient.newHttpClient()),new OceanJson(json),json,p,credentials,accounts);
    }
    void serve(String response,int status) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{requests.incrementAndGet();query=e.getRequestURI().getRawQuery();assertEquals("test-token",e.getRequestHeaders().getFirst("Access-Token"));byte[] bytes=response.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
        String url="http://127.0.0.1:"+server.getAddress().getPort()+"/";p.getQianchuan().getCatalog().setProductGetUrl(url);p.getQianchuan().getDelivery().setAdCreateUrl(url);
    }
    @AfterEach void close(){if(server!=null)server.stop(0);}
    @Test void productPagingAndAbsentPricesRemainReal() throws Exception {
        serve("{\"code\":0,\"data\":{\"product_list\":[{\"id\":9007199254740995,\"name\":\"真实商品\",\"img\":\"https://example.test/a.jpg\"}],\"page_info\":{\"total_number\":42}}}",200);
        var page=gateway.products(new TenantId("t"),"a","123",2,10,"商品","ALL",true,null);
        assertEquals(42,page.total());assertEquals("9007199254740995",page.items().getFirst().qcProductId());assertNull(page.items().getFirst().price());
        assertTrue(query.contains("page=2"));var decoded=URLDecoder.decode(query,StandardCharsets.UTF_8);assertTrue(decoded.contains("create_roi2_limit_product"));assertTrue(decoded.contains("商品"));
    }
    @Test void recommendationScanDoesNotFanOutSkuPriceRequests() throws Exception {
        serve("{\"code\":0,\"data\":{\"product_list\":[{\"id\":9007199254740995,\"name\":\"真实商品\",\"price\":800}],\"page_info\":{\"total_number\":42}}}",200);
        var prices=mock(com.lynx.ad.modules.catalog.application.ProductPriceService.class);gateway.productPrices(prices);
        var result=gateway.recommendationCandidates(new TenantId("t"),"a","123",1,100);
        assertEquals("9007199254740995",result.items().getFirst().qcProductId());assertEquals(800,result.items().getFirst().price().intValueExact());
        verifyNoInteractions(prices);assertEquals(1,requests.get());
        assertTrue(URLDecoder.decode(query,StandardCharsets.UTF_8).contains("\"tab\":\"ALL\""));
    }
    Map<String,String> queryParams() {
        var result=new LinkedHashMap<String,String>();
        for(String pair:query.split("&")){var parts=pair.split("=",2);result.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}
        return result;
    }
    @Test void newAndBreakthroughProductsUseNativeCursorWithoutUnsupportedMerchantFilters() throws Exception {
        serve("""
            {"code":0,"data":{"product_list":[{"id":1001,"name":"分类商品"}],
             "page_info":{"cursor":9007199254740993,"has_more":true}}}
            """,200);
        for(String tab:List.of("NEW_PRODUCT","BREAKTHROUGH_PRODUCT")) {
            var first=gateway.products(new TenantId("t"),"a","123",1,10,"新品",tab,true,null);
            var params=queryParams();var filters=json.readTree(params.get("filtering"));
            assertEquals(tab,filters.path("tab").asText());assertEquals("新品",filters.path("product_name").asText());
            assertFalse(filters.has("create_roi2_limit_product"));assertEquals("0",params.get("cursor"));
            assertFalse(params.containsKey("page"));assertFalse(params.containsKey("page_size"));
            assertEquals("1001",first.items().getFirst().productId());assertTrue(first.hasMore());
            assertEquals("9007199254740993",first.nextCursor());
        }
        server.stop(0);
        serve("{\"code\":0,\"data\":{\"product_list\":[{\"id\":1002}],\"page_info\":{\"cursor\":0}}}",200);
        var last=gateway.products(new TenantId("t"),"a","123",2,10,null,"NEW_PRODUCT",false,null,false,"9007199254740993");
        assertEquals("9007199254740993",queryParams().get("cursor"));assertFalse(last.hasMore());assertNull(last.nextCursor());
        assertEquals("1002",last.items().getFirst().productId());
    }
    @Test void searchTrendRetainsNumberedPagingAndDoesNotSendUnpromotedFilter() throws Exception {
        serve("{\"code\":0,\"data\":{\"product_list\":[],\"page_info\":{\"total_number\":0}}}",200);
        var result=gateway.products(new TenantId("t"),"a","123",2,30,null,"SEARCH_TREND",true,null);
        var params=queryParams();var filters=json.readTree(params.get("filtering"));
        assertEquals("SEARCH_TREND",filters.path("tab").asText());assertFalse(filters.has("create_roi2_limit_product"));
        assertEquals("2",params.get("page"));assertEquals("30",params.get("page_size"));assertFalse(params.containsKey("cursor"));
        assertEquals(0,result.total());assertTrue(result.items().isEmpty());
    }
    @Test void categoryEmptyResponseIsSuccessfulButPlatformRejectionIsNotSilentlyEmpty() throws Exception {
        serve("{\"code\":0,\"data\":{\"product_list\":[],\"page_info\":{\"cursor\":0,\"page\":0,\"page_size\":0}}}",200);
        var empty=gateway.products(new TenantId("t"),"a","123",1,10,null,"NEW_PRODUCT",false,null);
        assertTrue(empty.items().isEmpty());assertFalse(empty.hasMore());assertNull(empty.nextCursor());
        server.stop(0);
        serve("{\"code\":40000,\"message\":\"分类请求被平台拒绝\"}",200);
        var failure=assertThrows(BizException.class,()->gateway.products(new TenantId("t"),"a","123",1,10,null,"NEW_PRODUCT",false,null));
        assertTrue(failure.getMessage().contains("分类请求被平台拒绝"));
    }
    @Test void invalidCategoryAndCursorCannotReachThePlatform() {
        assertThrows(BizException.class,()->gateway.products(new TenantId("t"),"a","123",1,10,null,"GOOD_BOOST",true,null));
        for(String cursor:List.of("-1","bad","9223372036854775808","9007199254740993.0","0"))
            assertThrows(BizException.class,()->gateway.products(new TenantId("t"),"a","123",2,10,null,"NEW_PRODUCT",false,null,false,cursor));
        assertThrows(BizException.class,()->gateway.products(new TenantId("t"),"a","123",2,10,null,"NEW_PRODUCT",false,null));
        verifyNoInteractions(credentials);assertEquals(0,requests.get());
    }
    @Test void nonAdvancingCursorCannotRepeatTheSamePageForever() throws Exception {
        serve("{\"code\":0,\"data\":{\"product_list\":[],\"page_info\":{\"cursor\":123,\"has_more\":true}}}",200);
        assertThrows(BizException.class,()->gateway.products(new TenantId("t"),"a","123",2,10,null,"NEW_PRODUCT",false,null,false,"123"));
        assertEquals(1,requests.get());
    }
    @Test void platformSalesPreserveZeroAndDoNotTurnMissingOrInvalidValuesIntoZero() throws Exception {
        serve("""
            {"code":0,"data":{"product_list":[
              {"id":1001,"name":"零销量","sell_num":0,"stock_num":0},
              {"id":1002,"name":"有销量","sell_num":1256},
              {"id":1003,"name":"未提供销量"},
              {"id":1004,"name":"空销量","sell_num":null},
              {"id":1005,"name":"文本零销量","sell_num":"0"},
              {"id":1006,"name":"异常负数","sell_num":-1},
              {"id":1007,"name":"异常文本","sell_num":"unknown"},
              {"id":1008,"name":"异常小数","sell_num":1.5},
              {"id":1009,"name":"超出整数范围","sell_num":"9223372036854775808"}
            ]}}
            """,200);
        var rows=gateway.products(new TenantId("t"),"a","123",1,10,null,"ALL",false,null).items();
        assertEquals(0L,rows.get(0).sales());assertEquals(0L,rows.get(0).stock());assertEquals(1256L,rows.get(1).sales());
        for(int i:new int[]{2,3,5,6,7,8})assertNull(rows.get(i).sales());
        assertEquals(0L,rows.get(4).sales());assertEquals(1,requests.get());
        assertEquals(0,json.valueToTree(rows.getFirst()).path("sales").asLong(-1));
    }
    @Test void salesSurviveDraftDecorationPriceRefreshAndStateRoundTrip() throws Exception {
        serve("{\"code\":0,\"data\":{\"product_list\":[{\"id\":1001,\"name\":\"真实商品\",\"sell_num\":0}]}}",200);
        var product=gateway.products(new TenantId("t"),"a","123",1,10,null,"ALL",false,null).items().getFirst()
            .withDraft(new com.lynx.ad.modules.delivery.domain.model.CreationState.DraftReference("draft","已保存草稿"));
        var prices=mock(com.lynx.ad.modules.catalog.application.ProductPriceService.class);gateway.productPrices(prices);
        when(prices.prices(any(),any(),any(),any(),anyBoolean())).thenReturn(Map.of());
        var refreshed=gateway.refreshPrices(new TenantId("t"),"a","123",List.of(product)).getFirst();
        assertEquals(0L,refreshed.sales());assertEquals(product.draft(),refreshed.draft());
        var restored=json.readValue(json.writeValueAsString(refreshed),com.lynx.ad.modules.delivery.domain.model.CreationState.Product.class);
        assertEquals(refreshed,restored);
        var legacy=json.readValue("{\"productId\":\"1001\",\"qcProductId\":\"1001\",\"name\":\"历史商品\"}",com.lynx.ad.modules.delivery.domain.model.CreationState.Product.class);
        assertNull(legacy.sales());
    }
    @Test void CrossTenantOrForeignAwemeNeverCallsPlatform(){
        assertThrows(BizException.class,()->gateway.credential(new TenantId("other"),"a","123"));assertThrows(BizException.class,()->gateway.credential(new TenantId("t"),"a","999"));verifyNoInteractions(credentials);
    }
    @Test void recoveryReadsSelectedIdsWithoutCreatingPlansAndIncludesUnavailableReasons() throws Exception {
        serve("{\"code\":0,\"data\":{\"product_list\":[{\"id\":123,\"name\":\"审核商品\",\"gray_reason\":[\"审核中\"]},{\"id\":456,\"name\":\"正常商品\"}]}}",200);
        var rows=gateway.restorable(new TenantId("t"),"a","123",List.of("123","456"));
        assertEquals(2,rows.size());assertEquals(List.of("审核中"),rows.getFirst().unavailableReasons());
        assertEquals(1,requests.get());
        String decoded=URLDecoder.decode(query,StandardCharsets.UTF_8);
        assertTrue(decoded.contains("\"product_ids\":[123,456]"));assertFalse(decoded.contains("create_roi2_limit_product"));
    }
    @Test void recoveryRejectsInvalidOrCrossAccountIdsBeforePlatformAccess() {
        for(var ids:List.of(List.<String>of(),List.of("123","123"),List.of("bad"),java.util.stream.IntStream.range(0,11).mapToObj(i->String.valueOf(i+1)).toList()))
            assertThrows(BizException.class,()->gateway.restorable(new TenantId("t"),"a","123",ids));
        assertThrows(BizException.class,()->gateway.restorable(new TenantId("other"),"a","123",List.of("123")));
        assertThrows(BizException.class,()->gateway.restorable(new TenantId("t"),"a","999",List.of("123")));
        verifyNoInteractions(credentials);assertEquals(0,requests.get());
    }
    @Test void AcceptedWithoutIdAndMalformedResponseAreUncertain() throws Exception {
        serve("{\"code\":0,\"data\":{}}",200);assertEquals(1523,assertThrows(BizException.class,()->gateway.create(new TenantId("t"),"a","123",json.createObjectNode())).code());assertEquals(1,requests.get());
    }
    @Test void serverFailureNeverAutomaticallyCreatesTwice() throws Exception {
        serve("{\"code\":50000}",500);assertEquals(1523,assertThrows(BizException.class,()->gateway.create(new TenantId("t"),"a","123",json.createObjectNode())).code());assertEquals(1,requests.get());
    }
    @Test void rejectedPublishRetainsPlatformReasonAndRequestIdWithoutReplayOrSecrets() throws Exception {
        serve("{\"code\":40000,\"message\":\"商品卡标题不能为空 test-token https://private.test/path\\n请检查参数\",\"request_id\":\"20261010181810F28DCC4EC1F5E1ACE2C7\"}",200);
        var failure=assertThrows(BizException.class,()->gateway.create(new TenantId("t"),"a","123",json.createObjectNode()));
        assertEquals(1406,failure.code());assertTrue(failure.getMessage().contains("商品卡标题不能为空"));
        assertTrue(failure.getMessage().contains("40000"));assertTrue(failure.getMessage().contains("20261010181810F28DCC4EC1F5E1ACE2C7"));
        assertFalse(failure.getMessage().contains("test-token"));assertFalse(failure.getMessage().contains("private.test"));assertFalse(failure.getMessage().contains("\n"));assertEquals(1,requests.get());
    }
    @Test void longPlatformReasonFitsDurableFailureColumn() throws Exception {
        serve(json.createObjectNode().put("code",40000).put("message","参数不合法".repeat(200)).put("request_id","r".repeat(100)).toString(),200);
        var failure=assertThrows(BizException.class,()->gateway.create(new TenantId("t"),"a","123",json.createObjectNode()));
        assertTrue(failure.getMessage().length()<=500);assertTrue(failure.getMessage().contains("40000"));assertEquals(1,requests.get());
    }
    @Test void absentPlatformReasonHasExplicitFallback() throws Exception {
        serve("{\"code\":40000}",200);
        var failure=assertThrows(BizException.class,()->gateway.create(new TenantId("t"),"a","123",json.createObjectNode()));
        assertTrue(failure.getMessage().contains("平台未返回具体原因"));assertTrue(failure.getMessage().contains("40000"));assertFalse(failure.getMessage().contains("请求号"));
    }
    @Test void invalidOrOversizedIdIsRejected(){assertThrows(BizException.class,()->CreationQianchuanGateway.numericId("mock-id"));assertThrows(BizException.class,()->CreationQianchuanGateway.numericId("9999999999999999999"));}
    @Test void recommendationFailureKeepsIndependentRoiAndCanBeFetchedAgain() throws Exception {
        var budgetCalls=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{
            var params=URLDecoder.decode(e.getRequestURI().getRawQuery(),StandardCharsets.UTF_8);
            assertTrue(params.contains("product_ids=[9007199254740995]"));
            assertTrue(params.contains("aweme_id=123"));
            boolean budget=e.getRequestURI().getPath().contains("budget");
            if(!budget)assertTrue(params.contains("deep_external_action=AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI"));
            String response=budget && budgetCalls.incrementAndGet()==1
                    ? "{\"code\":40000,\"message\":\"暂时无法计算预算 test-token https://private.test/path\"}"
                    : budget ? "{\"code\":0,\"data\":{\"budget\":100,\"suggested_budget_list\":[50,100,150],\"estimate_trans_cost_list\":[{\"budget\":100,\"min_estimate_roi\":0.19,\"max_estimate_roi\":0.29,\"min_estimate_convert\":6,\"max_estimate_convert\":8}]}}"
                    : "{\"code\":0,\"data\":{\"ecp_roi2_goal\":4.66}}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
        });server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        p.getQianchuan().getDelivery().setSuggestBudgetUrl(base+"/budget/");
        p.getQianchuan().getDelivery().setSuggestRoiUrl(base+"/roi/");
        var first=gateway.recommendations(new TenantId("t"),"a","123",List.of("9007199254740995"));
        assertTrue(first.path("budgetError").asText().contains("暂时无法计算预算"));assertEquals(4.66,first.path("roi").path("ecp_roi2_goal").asDouble());
        assertFalse(first.toString().contains("test-token"));assertFalse(first.toString().contains("private.test"));
        var retried=gateway.recommendations(new TenantId("t"),"a","123",List.of("9007199254740995"));
        assertFalse(retried.has("budgetError"));assertEquals(100,retried.path("budget").path("budget").asInt());
        assertEquals(0.19,retried.path("budget").path("estimate_trans_cost_list").get(0).path("min_estimate_roi").asDouble());
    }
    @Test void successfulEmptyForecastAndRateLimitHaveDifferentStatusCodesAndRequestIds() throws Exception {
        var calls=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{
            boolean budget=e.getRequestURI().getPath().contains("budget");
            String body=budget?(calls.incrementAndGet()==1?"{\"code\":0,\"request_id\":\"empty-quote\",\"data\":{\"budget\":50,\"estimate_trans_cost_list\":[]}}":"{\"code\":40100,\"message\":\"请求频率受限 test-token\",\"request_id\":\"limited-quote\"}")
                :"{\"code\":0,\"data\":{\"ecp_roi2_goal\":4.82}}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
        });server.start();var base="http://127.0.0.1:"+server.getAddress().getPort();p.getQianchuan().getDelivery().setSuggestBudgetUrl(base+"/budget/");p.getQianchuan().getDelivery().setSuggestRoiUrl(base+"/roi/");
        var empty=gateway.recommendations(new TenantId("t"),"a","123",List.of("9007199254740995"));assertEquals("NO_ESTIMATE",empty.path("budgetStatus").asText());assertEquals(0,empty.path("budgetCode").asInt(-1));assertEquals("empty-quote",empty.path("budgetRequestId").asText());assertFalse(empty.has("budgetError"));
        var limited=gateway.recommendations(new TenantId("t"),"a","123",List.of("9007199254740995"));assertEquals("RATE_LIMITED",limited.path("budgetStatus").asText());assertEquals(40100,limited.path("budgetCode").asInt());assertEquals("limited-quote",limited.path("budgetRequestId").asText());assertEquals(4.82,limited.path("roi").path("ecp_roi2_goal").asDouble());assertFalse(limited.toString().contains("test-token"));
    }
    @ParameterizedTest @ValueSource(ints={40110,40130})
    void developerAndCustomerFrequencyLimitsAreNotReportedAsEmptyForecasts(int code)throws Exception {
        serve("{\"code\":"+code+",\"message\":\"Too many requests\",\"request_id\":\"rate-scope\"}",200);
        var endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/";p.getQianchuan().getDelivery().setSuggestBudgetUrl(endpoint);p.getQianchuan().getDelivery().setSuggestRoiUrl(endpoint);
        var r=gateway.recommendations(new TenantId("t"),"a","123",List.of("9007199254740995"));
        assertEquals(code,r.path("budgetCode").asInt());assertEquals("RATE_LIMITED",r.path("budgetStatus").asText());assertEquals("RATE_LIMITED",r.path("roiStatus").asText());assertEquals("rate-scope",r.path("budgetRequestId").asText());assertTrue(r.path("budgetError").asText().contains(code==40110?"开发者":"客户账户"));
    }
}
