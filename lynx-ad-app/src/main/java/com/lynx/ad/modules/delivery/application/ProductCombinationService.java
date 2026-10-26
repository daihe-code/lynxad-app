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
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Uses the same managed perception model as product analysis; never falls back to lexical similarity. */
@Service
public class ProductCombinationService {
    private final CreationEngineClient engine; private final ProductCombinationFactsStore facts; private final ObjectMapper json;
    private static class Entry { String fingerprint; Instant expires=Instant.EPOCH; List<Suggestion> items=List.of(); }
    private final Map<String,Entry> cache=new LinkedHashMap<>();
    public ProductCombinationService(CreationEngineClient engine,ProductCombinationFactsStore facts,ObjectMapper json){this.engine=engine;this.facts=facts;this.json=json;}
    public List<Suggestion> recommend(TenantId tenant,String account,String aweme,List<Product> selected,List<Product> candidates,boolean refresh) {
        if(candidates.isEmpty())return List.of();
        var ids=new ArrayList<String>();selected.forEach(p->ids.add(p.qcProductId()));candidates.forEach(p->ids.add(p.qcProductId()));
        var goals=facts.goals(tenant.value(),account,aweme,ids);
        var payload=Map.of("store_id",tenant.value(),"account_id",account,"aweme_id",aweme,
                "selected",selected.stream().map(p->fact(p,goals)).toList(),"candidates",candidates.stream().map(p->fact(p,goals)).toList());
        String fingerprint;
        try{fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception invalid){throw new IllegalStateException(invalid);}
        String scope=tenant.value()+":"+account+":"+aweme;Entry entry;
        synchronized(cache){if(!cache.containsKey(scope)&&cache.size()>=128)cache.remove(cache.keySet().iterator().next());entry=cache.computeIfAbsent(scope,key->new Entry());}
        synchronized(entry){
            if(!refresh&&fingerprint.equals(entry.fingerprint)&&entry.expires.isAfter(Instant.now()))return entry.items;
            var response=engine.post("/api/v1/capabilities/perception/products/combinations",payload);
            if(response==null||!"AI_COMBINATION_V1".equals(response.path("method").asText())||!response.path("items").isArray())throw new BizException(8002,"AI未返回有效的商品组合推荐，请重试");
            var byId=new HashMap<String,Product>();candidates.forEach(p->byId.put(p.qcProductId(),p));var anchors=new HashSet<String>();selected.forEach(p->anchors.add(p.qcProductId()));
            var seen=new HashSet<String>();var result=new ArrayList<Suggestion>();
            for(var row:response.path("items")){
                String id=row.path("product_id").asText();var product=byId.get(id);if(product==null||!seen.add(id))continue;
                var matched=new ArrayList<String>();for(var seed:row.path("matched_product_ids"))if(anchors.contains(seed.asText())&&!matched.contains(seed.asText()))matched.add(seed.asText());
                var reasons=new ArrayList<String>();for(var reason:row.path("reasons"))if(reason.isTextual()&&!reason.asText().isBlank()&&reason.asText().length()<=240&&reasons.size()<3)reasons.add(reason.asText());
                if(!matched.isEmpty()&&!reasons.isEmpty())result.add(new Suggestion(product,List.copyOf(reasons),List.copyOf(matched)));
                if(result.size()==6)break;
            }
            entry.fingerprint=fingerprint;entry.expires=Instant.now().plusSeconds(60);entry.items=List.copyOf(result);return entry.items;
        }
    }
    private static Map<String,Object> fact(Product product,Map<String,ProductCombinationFactsStore.Goals> goals){
        var row=new LinkedHashMap<String,Object>();row.put("id",product.qcProductId());row.put("name",shorten(product.name(),240));row.put("category",shorten(product.category(),120));
        BigDecimal price=product.userPrice()!=null?product.userPrice():product.price();
        if(price!=null&&price.signum()>0&&(product.userPrice()!=null||product.priceInfo()==null||!product.priceInfo().stale()))row.put("price",price);
        var goal=goals.get(product.qcProductId());if(goal!=null){row.put("roi_goal_min",goal.min());row.put("roi_goal_max",goal.max());}
        return row;
    }
    private static String shorten(String value,int limit){return value==null?"":value.substring(0,Math.min(value.length(),limit));}
}
