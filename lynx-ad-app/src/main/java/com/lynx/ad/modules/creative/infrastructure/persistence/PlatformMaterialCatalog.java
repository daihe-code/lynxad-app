package com.lynx.ad.modules.creative.infrastructure.persistence;

import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort.PlatformMaterial;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistent material identity and metadata. Provider preview URLs are deliberately not stored. */
@Repository
public class PlatformMaterialCatalog {
    public record Material(String tenantId, String accountId, String advertiserId, MaterialType type,
            String entryKey, String objectId, String materialId, String filename, long sizeBytes, Integer width,
            Integer height, Integer durationSec, String imageMode, String reviewBadge, Instant createdAt) {}
    public record Account(String tenantId, String accountId) {}

    private final JdbcTemplate jdbc;
    public PlatformMaterialCatalog(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void discoverAccounts() {
        for (Account account : jdbc.query("SELECT a.tenant_id,a.id FROM oauth_account a LEFT JOIN platform_material_sync s ON s.account_id=a.id "
                + "WHERE a.platform='QIANCHUAN' AND a.status='ACTIVE' AND s.account_id IS NULL",
                (r, i) -> new Account(r.getString(1), r.getString(2)))) {
            try {
                jdbc.update("INSERT INTO platform_material_sync(account_id,tenant_id,next_sync_at) VALUES(?,?,?)",
                        account.accountId(), account.tenantId(), Timestamp.from(Instant.now()));
            } catch (DuplicateKeyException alreadyTracked) {
                // Keep the current schedule and lease in a multi-replica deployment.
            }
        }
    }

    public List<Account> due(int limit) {
        return jdbc.query("SELECT s.tenant_id,s.account_id FROM platform_material_sync s JOIN oauth_account a ON a.id=s.account_id "
                + "WHERE a.status='ACTIVE' AND a.platform='QIANCHUAN' AND s.next_sync_at<=? "
                + "AND (s.lease_until IS NULL OR s.lease_until<?) ORDER BY s.next_sync_at LIMIT ?",
                (r, i) -> new Account(r.getString(1), r.getString(2)),
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), limit);
    }

