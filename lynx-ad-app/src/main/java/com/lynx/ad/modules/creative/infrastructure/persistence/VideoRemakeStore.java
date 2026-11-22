package com.lynx.ad.modules.creative.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VideoRemakeStore {
    public record Task(String tenant, String id, String actor, String binding, String product, String engine,
                       String phase, String request, String confirmation, String asset, String error,
                       String lease, Instant createdAt) {}

    private final JdbcTemplate jdbc;

    public VideoRemakeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Task> find(String tenant, String id, boolean lock) {
        return jdbc.query("SELECT * FROM creative_video_remake WHERE tenant_id=? AND id=?"
                + (lock ? " FOR UPDATE" : ""), this::task, tenant, id).stream().findFirst();
    }

    public void lockWorkspace(String tenant) {
        jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE", String.class, tenant);
    }

    public int pending(String tenant, String actor) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM creative_video_remake WHERE tenant_id=? AND actor_id=?
                  AND phase IN ('CREATING','PLANNING','CONFIRMING','SENDING','GENERATING','NEEDS_ATTENTION')
                """, Integer.class, tenant, actor);
    }

    public int recent(String tenant, String actor) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM creative_video_remake WHERE tenant_id=? AND actor_id=? AND created_at>=?
                """, Integer.class, tenant, actor, Timestamp.from(Instant.now().minusSeconds(86400)));
    }

    public void recordUpload(String tenant, String actor, String binding, String id, String kind) {
        try {
            jdbc.update("""
                    INSERT INTO creative_video_remake_upload (tenant_id,upload_id,actor_id,binding_id,media_type)
                    VALUES (?,?,?,?,?)
                    """, tenant, id, actor, binding, kind);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            if (!ownsUpload(tenant, actor, binding, id)) throw ex;
        }
    }

    /** 记录链接导入视频的来源：原始链接、来源平台与下载方式（H5 路由或备用下载接口）。 */
    public void recordUploadSource(String tenant, String uploadId, String sourceUrl, String platform,
            String method) {
        jdbc.update("""
                UPDATE creative_video_remake_upload
                SET source_url=?, source_platform=?, download_method=?
                WHERE tenant_id=? AND upload_id=?
                """, sourceUrl, platform, method, tenant, uploadId);
    }

    public boolean ownsUpload(String tenant, String actor, String binding, String id) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM creative_video_remake_upload
                WHERE tenant_id=? AND upload_id=? AND actor_id=? AND binding_id=?
                """, Integer.class, tenant, id, actor, binding) == 1;
    }

    public void finishUpload(String tenant, String id, String asset) {
        jdbc.update("UPDATE creative_video_remake_upload SET asset_id=? WHERE tenant_id=? AND upload_id=?",
                asset, tenant, id);
    }

    public boolean ownsAsset(String tenant, String actor, String binding, String asset, String kind) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM creative_video_remake_upload
                WHERE tenant_id=? AND asset_id=? AND actor_id=? AND binding_id=? AND media_type=?
                """, Integer.class, tenant, asset, actor, binding, kind) == 1;
    }

    public void create(String tenant, String id, String actor, String binding, String product, String request) {
        jdbc.update("""
                INSERT INTO creative_video_remake (tenant_id,id,actor_id,binding_id,product_name,phase,request_json)
                VALUES (?,?,?,?,?,'CREATING',?)
                """, tenant, id, actor, binding, product, request);
    }

    public List<Task> history(String tenant, String actor) {
        return jdbc.query("""
                SELECT * FROM creative_video_remake WHERE tenant_id=? AND actor_id=?
                ORDER BY created_at DESC,id DESC LIMIT 30
                """, this::task, tenant, actor);
    }

    public List<Task> due() {
        return jdbc.query("""
                SELECT * FROM creative_video_remake WHERE next_check_at<=CURRENT_TIMESTAMP
                  AND phase IN ('CREATING','PLANNING','CONFIRMING','SENDING','GENERATING','NEEDS_ATTENTION')
                ORDER BY next_check_at,created_at LIMIT 4
                """, this::task);
    }

    public boolean claim(Task task, String token) {
        return jdbc.update("""
                UPDATE creative_video_remake SET lease_token=?,next_check_at=?
                WHERE tenant_id=? AND id=? AND phase=? AND next_check_at<=CURRENT_TIMESTAMP
                """, token, Timestamp.from(Instant.now().plusSeconds(180)), task.tenant(), task.id(), task.phase()) == 1;
    }

    public boolean update(Task task, String token, String phase, String engine, String error, long delaySeconds) {
        return jdbc.update("""
                UPDATE creative_video_remake SET phase=?,engine_id=?,error_message=?,next_check_at=?,lease_token=NULL
                WHERE tenant_id=? AND id=? AND lease_token=?
                """, phase, engine, error, Timestamp.from(Instant.now().plusSeconds(delaySeconds)),
                task.tenant(), task.id(), token) == 1;
    }

    public void queueConfirmation(Task task, String confirmation) {
        jdbc.update("""
                UPDATE creative_video_remake SET phase='CONFIRMING',confirmation_json=?,next_check_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=?
                """, confirmation, task.tenant(), task.id());
    }

    public void saved(Task task, String asset) {
        jdbc.update("UPDATE creative_video_remake SET asset_id=? WHERE tenant_id=? AND id=?",
                asset, task.tenant(), task.id());
    }

    private Task task(ResultSet row, int index) throws SQLException {
        return new Task(row.getString("tenant_id"), row.getString("id"), row.getString("actor_id"),
                row.getString("binding_id"), row.getString("product_name"), row.getString("engine_id"),
                row.getString("phase"), row.getString("request_json"), row.getString("confirmation_json"),
                row.getString("asset_id"), row.getString("error_message"), row.getString("lease_token"),
                row.getTimestamp("created_at").toInstant());
    }
}
