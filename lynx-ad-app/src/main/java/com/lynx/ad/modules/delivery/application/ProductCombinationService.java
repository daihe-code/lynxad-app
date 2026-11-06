package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import com.lynx.ad.modules.delivery.domain.service.ProductRecommendationPolicy.Suggestion;
import com.lynx.ad.modules.delivery.infrastructure.persistence.ProductCombinationFactsStore;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Uses the same managed perception model as product analysis; never falls back to lexical similarity. */
@Service
public class ProductCombinationService {
    private final CreationEngineClient engine; private final ProductCombinationFactsStore facts; private final ObjectMapper json;
    private static final int MAX_ENTRIES=1024;
    private static final Duration CACHE_TTL=Duration.ofMinutes(10);
    private record Ranked(String productId,List<String> reasons,List<String> matchedProductIds) {}
    private static class Entry { Instant expires=Instant.EPOCH; List<Ranked> items=List.of(); }
    private final Map<String,Entry> cache=new LinkedHashMap<>(16,.75f,true);
    private Clock clock=Clock.systemUTC();
    public ProductCombinationService(CreationEngineClient engine,ProductCombinationFactsStore facts,ObjectMapper json){this.engine=engine;this.facts=facts;this.json=json;}
    public List<Suggestion> recommend(TenantId tenant,String account,String aweme,List<Product> selected,List<Product> candidates,boolean refresh) {
        if(candidates.isEmpty())return List.of();
        var ids=new ArrayList<String>();selected.forEach(p->ids.add(p.qcProductId()));candidates.forEach(p->ids.add(p.qcProductId()));
        var goals=facts.goals(tenant.value(),account,aweme,ids);
        // Catalog ordering and decimal formatting do not change the combination being evaluated.
        var order=Comparator.comparing(Product::qcProductId);
        var payload=Map.of("store_id",tenant.value(),"account_id",account,"aweme_id",aweme,
                "selected",selected.stream().sorted(order).map(p->fact(p,goals)).toList(),
                "candidates",candidates.stream().sorted(order).map(p->fact(p,goals)).toList());
        String fingerprint;
        try{fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception invalid){throw new IllegalStateException(invalid);}
        Entry entry;
        // Keep separate contexts for A -> B -> A instead of overwriting this account's last result.
        // The fingerprint includes tenant, account, aweme and the current product/goal facts.
        synchronized(cache){if(!cache.containsKey(fingerprint)&&cache.size()>=MAX_ENTRIES)cache.remove(cache.keySet().iterator().next());entry=cache.computeIfAbsent(fingerprint,key->new Entry());}
        synchronized(entry){
            if(!refresh&&entry.expires.isAfter(clock.instant()))return currentProducts(entry.items,candidates);
            var response=engine.post("/api/v1/capabilities/perception/products/combinations",payload);
            if(response==null||!"AI_COMBINATION_V1".equals(response.path("method").asText())||!response.path("items").isArray())throw new BizException(8002,"AI未返回有效的商品组合推荐，请重试");
            var byId=new HashMap<String,Product>();candidates.forEach(p->byId.put(p.qcProductId(),p));var anchors=new HashSet<String>();selected.forEach(p->anchors.add(p.qcProductId()));
            var seen=new HashSet<String>();var result=new ArrayList<Ranked>();
            for(var row:response.path("items")){
                String id=row.path("product_id").asText();var product=byId.get(id);if(product==null||!seen.add(id))continue;
                var matched=new ArrayList<String>();for(var seed:row.path("matched_product_ids"))if(anchors.contains(seed.asText())&&!matched.contains(seed.asText()))matched.add(seed.asText());
                var reasons=new ArrayList<String>();for(var reason:row.path("reasons"))if(reason.isTextual()&&!reason.asText().isBlank()&&reason.asText().length()<=240&&reasons.size()<3)reasons.add(reason.asText());
                if(!matched.isEmpty()&&!reasons.isEmpty())result.add(new Ranked(id,List.copyOf(reasons),List.copyOf(matched)));
                if(result.size()==6)break;
            }
            entry.expires=clock.instant().plus(CACHE_TTL);entry.items=List.copyOf(result);return currentProducts(entry.items,candidates);
        }
    }
    private static List<Suggestion> currentProducts(List<Ranked> ranking,List<Product> candidates){
        var byId=new HashMap<String,Product>();candidates.forEach(p->byId.put(p.qcProductId(),p));
        // Cache only the model's ranking/reasons; stock, prices and images come from this request.
        return ranking.stream().filter(row->byId.containsKey(row.productId()))
                .map(row->new Suggestion(byId.get(row.productId()),row.reasons(),row.matchedProductIds())).toList();
    }
    private static Map<String,Object> fact(Product product,Map<String,ProductCombinationFactsStore.Goals> goals){
        var row=new LinkedHashMap<String,Object>();row.put("id",product.qcProductId());row.put("name",shorten(product.name(),240));row.put("category",shorten(product.category(),120));
        BigDecimal price=product.userPrice()!=null?product.userPrice():product.price();
        if(price!=null&&price.signum()>0&&(product.userPrice()!=null||product.priceInfo()==null||!product.priceInfo().stale()))row.put("price",price.stripTrailingZeros());
        var goal=goals.get(product.qcProductId());if(goal!=null){row.put("roi_goal_min",goal.min().stripTrailingZeros());row.put("roi_goal_max",goal.max().stripTrailingZeros());}
        return row;
    }
    private static String shorten(String value,int limit){return value==null?"":value.substring(0,Math.min(value.length(),limit));}
}
