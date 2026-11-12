package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import com.lynx.ad.modules.delivery.domain.service.ProductCatalogPolicy;
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
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CreationQianchuanGateway.class);
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
        var bound=accounts.findById(tenant,new AccountId(account)).orElseThrow(()->new BizException(1201,"授权账户不属于当前坤境账户"));
        if(bound.platform()!=Platform.QIANCHUAN || !"ACTIVE".equals(bound.status())) throw new BizException(1201,"请选择有效千川授权账户");
        if(aweme!=null && bound.awemes().stream().noneMatch(a->a.awemeId().value().equals(aweme))) throw new BizException(1201,"抖音号不属于所选授权账户");
        if(properties.getMock().isQianchuan()) throw new BizException(1524,"创建投放需要真实千川授权，模拟模式不可发布");
        return credentials.getUsableCredential(tenant,new AccountId(account));
    }
    public record Page(List<Product> items,long total,int page,int pageSize,String nextCursor,boolean hasMore) {
        public Page(List<Product> items,long total,int page,int pageSize){this(items,total,page,pageSize,null,false);}
    }
    public boolean commodityCardTitle(TenantId tenant,String account,String aweme) {
        return accounts.findById(tenant,new AccountId(account)).orElseThrow(()->new BizException(1201,"授权账户不存在"))
                .awemes().stream().filter(w->w.awemeId().value().equals(aweme)).anyMatch(w->w.type()==AwemeType.SELF||w.type()==AwemeType.OFFICIAL);
    }
    public Page products(TenantId tenant,String account,String aweme,int page,int size,String keyword,String tab,boolean unpromoted,List<String> ids) {
        return products(tenant,account,aweme,page,size,keyword,tab,unpromoted,ids,false);
    }
    public Page products(TenantId tenant,String account,String aweme,int page,int size,String keyword,String tab,boolean unpromoted,List<String> ids,boolean refreshPrices) {
        return products(tenant,account,aweme,page,size,keyword,tab,unpromoted,ids,refreshPrices,true,null);
    }
    public Page products(TenantId tenant,String account,String aweme,int page,int size,String keyword,String tab,boolean unpromoted,List<String> ids,boolean refreshPrices,String cursor) {
        return products(tenant,account,aweme,page,size,keyword,tab,unpromoted,ids,refreshPrices,true,cursor);
    }
    /** Recommendation scanning uses catalog prices; do not fan out SKU-price requests for every candidate. */
    public Page recommendationCandidates(TenantId tenant,String account,String aweme,int page,int size) {
        return products(tenant,account,aweme,page,size,null,"ALL",false,null,false,false,null);
    }
    private Page products(TenantId tenant,String account,String aweme,int page,int size,String keyword,String tab,boolean unpromoted,List<String> ids,boolean refreshPrices,boolean fetchPrices,String cursor) {
        if(page<1 || size<1 || size>100) throw new BizException(1302,"商品分页参数不合法");
        String category=tab==null||tab.isBlank()?"ALL":tab;
        if(!ProductCatalogPolicy.TABS.contains(category)) throw new BizException(1302,"该商品分类暂未开放，请选择全部商品、店铺新品、破圈尖货或搜索趋势");
        boolean cursorPaging=ProductCatalogPolicy.usesCursor(category);
        String position=cursorPaging?catalogCursor(cursor==null&&page==1?"0":cursor):null;
        if(cursorPaging && page>1 && "0".equals(position)) throw new BizException(1302,"请从商品分类首页重新查询");
        var c=credential(tenant,account,aweme);
        Map<String,Object> filtering=new LinkedHashMap<>();
        filtering.put("tab",category);
        // Merchant product/get accepts this filter only on ALL (the talent endpoint has different rules).
        if(unpromoted && ProductCatalogPolicy.supportsUnpromoted(category)) filtering.put("create_roi2_limit_product",true);
        if(ids!=null && !ids.isEmpty()) filtering.put("product_ids",ids.stream().map(CreationQianchuanGateway::numericId).toList());
        if(keyword!=null && !keyword.isBlank()) filtering.put("product_name",keyword.trim());
        var params=new LinkedHashMap<String,Object>();params.put("advertiser_id",c.advertiserId().value());params.put("aweme_id",aweme);
        params.put("filtering",encode(filtering));
        // NEW_PRODUCT and BREAKTHROUGH_PRODUCT reject both page and page_size.
        if(cursorPaging)params.put("cursor",position);
        else {params.put("page",page);params.put("page_size",size);}
        var data=read(c,properties.getQianchuan().getCatalog().getProductGetUrl(),params);
        var info=data.path("page_info");
        String next=null;boolean hasMore=false;
        if(cursorPaging) {
            String candidate=info.path("cursor").asText("0");
            hasMore=info.has("has_more")?info.path("has_more").asBoolean():!"0".equals(candidate);
            if(hasMore) {
                try {next=catalogCursor(candidate);}
                catch(BizException invalid) {throw new BizException(1406,"千川商品分页信息异常，请重新查询");}
                if("0".equals(next)||next.equals(position))throw new BizException(1406,"千川商品分页信息异常，请重新查询");
            }
        }
        var candidates=new ArrayList<JsonNode>();
        data.path("product_list").forEach(candidates::add);
        if(unpromoted && !ProductCatalogPolicy.supportsUnpromoted(category) && !candidates.isEmpty()) {
            // These categories reject the native filter. Check only this page's IDs
            // against the same advertiser/aweme's ALL + unpromoted endpoint.
            var eligible=unpromotedProductIds(c,aweme,candidates);
            candidates.removeIf(product->!eligible.contains(product.path("id").asText(product.path("product_id").asText())));
        }
        var refs=new ArrayList<com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.Ref>();
        for(var p:candidates) refs.add(new com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.Ref(
                p.path("id").asText(p.path("product_id").asText()),p.path("channel_id").asText(null)));
        var quotes=prices==null||!fetchPrices?Map.<String,com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice>of():prices.prices(tenant,c,aweme,refs,refreshPrices);
        var manual=manualPrices(tenant,aweme);
        var items=new ArrayList<Product>();
        for(var p:candidates) {
            String id=p.path("id").asText(p.path("product_id").asText());
            if(!id.matches("[1-9][0-9]{0,18}")) continue;
            var quote=quotes.get(id);
            if(quote==null)quote=com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice.direct(p.path("price"),java.time.Instant.now());
            BigDecimal price=quote==null?null:quote.minPrice();
            var tags=strings(p.path("tag"));var reasons=strings(p.path("gray_reason"));
            items.add(new Product(id,id,p.path("name").asText(),price,manual.get(id),p.path("img").asText(p.path("image_url").asText(null)),
                    p.path("category_name").asText(""),p.path("stock_num").isNumber()?p.path("stock_num").longValue():null,tags,reasons,quote,p.path("channel_id").asText(null),null,sales(p.get("sell_num"))));
        }
        return new Page(items,info.path("total_number").asLong(items.size()),page,size,next,hasMore);
    }
    private Set<String> unpromotedProductIds(Credential credential,String aweme,List<JsonNode> candidates) {
        var ids=candidates.stream().map(product->product.path("id").asText(product.path("product_id").asText()))
                .filter(id->id.matches("[1-9][0-9]{0,18}")).distinct().toList();
        var eligible=new HashSet<String>();
        // Bound each lookup to ten candidate IDs, never scan the whole shop.
        for(int offset=0;offset<ids.size();offset+=10) {
            var batch=ids.subList(offset,Math.min(offset+10,ids.size()));
            var params=new LinkedHashMap<String,Object>();
            params.put("advertiser_id",credential.advertiserId().value());params.put("aweme_id",aweme);
            params.put("filtering",encode(Map.of("tab","ALL","create_roi2_limit_product",true,
                    "product_ids",batch.stream().map(CreationQianchuanGateway::numericId).toList())));
            params.put("page",1);params.put("page_size",100);
            var result=read(credential,properties.getQianchuan().getCatalog().getProductGetUrl(),params);
            for(var product:result.path("product_list")) {
                String id=product.path("id").asText(product.path("product_id").asText());
                if(batch.contains(id))eligible.add(id);
            }
        }
        return eligible;
    }
    private static String catalogCursor(String value) {
        if(value==null||!value.matches("0|[1-9][0-9]{0,18}")||new BigInteger(value).compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0)
            throw new BizException(1302,"商品分页游标不合法，请从分类首页重新查询");
        return value;
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
    /** Read-only recovery: keep unavailable rows visible so the browser can explain exclusions. */
    public List<Product> restorable(TenantId tenant,String account,String aweme,List<String> ids) {
        if(ids==null||ids.isEmpty()||ids.size()>10||new HashSet<>(ids).size()!=ids.size()
                ||ids.stream().anyMatch(id->id==null||!id.matches("[1-9][0-9]{0,18}")))
            throw new BizException(1302,"恢复选品需要 1–10 个不同的有效商品 ID");
        return products(tenant,account,aweme,1,100,null,"ALL",false,ids).items();
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
            return new Product(p.productId(),p.qcProductId(),p.name(),quote==null?p.price():quote.minPrice(),manual,p.imageUrl(),p.category(),p.stock(),p.tags(),p.unavailableReasons(),quote,p.channelId(),p.draft(),p.sales());
        }).toList();
    }
    private static Long sales(JsonNode value) {
        if(value==null||value.isNull())return null;
        if(value.isIntegralNumber()&&value.canConvertToLong())return value.longValue()>=0?value.longValue():null;
        if(value.isTextual()&&value.asText().matches("[0-9]{1,19}")) {
            try{return Long.parseLong(value.asText());}catch(NumberFormatException oversized){return null;}
        }
        return null;
    }
    public JsonNode recommendations(TenantId t,String a,String aw,List<String> ids) {
        var c=credential(t,a,aw);var params=new LinkedHashMap<String,Object>();
        params.put("advertiser_id",c.advertiserId().value());params.put("aweme_id",aw);params.put("marketing_goal","VIDEO_PROM_GOODS");
        params.put("product_ids",encode(ids.stream().map(CreationQianchuanGateway::numericId).toList()));
        var result=json.createObjectNode();
        readRecommendation(c,properties.getQianchuan().getDelivery().getSuggestBudgetUrl(),params,result,"budget");
        params.put("deep_external_action","AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI");
        readRecommendation(c,properties.getQianchuan().getDelivery().getSuggestRoiUrl(),params,result,"roi");
        result.put("fetchedAt",java.time.Instant.now().toString());
        result.put("accountId",a);result.put("awemeId",aw);
        result.set("productIds",json.valueToTree(ids));
        return result;
    }
    private void readRecommendation(Credential c,String endpoint,Map<String,Object> params,com.fasterxml.jackson.databind.node.ObjectNode result,String kind) {
        try {
            String query=params.entrySet().stream().map(e->url(e.getKey())+"="+url(String.valueOf(e.getValue()))).collect(java.util.stream.Collectors.joining("&"));
            var root=ocean.root(client.exchange(RequestSpec.get(endpoint+"?"+query).header("Access-Token",c.accessToken()).replayAccountId(c.accountId()).build(),String.class));
            int code=root.path("code").asInt(-1);result.put(kind+"Code",code);
            String request=root.path("request_id").asText("").replaceAll("[^A-Za-z0-9_-]","");
            if(!request.isBlank())result.put(kind+"RequestId",request.substring(0,Math.min(64,request.length())));
            if(code!=0)throw new BizException(code,root.path("message").asText("千川未明确返回成功结果"));
            var data=root.path("data");result.set(kind,data);
            boolean available="budget".equals(kind)?data.path("budget").asDouble(0)>0:data.path("ecp_roi2_goal").asDouble(0)>0;
            result.put(kind+"Status",available?("budget".equals(kind)&&data.path("estimate_trans_cost_list").isEmpty()?"NO_ESTIMATE":"AVAILABLE"):"EMPTY");
            result.put(kind+"FetchedAt",java.time.Instant.now().toString());
        }catch(BizException e) {
            int code=result.path(kind+"Code").asInt(e.code());result.put(kind+"Code",code);
            boolean limited=Set.of(40100,40110,40130,429).contains(code);
            result.put(kind+"Status",limited?"RATE_LIMITED":code==1211||Qianchuan401ReplayCodes.isAuthExpiredCode(code)?"AUTH_REQUIRED":"UNAVAILABLE");
            String request=result.path(kind+"RequestId").asText("");
            String message=limited?"千川"+(code==40110?"开发者":code==40130?"客户账户":"")+"请求频率受限，请稍后重试":recommendationError(e,c);
            result.put(kind+"Error",("budget".equals(kind)?"预算推荐获取失败：":"ROI 推荐获取失败：")+message+"（错误码 "+code+(request.isBlank()?"":"，请求号 "+request)+"）");
        }
        log.info("qianchuan recommendation kind={} account={} code={} status={} request_id={} estimate_count={}",kind,c.accountId(),result.path(kind+"Code").asInt(-1),result.path(kind+"Status").asText(),result.path(kind+"RequestId").asText("unavailable"),result.path(kind).path("estimate_trans_cost_list").size());
    }
    private static String recommendationError(BizException error,Credential credential) {
        String message=error.getMessage();
        if(message==null||message.isBlank())message="千川暂未返回结果，请重新获取";
        message=message.replace(credential.accessToken(),"[已隐藏]").replaceAll("https?://\\S+","[平台地址]").replaceAll("[\\p{Cntrl}]"," ");
        return message.substring(0,Math.min(message.length(),350));
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
