package com.lynx.ad.modules.delivery.domain.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
public final class ProductCatalogPolicy {
    private ProductCatalogPolicy() {}
    private record Tab(boolean cursor,boolean unpromoted) {}
    private static final Map<String,Tab> POLICY=load();
    public static final Set<String> TABS=POLICY.keySet();
    public static boolean usesCursor(String tab){return POLICY.get(tab).cursor();}
    public static boolean supportsUnpromoted(String tab){return POLICY.get(tab).unpromoted();}
    private static Map<String,Tab> load() {
        try(var in=ProductCatalogPolicy.class.getResourceAsStream("/product-catalog-policy.json")) {
            return StreamSupport.stream(new ObjectMapper().readTree(in).path("tabs").spliterator(),false)
                .collect(Collectors.toUnmodifiableMap(row->row.path("value").asText(),row->new Tab("CURSOR".equals(row.path("pagination").asText()),row.path("unpromoted").asBoolean())));
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
}
