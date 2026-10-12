package com.lynx.ad.modules.catalog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice;
import com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore;
import com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.*;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ProductPriceService {
    private record Entry(PlatformProductPrice price, Instant retryAt) {}
    private static class Bucket { final Map<Ref, Entry> entries = new HashMap<>(); volatile Instant used = Instant.EPOCH; }
    private final Map<Scope, Bucket> cache = new ConcurrentHashMap<>();
    private final QianchuanRestClient client;
    private final OceanJson ocean;
    private final ObjectMapper json;
    private final LynxAdProperties properties;
    private final ProductPriceStore store;
    private Clock clock = Clock.systemUTC();
    public ProductPriceService(QianchuanRestClient client, OceanJson ocean, ObjectMapper json, LynxAdProperties properties, ProductPriceStore store) {
        this.client=client; this.ocean=ocean; this.json=json; this.properties=properties; this.store=store;
    }
    void clock(Clock value) { clock=value; }

    public Map<String,PlatformProductPrice> prices(TenantId tenant, Credential credential, String aweme, Collection<Ref> requested, boolean refresh) {
        if (requested.isEmpty() || properties.getMock().isQianchuan()) return Map.of();
        var refs = requested.stream().filter(r -> validId(r.id())).distinct().toList();
        var scope = new Scope(tenant.value(), credential.accountId() == null ? "" : credential.accountId(), credential.advertiserId().value(), aweme == null ? "" : aweme);
        var bucket = cache.computeIfAbsent(scope, ignored -> new Bucket());
        synchronized (bucket) {
            Instant now=clock.instant(); bucket.used=now;
            var absent=refs.stream().filter(r -> !bucket.entries.containsKey(r)).toList();
            if (store != null && !absent.isEmpty()) try {
                store.load(scope, absent).forEach((r,p) -> bucket.entries.put(r,new Entry(p,p.fetchedAt().plusSeconds(ttl()))));
            } catch (RuntimeException ignored) { /* The platform can still supply prices. */ }
            var needed=refs.stream().filter(r -> refresh || !bucket.entries.containsKey(r) || !bucket.entries.get(r).retryAt().isAfter(now)).toList();
            // Small ID batches avoid one platform request per product and never convert IDs through double.
            for (int offset=0; offset<needed.size(); offset+=10) {
                var batch=needed.subList(offset, Math.min(offset+10,needed.size()));
                var received=new HashMap<Ref,PlatformProductPrice>();
                try {
                    var params=new LinkedHashMap<String,Object>(); params.put("advertiser_id",credential.advertiserId().value());
                    if (!scope.aweme().isBlank()) params.put("aweme_id",scope.aweme());
                    params.put("filter",json.writeValueAsString(Map.of("product_ids",batch.stream().map(r -> new BigInteger(r.id())).distinct().toList())));
                    params.put("page",1); params.put("page_size",10);
                    String query=params.entrySet().stream().map(e -> url(e.getKey())+"="+url(e.getValue().toString())).collect(java.util.stream.Collectors.joining("&"));
                    var data=ocean.dataOrThrow(client.exchange(RequestSpec.get(properties.getQianchuan().getCatalog().getProductPriceUrl()+"?"+query)
                            .header("Access-Token",credential.accessToken()).replayAccountId(credential.accountId()).build(),String.class),1306,"商品价格查询失败");
                    var seen=new HashSet<String>();
                    for (var product:data.path("product_list")) {
                        String id=product.path("id").asText(product.path("product_id").asText());
                        if (!seen.add(id)) { received.keySet().removeIf(r -> r.id().equals(id)); continue; }
                        var price=PlatformProductPrice.available(product,now);
                        if (price == null) continue;
                        String channel=product.path("channel_id").asText("");
                        for (var ref:batch) if (ref.id().equals(id) && (ref.channel().isBlank() || ref.channel().equals(channel))) received.put(ref,price);
                    }
                } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) { /* Prices never decide eligibility. */ }
                for (var ref:batch) {
                    var fresh=received.get(ref); var previous=bucket.entries.get(ref);
                    var price=fresh != null ? fresh : previous == null || previous.price()==null ? null : previous.price().staleCopy();
                    bucket.entries.put(ref,new Entry(price,now.plusSeconds(fresh == null ? 60 : ttl())));
                    if (fresh != null && store != null) try { store.save(scope,ref,fresh); } catch (RuntimeException ignored) { /* Keep the memory snapshot. */ }
                }
            }
            var result=new LinkedHashMap<String,PlatformProductPrice>();
            for (var ref:refs) { var entry=bucket.entries.get(ref); if(entry != null && entry.price()!=null) result.put(ref.id(),entry.price()); }
            if (bucket.entries.size()>2048) bucket.entries.keySet().removeIf(r -> !refs.contains(r));
            if (cache.size()>128) cache.entrySet().stream().filter(e -> e.getValue()!=bucket).min(Comparator.comparing(e -> e.getValue().used)).ifPresent(e -> cache.remove(e.getKey(),e.getValue()));
            return Collections.unmodifiableMap(result);
        }
    }
    private long ttl() { return Math.max(1,properties.getQianchuan().getCatalog().getPriceCacheSeconds()); }
    private static boolean validId(String value) { return value != null && value.matches("[1-9][0-9]{0,18}") && new BigInteger(value).bitLength()<=63; }
    private static String url(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