    public boolean claim(Account account) {
        var now = Instant.now();
        return jdbc.update("UPDATE platform_material_sync SET lease_until=? WHERE account_id=? AND tenant_id=? "
                + "AND next_sync_at<=? AND (lease_until IS NULL OR lease_until<?)",
                Timestamp.from(now.plusSeconds(1800)), account.accountId(), account.tenantId(),
                Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    public void finish(Account account, boolean success) {
        var now = Instant.now();
        jdbc.update("UPDATE platform_material_sync SET next_sync_at=?,lease_until=NULL,last_attempt_ok=?,last_success_at=CASE WHEN ? THEN ? ELSE last_success_at END "
                + "WHERE account_id=? AND tenant_id=?",
                Timestamp.from(now.plusSeconds(success ? 600 : 300)), success, success, Timestamp.from(now),
                account.accountId(), account.tenantId());
    }

    @Transactional
    public void replace(String tenant, String account, String advertiser, MaterialType type, List<PlatformMaterial> pageItems) {
        Map<String, PlatformMaterial> unique = new LinkedHashMap<>();
        for (var item : pageItems) {
            if (item.objectId() != null && !item.objectId().isBlank()) unique.put(entryKey(item), item);
        }
        var items = List.copyOf(unique.values());
        jdbc.update("DELETE FROM platform_material_catalog WHERE tenant_id=? AND account_id=? AND material_type=?",
                tenant, account, type.name());
        jdbc.batchUpdate("INSERT INTO platform_material_catalog(tenant_id,account_id,advertiser_id,material_type,entry_key,object_id,material_id,filename,size_bytes,width,height,duration_sec,image_mode,review_badge,created_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", new BatchPreparedStatementSetter() {
            @Override public int getBatchSize() { return items.size(); }
            @Override public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                var m = items.get(i);
                ps.setString(1, tenant); ps.setString(2, account); ps.setString(3, advertiser);
                ps.setString(4, type.name()); ps.setString(5, entryKey(m)); ps.setString(6, m.objectId());
                ps.setString(7, m.materialId()); ps.setString(8, m.filename() == null ? "" : m.filename());
                ps.setLong(9, m.sizeBytes()); ps.setObject(10, m.width()); ps.setObject(11, m.height());
                ps.setObject(12, m.durationSec()); ps.setString(13, m.imageMode()); ps.setString(14, m.reviewBadge());
                ps.setTimestamp(15, m.createdAt() == null ? null : Timestamp.from(m.createdAt()));
            }
        });
    }

    /** Publish one complete account snapshot, so a failure cannot leave some types stale and others fresh. */
    @Transactional
    public void replaceAll(String tenant, String account, String advertiser,
            Map<MaterialType, List<PlatformMaterial>> materials) {
        for (MaterialType type : MaterialType.values()) {
            List<PlatformMaterial> items = materials.get(type);
            if (items == null) throw new IllegalArgumentException("Missing material type " + type);
            replace(tenant, account, advertiser, type, items);
        }
    }

    private final org.springframework.jdbc.core.RowMapper<Material> mapper = (r, i) -> new Material(
            r.getString("tenant_id"), r.getString("account_id"), r.getString("advertiser_id"),
            MaterialType.valueOf(r.getString("material_type")), r.getString("entry_key"), r.getString("object_id"),
            r.getString("material_id"), r.getString("filename"), r.getLong("size_bytes"),
            (Integer) r.getObject("width"), (Integer) r.getObject("height"), (Integer) r.getObject("duration_sec"),
            r.getString("image_mode"), r.getString("review_badge"),
            r.getTimestamp("created_at") == null ? null : r.getTimestamp("created_at").toInstant());

    public List<Material> list(String tenant) {
        return jdbc.query("SELECT c.* FROM platform_material_catalog c JOIN oauth_account a ON a.id=c.account_id "
                + "WHERE c.tenant_id=? AND a.tenant_id=? AND a.platform='QIANCHUAN' AND a.status='ACTIVE'",
                mapper, tenant, tenant);
    }

    public List<Material> list(String tenant, Instant from, Instant untilExclusive) {
        return jdbc.query("SELECT c.* FROM platform_material_catalog c JOIN oauth_account a ON a.id=c.account_id "
                + "WHERE c.tenant_id=? AND a.tenant_id=? AND a.platform='QIANCHUAN' AND a.status='ACTIVE' "
                + "AND (c.created_at IS NULL OR (c.created_at>=? AND c.created_at<?))",
                mapper, tenant, tenant, Timestamp.from(from), Timestamp.from(untilExclusive));
    }

    public Optional<Material> find(String tenant, String account, MaterialType type, String entryKey) {
        return jdbc.query("SELECT c.* FROM platform_material_catalog c JOIN oauth_account a ON a.id=c.account_id "
                + "WHERE c.tenant_id=? AND a.tenant_id=? AND a.status='ACTIVE' AND a.platform='QIANCHUAN' "
                + "AND c.account_id=? AND c.material_type=? AND c.entry_key=?",
                mapper, tenant, tenant, account, type.name(), entryKey).stream().findFirst();
    }

    public boolean hasCompletedSync(String tenant) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM oauth_account a LEFT JOIN platform_material_sync s ON s.account_id=a.id "
                + "WHERE a.tenant_id=? AND a.status='ACTIVE' AND a.platform='QIANCHUAN' AND s.last_success_at IS NULL",
                Integer.class, tenant);
        return count != null && count == 0;
    }
    public Optional<Material> findObject(String tenant,String account,MaterialType type,String objectId) {
        var matches=jdbc.query("SELECT c.* FROM platform_material_catalog c JOIN oauth_account a ON a.id=c.account_id "
                + "WHERE c.tenant_id=? AND a.tenant_id=? AND a.status='ACTIVE' AND a.platform='QIANCHUAN' "
                + "AND c.account_id=? AND c.material_type=? AND c.object_id=?",mapper,tenant,tenant,account,type.name(),objectId);
        return matches.size()==1?Optional.of(matches.getFirst()):Optional.empty();
    }

    public boolean hasFailedSync(String tenant) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM platform_material_sync s JOIN oauth_account a ON a.id=s.account_id "
                + "WHERE s.tenant_id=? AND a.status='ACTIVE' AND a.platform='QIANCHUAN' AND s.last_attempt_ok=FALSE",
                Integer.class, tenant);
        return count != null && count > 0;
    }

    public static String entryKey(PlatformMaterial material) {
        return material.materialId() != null && !material.materialId().isBlank()
                ? "m:" + material.materialId() : "o:" + material.objectId();
    }
}
