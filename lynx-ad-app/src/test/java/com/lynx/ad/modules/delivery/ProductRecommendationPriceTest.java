package com.lynx.ad.modules.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.http.*;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.catalog.application.ProductPriceService;
import com.lynx.ad.modules.delivery.api.CreationController;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.service.ProductRecommendationPolicy.Suggestion;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the HTTP response through the real recommendation, gateway and price services. */
class ProductRecommendationPriceTest {
    final TenantId tenant=new TenantId("tenant");
    final String anchor="3841023363610968621",sock="3841021345144111165",shoe="3841021345144111166",other="3841021345144111167";
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final LynxAdProperties properties=new LynxAdProperties();
    final List<List<String>> priceQueries=new ArrayList<>();
    MockMvc mvc;boolean priceFailure;

    @BeforeEach void setup() throws Exception {
        TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        properties.getMock().setQianchuan(false);
        var client=mock(QianchuanRestClient.class);var accounts=mock(OauthAccountRepository.class);
        var credentials=mock(QianchuanCredentialFacade.class);var account=mock(OauthAccount.class);
        when(account.platform()).thenReturn(Platform.QIANCHUAN);when(account.status()).thenReturn("ACTIVE");
        when(account.awemes()).thenReturn(List.of(new Aweme(new AwemeId("123"),"抖音号",null,AwemeType.AUTHORIZED,false,null)));
        when(accounts.findById(tenant,new AccountId("account"))).thenReturn(Optional.of(account));
        when(credentials.getUsableCredential(tenant,new AccountId("account"))).thenReturn(new Credential("test-token",new AdvertiserId("9007199254740993"),"account"));
        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec req=call.getArgument(0);var params=query(req.url());
            assertEquals("test-token",req.headers().get("Access-Token"));assertEquals("9007199254740993",params.get("advertiser_id"));assertEquals("123",params.get("aweme_id"));
            if(req.url().startsWith(properties.getQianchuan().getCatalog().getProductPriceUrl()+"?")) {
                var requested=json.readTree(params.get("filter")).path("product_ids");
                List<String> ids=new ArrayList<>();requested.forEach(id->{assertTrue(id.isIntegralNumber());ids.add(id.asText());});priceQueries.add(ids);
                if(priceFailure)return "{\"code\":50001,\"message\":\"temporary failure\"}";
                var data=json.createObjectNode();var rows=data.putArray("product_list");
                for(String id:ids)rows.addObject().put("id",id).put("channel_id","1001").put("discount_lower_price","19.90").put("discount_higher_price","29.90");
                return "{\"code\":0,\"data\":"+data+"}";
            }
            assertTrue(req.url().startsWith(properties.getQianchuan().getCatalog().getProductGetUrl()+"?"));
            var filter=json.readTree(params.get("filtering"));
            var ids=filter.has("product_ids")?List.of(anchor):List.of(anchor,sock,shoe,other);
            var data=json.createObjectNode();var rows=data.putArray("product_list");
            for(String id:ids)rows.addObject().put("id",id).put("name","商品"+id).put("stock_num",100).put("channel_id","1001");
            data.putObject("page_info").put("total_number",ids.size());
            return "{\"code\":0,\"data\":"+data+"}";
        });
        var ocean=new OceanJson(json);var gateway=new CreationQianchuanGateway(client,ocean,json,properties,credentials,accounts);
        gateway.productPrices(new ProductPriceService(client,ocean,json,properties,null));
        var drafts=mock(DraftProductStore.class);
        when(drafts.decorate(anyString(),anyString(),nullable(String.class),anyList())).thenAnswer(call->call.getArgument(3));
        var combinations=mock(ProductCombinationService.class);
        when(combinations.recommend(any(),anyString(),anyString(),anyList(),anyList(),anyBoolean())).thenAnswer(call->{
            List<Product> candidates=call.getArgument(4);
            assertEquals(3,candidates.size());assertTrue(candidates.stream().allMatch(p->p.price()==null&&p.priceInfo()==null));
            return candidates.stream().filter(p->!other.equals(p.qcProductId())).map(p->new Suggestion(p,List.of("AI组合建议：运动场景搭配"),List.of(anchor))).toList();
        });
        var service=new ProductRecommendationService(gateway,drafts,mock(AdPlanRepository.class),combinations,mock(CreationStore.class));
        var controller=new CreationController(mock(CreationWorkflowService.class),gateway);controller.productSuggestions(service);
        mvc=MockMvcBuilders.standaloneSetup(controller).build();
    }
    @AfterEach void clear(){TenantContext.clear();}
    @Test void recommendationResponseContainsRealPriceRangesAndBatchesOnlyTheDisplayedProducts() throws Exception {
        for(int turn=0;turn<2;turn++)mvc.perform(get("/api/v1/delivery/creation/products/suggestions").param("accountId","account").param("awemeId","123").param("productIds",anchor))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].product.qcProductId").value(sock))
            .andExpect(jsonPath("$.data.items[0].product.price").value(19.90))
            .andExpect(jsonPath("$.data.items[0].product.priceInfo.minPrice").value(19.90))
            .andExpect(jsonPath("$.data.items[0].product.priceInfo.maxPrice").value(29.90))
            .andExpect(jsonPath("$.data.items[0].product.priceInfo.source").value("QIANCHUAN_AVAILABLE"))
            .andExpect(jsonPath("$.data.items[0].product.priceInfo.stale").value(false))
            .andExpect(jsonPath("$.data.items[0].reasons[0]").value("AI组合建议：运动场景搭配"))
            .andExpect(jsonPath("$.data.items[0].matchedProductIds[0]").value(anchor));
        assertEquals(List.of(List.of(anchor),List.of(sock,shoe)),priceQueries);
    }
    @Test void platformPriceFailureDoesNotInventPricesOrBreakRecommendations() throws Exception {
        priceFailure=true;
        mvc.perform(get("/api/v1/delivery/creation/products/suggestions").param("accountId","account").param("awemeId","123").param("productIds",anchor))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].product.price").isEmpty())
            .andExpect(jsonPath("$.data.items[0].product.priceInfo").isEmpty());
        assertEquals(List.of(List.of(anchor),List.of(sock,shoe)),priceQueries);
    }
    private Map<String,String> query(String url){
        var params=new HashMap<String,String>();
        for(String entry:URI.create(url).getRawQuery().split("&")) {
            var pair=entry.split("=",2);params.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
        }
        return params;
    }
}
