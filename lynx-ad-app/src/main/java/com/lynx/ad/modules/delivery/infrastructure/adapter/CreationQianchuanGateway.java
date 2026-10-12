package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CreationQianchuanGateway {
    private com.lynx.ad.modules.catalog.application.ProductPriceService prices;
    private com.lynx.ad.modules.catalog.domain.port.ProductRepository productRepository;
    @org.springframework.beans.factory.annotation.Autowired
    public void productPrices(com.lynx.ad.modules.catalog.application.ProductPriceService value) { prices=value; }
    @org.springframework.beans.factory.annotation.Autowired
    public void productRepository(com.lynx.ad.modules.catalog.domain.port.ProductRepository value) { productRepository=value; }
    private Map<String,BigDecimal> manualPrices(TenantId tenant,String aweme) {
        var result=new HashMap<String,BigDecimal>();
        if(productRepository!=null)for(var p:productRepository.findByAweme(tenant,new AwemeId(aweme)))if(p.userPrice()!=null)result.put(p.qcProductId().value(),p.userPrice().amount());
        return result;
    }
    private final QianchuanRestClient client;
    private final OceanJson ocean;
    private final ObjectMapper json;
    private final LynxAdProperties properties;
    private final QianchuanCredentialFacade credentials;
    private final OauthAccountRepository accounts;
    public CreationQianchuanGateway(QianchuanRestClient client,OceanJson ocean,ObjectMapper json,LynxAdProperties properties,QianchuanCredentialFacade credentials,OauthAccountRepository accounts) {
        this.client=client;this.ocean=ocean;this.json=json;this.properties=properties;this.credentials=credentials;this.accounts=accounts;
    }
    public Credential credential(TenantId tenant,String account,String aweme) {
        var bound=accounts.findById(tenant,new AccountId(account)).orElseThrow(()->new BizException(1201,"授权账户不属于当前灵投账户"));
        if(bound.platform()!=Platform.QIANCHUAN || !"ACTIVE".equals(bound.status())) throw new BizException(1201,"请选择有效千川授权账户");
        if(aweme!=null && bound.awemes().stream().noneMatch(a->a.awemeId().value().equals(aweme))) throw new BizException(1201,"抖音号不属于所选授权账户");
        if(properties.getMock().isQianchuan()) throw new BizException(1524,"创建投放需要真实千川授权，模拟模式不可发布");
        return credentials.getUsableCredential(tenant,new AccountId(account));
    }
    public record Page(List<Product> items,long total,int page,int pageSize) {}
    public boolean commodityCardTitle(TenantId tenant,String account,String aweme) {
        return accounts.findById(tenant,new AccountId(account)).orElseThrow(()->new BizException(1201,"授权账户不存在"))
                .awemes().stream().filter(w->w.awemeId().value().equals(aweme)).anyMatch(w->w.type()==AwemeType.SELF||w.type()==AwemeType.OFFICIAL);
    }
    public Page products(TenantId tenant,String account,String aweme,int page,int size,String keyword,String tab,boolean unpromoted,List<String> ids) {
        return products(tenant,account,aweme,page,size,keyword,tab,unpromoted,ids,false);
    }
    public Page products(TenantId tenant,String account,String aweme,int page,int size,String keyword,String tab,boolean unpromoted,List<String> ids,boolean refreshPrices) {
        if(page<1 || size<1 || size>100) throw new BizException(1302,"商品分页参数不合法");
        var c=credential(tenant,account,aweme);
        Map<String,Object> filtering=new LinkedHashMap<>();
        if(tab!=null && !tab.isBlank()) {
            if(!Set.of("ALL","GOOD_BOOST","SEARCH_TREND","BREAKTHROUGH_PRODUCT").contains(tab)) throw new BizException(1302,"不支持的商品分类");
            filtering.put("tab",tab);
        }
        if(unpromoted && (tab==null || Set.of("ALL","GOOD_BOOST").contains(tab))) filtering.put("create_roi2_limit_product",true);
        if(ids!=null && !ids.isEmpty()) filtering.put("product_ids",ids.stream().map(CreationQianchuanGateway::numericId).toList());
        if(keyword!=null && !keyword.isBlank()) filtering.put("product_name",keyword.trim());
        var params=new LinkedHashMap<String,Object>();params.put("advertiser_id",c.advertiserId().value());params.put("aweme_id",aweme);
        params.put("filtering",encode(filtering));params.put("page",page);params.put("page_size",size);
        var data=read(c,properties.getQianchuan().getCatalog().getProductGetUrl(),params);
        var refs=new ArrayList<com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.Ref>();
        for(var p:data.path("product_list")) refs.add(new com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.Ref(
                p.path("id").asText(p.path("product_id").asText()),p.path("channel_id").asText(null)));
        var quotes=prices==null?Map.<String,com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice>of():prices.prices(tenant,c,aweme,refs,refreshPrices);
        var manual=manualPrices(tenant,aweme);
        var items=new ArrayList<Product>();
        for(var p:data.path("product_list")) {
            String id=p.path("id").asText(p.path("product_id").asText());
            if(!id.matches("[1-9][0-9]{0,18}")) continue;
            var quote=quotes.get(id);
            if(quote==null)quote=com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice.direct(p.path("price"),java.time.Instant.now());
            BigDecimal price=quote==null?null:quote.minPrice();
            var tags=strings(p.path("tag"));var reasons=strings(p.path("gray_reason"));
            items.add(new Product(id,id,p.path("name").asText(),price,manual.get(id),p.path("img").asText(p.path("image_url").asText(null)),
                    p.path("category_name").asText(""),p.path("stock_num").isNumber()?p.path("stock_num").longValue():null,tags,reasons,quote,p.path("channel_id").asText(null)));
        }
        return new Page(items,data.path("page_info").path("total_number").asLong(items.size()),page,size);
    }
    public List<Product> selected(TenantId t,String a,String aw,List<String> ids) {
        if(ids==null || ids.isEmpty() || ids.size()>10 || new HashSet<>(ids).size()!=ids.size()) throw new BizException(1302,"请选择 1–10 个不同商品");
        var products=products(t,a,aw,1,100,null,"ALL",false,ids).items();
        var result=new ArrayList<Product>();
        for(String id:ids) {
            var p=products.stream().filter(x->x.qcProductId().equals(id)).findFirst().orElseThrow(()->new BizException(1302,"商品已下架、不可投或不属于所选账户"));
            if(!p.unavailableReasons().isEmpty()) throw new BizException(1302,"商品不可投："+p.name());
            result.add(p);
        }
        return result;
    }
    public List<Product> refreshPrices(TenantId tenant,String account,String aweme,List<Product> products) {
        if(prices==null || products.isEmpty())return products;
        var c=credential(tenant,account,aweme);
        var refs=products.stream().map(p->new com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.Ref(p.qcProductId(),p.channelId())).toList();
        var quotes=prices.prices(tenant,c,aweme,refs,false);
        var savedManual=manualPrices(tenant,aweme);
        return products.stream().map(p->{
            var quote=quotes.get(p.qcProductId());
            if(quote==null && p.priceInfo()!=null)quote=p.priceInfo().staleCopy();
            BigDecimal manual=p.priceInfo()==null && Objects.equals(p.price(),p.userPrice())?null:p.userPrice();
            if(savedManual.containsKey(p.qcProductId()))manual=savedManual.get(p.qcProductId());
            return new Product(p.productId(),p.qcProductId(),p.name(),quote==null?p.price():quote.minPrice(),manual,p.imageUrl(),p.category(),p.stock(),p.tags(),p.unavailableReasons(),quote,p.channelId());
        }).toList();
    }
    public JsonNode recommendations(TenantId t,String a,String aw,List<String> ids) {
        var c=credential(t,a,aw);var params=new LinkedHashMap<String,Object>();
        params.put("advertiser_id",c.advertiserId().value());params.put("aweme_id",aw);params.put("marketing_goal","VIDEO_PROM_GOODS");
        params.put("product_ids",encode(ids.stream().map(CreationQianchuanGateway::numericId).toList()));
        var result=json.createObjectNode();
        try {result.set("budget",read(c,properties.getQianchuan().getDelivery().getSuggestBudgetUrl(),params));}
        catch(BizException e){result.put("budgetError","预算推荐获取失败："+recommendationError(e,c));}
        params.put("deep_external_action","AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI");
        try {result.set("roi",read(c,properties.getQianchuan().getDelivery().getSuggestRoiUrl(),params));}
        catch(BizException e){result.put("roiError","ROI 推荐获取失败："+recommendationError(e,c));}
        return result;
    }
    private static String recommendationError(BizException error,Credential credential) {
        String message=error.getMessage();
        if(message==null||message.isBlank())message="千川暂未返回结果，请重新获取";
        return message.replace(credential.accessToken(),"[已隐藏]").replaceAll("https?://\\S+","[平台地址]");
    }
    private JsonNode read(Credential c,String endpoint,Map<String,Object> params) {
        String query=params.entrySet().stream().map(e->url(e.getKey())+"="+url(String.valueOf(e.getValue()))).collect(java.util.stream.Collectors.joining("&"));
        return ocean.dataOrThrow(client.exchange(RequestSpec.get(endpoint+"?"+query).header("Access-Token",c.accessToken()).replayAccountId(c.accountId()).build(),String.class),1406,"千川数据获取失败");
    }
    /** No automatic POST retries: a transport failure can mean the platform accepted the write. */
    public JsonNode write(Credential c,String endpoint,JsonNode payload) {
        String response;
        try {response=client.exchange(RequestSpec.post(endpoint).header("Access-Token",c.accessToken()).json(payload.toString()).build().withoutReplay(),String.class);}
        catch(RuntimeException e){throw new BizException(1523,"平台写入结果待确认，请勿重复提交");}
        JsonNode root;
        try {root=json.readTree(response);}catch(Exception e){throw new BizException(1523,"平台响应无法确认，请勿重复提交");}
        if(!root.path("code").isIntegralNumber()) throw new BizException(1523,"平台响应缺少业务结果");
        if(root.path("code").asInt()!=0) {
            String message=root.path("message").asText("");
            if(message.isBlank())message="平台未返回具体原因";
            message=message.replace(c.accessToken(),"[已隐藏]").replaceAll("https?://\\S+","[平台地址]").replaceAll("[\\p{Cntrl}]"," ").strip();
            if(message.length()>350)message=message.substring(0,350)+"…";
            String requestId=root.path("request_id").asText("").replaceAll("[^A-Za-z0-9_-]","");
            if(requestId.length()>64)requestId=requestId.substring(0,64);
            throw new BizException(1406,"千川拒绝请求："+message+"（错误码 "+root.path("code").asInt()+(requestId.isBlank()?"":"，请求号 "+requestId)+"）");
        }
        return root.path("data");
    }
    public String create(TenantId t,String account,String aw,JsonNode payload) {
        var c=credential(t,account,aw);var data=write(c,properties.getQianchuan().getDelivery().getAdCreateUrl(),payload);
        String id=data.path("ad_id").asText();
        if(!id.matches("[1-9][0-9]{0,18}")) throw new BizException(1523,"千川未返回计划 ID，创建结果待确认");
        return id;
    }
    public JsonNode detail(TenantId tenant,String account,String aweme,String id) {
        var c=credential(tenant,account,aweme);numericId(id);
        String endpoint=properties.getQianchuan().getDelivery().getManagementBaseUrl().replaceAll("/+$","")+"/ad/detail/";
        var data=read(c,endpoint,Map.of("advertiser_id",c.advertiserId().value(),"ad_id",id));
        if(!id.equals(data.path("ad_id").asText(data.path("id").asText())))throw new BizException(1406,"千川计划详情 ID 不匹配");
        return data;
    }
    public static BigInteger numericId(String id) {
        if(id==null || !id.matches("[1-9][0-9]{0,18}")) throw new BizException(1402,"千川 ID 格式不合法");
        BigInteger value=new BigInteger(id);if(value.bitLength()>63) throw new BizException(1402,"千川 ID 超出范围");return value;
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private static String url(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static List<String> strings(JsonNode value){var result=new ArrayList<String>();if(value.isArray())value.forEach(n->{if(n.isTextual()&&!n.asText().isBlank())result.add(n.asText());});return result;}
}
