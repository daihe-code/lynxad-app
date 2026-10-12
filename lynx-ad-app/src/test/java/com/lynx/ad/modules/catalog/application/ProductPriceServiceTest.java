package com.lynx.ad.modules.catalog.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice;
import com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore;
import com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.Ref;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class ProductPriceServiceTest {
    final String id="3841023363610968621", other="3841021345144111165";
    final TenantId tenant=new TenantId("tenant-a");
    final Credential credential=new Credential("test-token",new AdvertiserId("9007199254740993"),"account-a");
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final LynxAdProperties properties=new LynxAdProperties();
    final AtomicInteger calls=new AtomicInteger();
    final List<String> queries=new ArrayList<>();
    HttpServer server; ProductPriceService prices; QianchuanRestClient client; String response;
    final Instant at=Instant.parse("2026-10-11T14:00:00Z");
    @BeforeEach void setup() throws Exception {
        properties.getMock().setQianchuan(false);
        response=body(id,"17.89","17.89",1789);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{
            assertEquals("GET",e.getRequestMethod()); assertEquals("test-token",e.getRequestHeaders().getFirst("Access-Token"));
            calls.incrementAndGet();queries.add(URLDecoder.decode(e.getRequestURI().getRawQuery(),StandardCharsets.UTF_8));
            String content=e.getRequestURI().getPath().equals("/full/")
                    ? "{\"code\":0,\"data\":{\"product_list\":[{\"id\":"+id+",\"name\":\"手套\",\"gray_reason\":[\"当前不可投\"]}],\"page_info\":{\"total_number\":1}}}"
                    :response;
            byte[] bytes=content.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
        });server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        properties.getQianchuan().getCatalog().setProductPriceUrl(base+"/price/");
        properties.getQianchuan().getCatalog().setProductGetUrl(base+"/full/");
        client=new QianchuanRestClient(json,ms->{},HttpClient.newHttpClient());
        prices=new ProductPriceService(client,new OceanJson(json),json,properties,null);prices.clock(Clock.fixed(at,ZoneOffset.UTC));
    }
    @AfterEach void close(){server.stop(0);}
    String body(String product,String low,String high,int fen){return "{\"code\":0,\"data\":{\"product_list\":[{\"id\":"+product+",\"discount_lower_price\":\""+low+"\",\"discount_higher_price\":"+high+",\"discount_price\":"+fen+",\"market_price\":20}]}}";}
    Map<String,PlatformProductPrice> get(boolean force){return prices.prices(tenant,credential,"123",List.of(new Ref(id,null)),force);}
    @Test void readsYuanBoundsWithoutDividingThemAndKeepsExactIds() {
        var quote=get(false).get(id);
        assertEquals(new BigDecimal("17.89"),quote.minPrice());assertEquals(quote.minPrice(),quote.maxPrice());
        assertEquals("QIANCHUAN_AVAILABLE",quote.source());assertEquals(at,quote.fetchedAt());assertFalse(quote.stale());
        assertTrue(queries.getFirst().contains("\"product_ids\":[3841023363610968621]"));assertTrue(queries.getFirst().contains("aweme_id=123"));
        response=body(id,"524.25","670.50",52425);
        quote=get(true).get(id);assertEquals(new BigDecimal("524.25"),quote.minPrice());assertEquals(new BigDecimal("670.50"),quote.maxPrice());
    }
    @Test void tenMinuteCacheForceRefreshFailureAndRecoveryKeepLastKnownPrice() {
        var first=get(false).get(id);get(false);assertEquals(1,calls.get());
        prices.clock(Clock.fixed(at.plusSeconds(599),ZoneOffset.UTC));get(false);assertEquals(1,calls.get());
        prices.clock(Clock.fixed(at.plusSeconds(600),ZoneOffset.UTC));response="{\"code\":40000,\"message\":\"权限不足\"}";
        var stale=get(false).get(id);assertTrue(stale.stale());assertEquals(first.minPrice(),stale.minPrice());assertEquals(at,stale.fetchedAt());
        get(false);assertEquals(2,calls.get());
        response=body(id,"19.89","19.89",1989);var current=get(true).get(id);assertFalse(current.stale());assertEquals(new BigDecimal("19.89"),current.minPrice());assertEquals(3,calls.get());
    }
    @Test void unavailableIsEmptyInsteadOfZeroAndDoesNotReturnUnrequestedProducts() {
        response=body(other,"107.70","107.70",10770);assertTrue(get(false).isEmpty());
        response="{\"code\":40000}";assertTrue(get(true).isEmpty());
        response="{\"code\":0,\"data\":{\"product_list\":[{\"id\":"+id+"}]}}";assertTrue(get(true).isEmpty());
    }
    @Test void scopeIncludesTenantAccountAwemeAndChannel() {
        get(false);
        prices.prices(new TenantId("tenant-b"),credential,"123",List.of(new Ref(id,null)),false);
        prices.prices(tenant,new Credential("test-token",credential.advertiserId(),"account-b"),"123",List.of(new Ref(id,null)),false);
        prices.prices(tenant,credential,"456",List.of(new Ref(id,null)),false);
        assertEquals(4,calls.get());
        assertTrue(prices.prices(tenant,credential,"123",List.of(new Ref(id,"channel-a")),false).isEmpty());
        assertEquals(5,calls.get());
    }
    @Test void validFenFallbackAndInvalidRangesOrUnknownFields() throws Exception {
        var quote=PlatformProductPrice.available(json.readTree("{\"discount_price\":10770}"),at);
        assertEquals(new BigDecimal("107.70"),quote.minPrice());
        assertNull(PlatformProductPrice.available(json.readTree("{\"discount_lower_price\":100,\"discount_higher_price\":50}"),at));
        assertNull(PlatformProductPrice.available(json.readTree("{\"discount_price\":\"unknown\",\"market_price\":200}"),at));
        assertNull(PlatformProductPrice.available(json.readTree("{\"discount_lower_price\":0,\"discount_price\":0}"),at));
    }
    @Test void restoredSnapshotIsAvailableDuringPlatformOutage() {
        var store=mock(ProductPriceStore.class);var old=new PlatformProductPrice(new BigDecimal("17.89"),new BigDecimal("17.89"),null,"QIANCHUAN_AVAILABLE",at.minusSeconds(1000),false);
        when(store.load(any(),any())).thenReturn(Map.of(new Ref(id,null),old));
        prices=new ProductPriceService(client,new OceanJson(json),json,properties,store);prices.clock(Clock.fixed(at,ZoneOffset.UTC));response="{\"code\":40000}";
        var quote=get(false).get(id);assertEquals(old.minPrice(),quote.minPrice());assertTrue(quote.stale());verify(store,never()).save(any(),any(),any());
    }
    @Test void supplementalPriceDoesNotMakeUnavailableProductSelectable() {
        var accounts=mock(OauthAccountRepository.class);var credentials=mock(QianchuanCredentialFacade.class);var account=mock(OauthAccount.class);
        when(account.platform()).thenReturn(Platform.QIANCHUAN);when(account.status()).thenReturn("ACTIVE");
        when(account.awemes()).thenReturn(List.of(new Aweme(new AwemeId("123"),"账号",null,AwemeType.SELF,true,null)));
        when(accounts.findById(tenant,new AccountId("account-a"))).thenReturn(Optional.of(account));when(credentials.getUsableCredential(tenant,new AccountId("account-a"))).thenReturn(credential);
        var gateway=new CreationQianchuanGateway(client,new OceanJson(json),json,properties,credentials,accounts);gateway.productPrices(prices);
        var product=gateway.products(tenant,"account-a","123",1,10,null,"ALL",false,null).items().getFirst();
        assertEquals(id,product.qcProductId());assertEquals(new BigDecimal("17.89"),product.price());assertNull(product.userPrice());assertEquals(List.of("当前不可投"),product.unavailableReasons());
        var repository=mock(com.lynx.ad.modules.catalog.domain.port.ProductRepository.class);
        var saved=com.lynx.ad.modules.catalog.domain.model.Product.fromPool(tenant,new AwemeId("123"),new QcProductId(id),"手套",com.lynx.ad.common.domain.Money.ofYuan(17.89),null);
        saved.changeUserPrice(com.lynx.ad.common.domain.Money.ofYuan(18));when(repository.findByAweme(tenant,new AwemeId("123"))).thenReturn(List.of(saved));gateway.productRepository(repository);
        response=body(id,"19.89","19.89",1989);
        product=gateway.products(tenant,"account-a","123",1,10,null,"ALL",false,null,true).items().getFirst();
        assertEquals(new BigDecimal("19.89"),product.price());assertEquals(saved.userPrice().amount(),product.userPrice());
        assertThrows(com.lynx.ad.common.exception.BizException.class,()->gateway.selected(tenant,"account-a","123",List.of(id)));
        verify(accounts,atLeastOnce()).findById(tenant,new AccountId("account-a"));
    }
    @Test void batchQueriesStayBoundedAndMalformedIdsNeverReachPlatform() {
        var refs=new ArrayList<Ref>();
        for(long value=3841023363610968600L;value<3841023363610968625L;value++)refs.add(new Ref(Long.toString(value),null));
        refs.add(new Ref("9999999999999999999",null));refs.add(new Ref("not-an-id",null));
        prices.prices(tenant,credential,"123",refs,false);assertEquals(3,calls.get());
        assertTrue(queries.stream().noneMatch(q->q.contains("9999999999999999999")||q.contains("not-an-id")));
        assertTrue(queries.stream().allMatch(q->q.contains("page_size=10")));
    }
}
