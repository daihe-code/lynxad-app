package com.lynx.ad.modules.catalog.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Scoped snapshots survive a restart or a temporary platform outage. */
@Repository
public class ProductPriceStore {
    public record Scope(String tenant, String account, String advertiser, String aweme) {}
    public record Ref(String id, String channel) {
        public Ref { channel = channel == null ? "" : channel; }
    }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ProductPriceStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Map<Ref, PlatformProductPrice> load(Scope scope, Collection<Ref> refs) {
        if (refs.isEmpty()) return Map.of();
        var args = new ArrayList<Object>(List.of(scope.tenant(), scope.account(), scope.advertiser(), scope.aweme()));
        refs.forEach(ref -> args.add(ref.id()));
        var result = new HashMap<Ref, PlatformProductPrice>();
        jdbc.query("SELECT product_id,channel_id,price_json FROM catalog_product_price WHERE tenant_id=? AND account_id=? AND advertiser_id=? AND aweme_id=? AND product_id IN ("
                + String.join(",", Collections.nCopies(refs.size(), "?")) + ")", rs -> {
            var ref = new Ref(rs.getString(1), rs.getString(2));
            if (refs.contains(ref)) try { result.put(ref, json.readValue(rs.getString(3), PlatformProductPrice.class)); }
            catch (Exception ignored) { /* Ignore an invalid snapshot; ask the platform again. */ }
        }, args.toArray());
        return result;
    }

    public void save(Scope s, Ref r, PlatformProductPrice price) {
        try {
            String value = json.writeValueAsString(price);
            Object[] update = {value, s.tenant(), s.account(), s.advertiser(), s.aweme(), r.id(), r.channel()};
            String sql = "UPDATE catalog_product_price SET price_json=? WHERE tenant_id=? AND account_id=? AND advertiser_id=? AND aweme_id=? AND product_id=? AND channel_id=?";
            if (jdbc.update(sql, update) == 0) try {
                jdbc.update("INSERT INTO catalog_product_price (tenant_id,account_id,advertiser_id,aweme_id,product_id,channel_id,price_json) VALUES (?,?,?,?,?,?,?)",
                        s.tenant(), s.account(), s.advertiser(), s.aweme(), r.id(), r.channel(), value);
            } catch (DuplicateKeyException conflict) { jdbc.update(sql, update); }
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException(invalid); }
    }
}
