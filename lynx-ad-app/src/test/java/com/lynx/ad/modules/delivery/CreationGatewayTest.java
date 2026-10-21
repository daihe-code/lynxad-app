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
}
