package com.lynx.ad.modules.delivery.domain.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
public final class ProductCatalogPolicy {
    private ProductCatalogPolicy() {}
    public static final Set<String> TABS=load();
    private static Set<String> load() {
        try(var in=ProductCatalogPolicy.class.getResourceAsStream("/product-catalog-policy.json")) {
            return StreamSupport.stream(new ObjectMapper().readTree(in).path("tabs").spliterator(),false)
                .map(row->row.path("value").asText()).collect(Collectors.toUnmodifiableSet());
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
}
