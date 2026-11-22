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
        jdbc.update("""
                UPDATE creative_video_remake_upload SET asset_id=? WHERE tenant_id=? AND upload_id=?
                """, asset, tenant, id);
    }

    public void create(String tenant, String id, String actor, String binding, String product,
            String engine, String request) {
        jdbc.update("""
                INSERT INTO creative_video_remake
                    (tenant_id,id,actor_id,binding_id,product_id,engine_task_id,phase,request_json)
                    VALUES (?,?,?,?,?,'CREATING',?)
                """, tenant, id, actor, binding, product, engine, request);
    }

    public boolean ownsAsset(String tenant, String actor, String binding, String id, String kind) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM creative_video_remake_upload
                WHERE tenant_id=? AND upload_id=? AND asset_id=? AND actor_id=? AND binding_id=? AND media_type=?
                """, Integer.class, tenant, id, id, actor, binding, kind) == 1;
    }

    public Optional<Task> findByEngine(String tenant, String engine) {
        return jdbc.query("SELECT * FROM creative_video_remake WHERE tenant_id=? AND engine_task_id=?",
                this::task, tenant, engine).stream().findFirst();
    }

    public void updatePhase(String tenant, String id, String phase, String confirmation,
            String asset, String error) {
        update(tenant, id, phase, confirmation, asset, error, null);
    }

    public void update(String tenant, String id, String phase, String confirmation,
            String asset, String error, String lease) {
        jdbc.update("""
                UPDATE creative_video_remake
                SET phase=?,confirmation_json=?,asset_id=?,error=?,lease_token=?
                WHERE tenant_id=? AND id=?
                """, phase, confirmation, asset, error, lease, tenant, id);
    }

    public List<Task> listAll(String tenant, String actor, int limit, int offset) {
        return jdbc.query("""
                SELECT * FROM creative_video_remake
                WHERE tenant_id=? AND actor_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?
                """, this::task, tenant, actor, limit, offset);
    }

    private Task task(ResultSet result, int row) throws SQLException {
        return new Task(result.getString("tenant_id"), result.getString("id"),
                result.getString("actor_id"), result.getString("binding_id"),
                result.getString("product_id"), result.getString("engine_task_id"),
                result.getString("phase"), result.getString("request_json"),
                result.getString("confirmation_json"), result.getString("asset_id"),
                result.getString("error"), result.getString("lease_token"),
                result.getTimestamp("created_at").toInstant());
    }
}
