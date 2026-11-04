package com.lynx.ad.modules.creative.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CarouselRepository {
    private final JdbcTemplate jdbc;
    public CarouselRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Bundle(String id, String tenant, String user, String request, String name,
            List<String> accounts, String status, Instant createdAt) {}
    public record Part(String role, int position, String assetId) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private static String write(List<String> values) {
        try { return JSON.writeValueAsString(values); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static List<String> read(String raw) {
        try { return JSON.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}); }
        catch (Exception ex) { throw new IllegalStateException("图文账户信息损坏", ex); }
    }
    private static final RowMapper<Bundle> BUNDLE = (r, n) -> new Bundle(r.getString("id"), r.getString("tenant_id"),
            r.getString("user_id"), r.getString("request_id"), r.getString("name"), read(r.getString("accounts_json")),
            r.getString("review_status"), r.getTimestamp("created_at").toInstant());
    public Optional<Bundle> request(String tenant, String request) {
        return jdbc.query("SELECT * FROM creative_carousel WHERE tenant_id=? AND request_id=?", BUNDLE, tenant, request).stream().findFirst();
    }
    public Bundle require(String tenant, String id) {
        return jdbc.query("SELECT * FROM creative_carousel WHERE tenant_id=? AND id=?", BUNDLE, tenant, id).stream().findFirst()
                .orElseThrow(() -> new BizException(1522, "图文不存在或不属于当前账户"));
    }
    public void insert(Bundle bundle, List<Part> parts) {
        var now = Timestamp.from(bundle.createdAt());
        jdbc.update("INSERT INTO creative_carousel(id,tenant_id,user_id,request_id,name,accounts_json,review_status,next_check_at,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                bundle.id(), bundle.tenant(), bundle.user(), bundle.request(), bundle.name(), write(bundle.accounts()), "PENDING", now, now);
        for (var part : parts) jdbc.update("INSERT INTO creative_carousel_part(carousel_id,tenant_id,role,position,asset_id) VALUES(?,?,?,?,?)",
                bundle.id(), bundle.tenant(), part.role(), part.position(), part.assetId());
    }
    public List<Part> parts(Bundle bundle) {
        return jdbc.query("SELECT role,position,asset_id FROM creative_carousel_part WHERE tenant_id=? AND carousel_id=? ORDER BY CASE role WHEN 'TEXT' THEN 0 WHEN 'IMAGE' THEN 1 ELSE 2 END,position",
                (r,n) -> new Part(r.getString("role"),r.getInt("position"),r.getString("asset_id")), bundle.tenant(),bundle.id());
    }
    public List<Bundle> list(String tenant) { return jdbc.query("SELECT * FROM creative_carousel WHERE tenant_id=? ORDER BY created_at DESC", BUNDLE, tenant); }
    public Map<String,List<Part>> libraryParts(String tenant) {
        Map<String,List<Part>> result=new HashMap<>();
        jdbc.query("SELECT carousel_id,role,position,asset_id FROM creative_carousel_part WHERE tenant_id=? "
                + "ORDER BY carousel_id,CASE role WHEN 'TEXT' THEN 0 WHEN 'IMAGE' THEN 1 ELSE 2 END,position",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> result.computeIfAbsent(r.getString("carousel_id"),ignored->new ArrayList<>())
                        .add(new Part(r.getString("role"),r.getInt("position"),r.getString("asset_id"))),tenant);
        return result;
    }
    public List<Bundle> due() { return jdbc.query("SELECT * FROM creative_carousel WHERE review_status IN ('PENDING','FAILED') AND next_check_at<=? ORDER BY next_check_at LIMIT 50", BUNDLE, Timestamp.from(Instant.now())); }
    public void reviewed(Bundle bundle, String status) {
        jdbc.update("UPDATE creative_carousel SET review_status=?,next_check_at=? WHERE tenant_id=? AND id=?",
                status, Timestamp.from(Instant.now().plusSeconds("PENDING".equals(status) ? 3 : 15)), bundle.tenant(), bundle.id());
    }
}
