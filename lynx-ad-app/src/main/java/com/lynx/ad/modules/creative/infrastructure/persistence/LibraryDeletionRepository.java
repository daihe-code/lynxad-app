package com.lynx.ad.modules.creative.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LibraryDeletionRepository {
    private final JdbcTemplate jdbc;
    private final String insert;
    public LibraryDeletionRepository(JdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Value("${lynx.ad.db.vendor:mysql}") String vendor) {
        this.jdbc = jdbc;
        this.insert = "INSERT INTO creative_library_deletion(tenant_id,library_id,deleted_at) VALUES(?,?,?) "
                + ("mysql".equalsIgnoreCase(vendor) ? "ON DUPLICATE KEY UPDATE library_id=VALUES(library_id)" : "ON CONFLICT DO NOTHING");
    }

    public Set<String> hidden(String tenant) {
        return new HashSet<>(jdbc.query("SELECT library_id FROM creative_library_deletion WHERE tenant_id=?",
                (r, n) -> r.getString(1), tenant));
    }

    @Transactional
    public void hide(String tenant, Collection<String> ids) {
        for (String id : new HashSet<>(ids)) {
            jdbc.update(insert, tenant, id, Timestamp.from(Instant.now()));
        }
    }

    public static String remote(String account, String type, String key) {
        return "EXT:" + account + ":" + type + ":" + key;
    }
}
