package com.lynx.ad.modules.creative.infrastructure.persistence;

import com.lynx.ad.common.exception.BizException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AssetUploadRepository {
    private final JdbcTemplate jdbc;
    public AssetUploadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Asset(String id, String tenantId, String userId, String requestId, String kind, String filename,
            String contentType, long sizeBytes, String objectKey, String storageStatus, String storageMode,
            String uploadId, String uploadTicketJson, String md5, boolean isAigc, Integer width, Integer height,
            String textContent, String canonicalId, String errorMessage, Instant createdAt, Instant updatedAt) {}
    public record Sync(String id, String tenantId, String assetId, String accountId, String advertiserId,
            String status, String objectId, String materialId, String requestId, String reviewStatus,
            int attempts, Instant nextAttemptAt, Instant leaseUntil, String errorMessage) {}
    public record Review(String assetId,String tenantId,String status,String reviewId,String decision,String summary,
            List<String> reasons,String errorMessage,int attempts,Instant createdAt) {}
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    private static List<String> reasons(String value) {
        if(value==null) return List.of();
        try { return JSON.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}); }
        catch(Exception ex) { return List.of(); }
    }
    private static final RowMapper<Review> REVIEW=(r,n)->new Review(r.getString("asset_id"),r.getString("tenant_id"),r.getString("status"),r.getString("review_id"),r.getString("decision"),r.getString("summary"),reasons(r.getString("reasons_json")),r.getString("error_message"),r.getInt("attempts"),instant(r,"created_at"));
    private static Instant instant(ResultSet r, String key) throws SQLException { var value = r.getTimestamp(key); return value == null ? null : value.toInstant(); }
    private static final RowMapper<Asset> ASSET = (r, n) -> new Asset(r.getString("id"), r.getString("tenant_id"), r.getString("user_id"), r.getString("request_id"), r.getString("kind"), r.getString("filename"), r.getString("content_type"), r.getLong("size_bytes"), r.getString("object_key"), r.getString("storage_status"), r.getString("storage_mode"), r.getString("upload_id"), r.getString("upload_ticket_json"), r.getString("md5"), r.getBoolean("is_aigc"), (Integer)r.getObject("width"), (Integer)r.getObject("height"), r.getString("text_content"), r.getString("canonical_id"), r.getString("error_message"), instant(r,"created_at"), instant(r,"updated_at"));
    private static final RowMapper<Sync> SYNC = (r,n) -> new Sync(r.getString("id"), r.getString("tenant_id"), r.getString("asset_id"), r.getString("account_id"), r.getString("advertiser_id"), r.getString("status"), r.getString("object_id"), r.getString("material_id"), r.getString("request_id"), r.getString("review_status"), r.getInt("attempts"), instant(r,"next_attempt_at"), instant(r,"lease_until"), r.getString("error_message"));
    public Asset require(String tenant, String id) {
        return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND id=?", ASSET, tenant, id).stream().findFirst().orElseThrow(() -> new BizException(1522, "素材不存在或不属于当前账户"));
    }
    public Optional<Asset> request(String tenant, String request) { return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND request_id=?", ASSET, tenant, request).stream().findFirst(); }
    public Optional<Asset> object(String tenant,String key) { return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND object_key=? AND storage_status='STORED'",ASSET,tenant,key).stream().findFirst(); }
    public Optional<Asset> content(String tenant, String content) { return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND content_key=? AND storage_status='STORED'", ASSET, tenant, content).stream().findFirst(); }
    public void insert(Asset a) {
        jdbc.update("INSERT INTO creative_asset(id,tenant_id,user_id,request_id,kind,filename,content_type,size_bytes,object_key,storage_status,storage_mode,is_aigc,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            a.id(),a.tenantId(),a.userId(),a.requestId(),a.kind(),a.filename(),a.contentType(),a.sizeBytes(),a.objectKey(),a.storageStatus(),a.storageMode(),a.isAigc(),Timestamp.from(a.createdAt()),Timestamp.from(a.updatedAt()));
    }
    public boolean ticket(String tenant, String id, String uploadId, String json) { return jdbc.update("UPDATE creative_asset SET upload_id=?,upload_ticket_json=?,updated_at=? WHERE tenant_id=? AND id=? AND upload_id IS NULL AND storage_status IN ('WAITING','FAILED')", uploadId,json,Timestamp.from(Instant.now()),tenant,id)==1; }
    public boolean claimUpload(String tenant, String id) { return jdbc.update("UPDATE creative_asset SET storage_status='UPLOADING',error_message=NULL,updated_at=? WHERE tenant_id=? AND id=? AND storage_status IN ('WAITING','FAILED')", Timestamp.from(Instant.now()),tenant,id) == 1; }
    public void validating(Asset a) { jdbc.update("UPDATE creative_asset SET storage_status='VALIDATING',updated_at=? WHERE tenant_id=? AND id=? AND storage_status='UPLOADING'",Timestamp.from(Instant.now()),a.tenantId(),a.id()); }
    public void stored(Asset a, String md5, Integer width, Integer height, String text) {
        int updated=jdbc.update("UPDATE creative_asset SET storage_status='STORED',content_key=?,md5=?,width=?,height=?,text_content=?,error_message=NULL,upload_ticket_json=NULL,updated_at=? WHERE tenant_id=? AND id=? AND storage_status IN ('UPLOADING','VALIDATING')",
            contentKey(a,md5),md5,width,height,text,Timestamp.from(Instant.now()),a.tenantId(),a.id());
        if(updated!=1) throw new BizException(1520,"上传会话已取消或过期");
    }
    public static String contentKey(Asset a, String md5) { return a.kind()+":"+a.isAigc()+":"+md5; }
    public void duplicate(Asset a, String canonical) {
        jdbc.update("UPDATE creative_asset SET storage_status='DUPLICATE',canonical_id=?,upload_ticket_json=NULL,updated_at=? WHERE tenant_id=? AND id=?",canonical,Timestamp.from(Instant.now()),a.tenantId(),a.id());
    }
    public void removeDuplicateTasks(Asset a) { jdbc.update("DELETE FROM creative_asset_platform WHERE tenant_id=? AND asset_id=? AND status='WAITING_STORAGE'",a.tenantId(),a.id()); }
    public void storageFailed(Asset a, String error) {
        jdbc.update("UPDATE creative_asset SET storage_status='FAILED',error_message=?,updated_at=? WHERE tenant_id=? AND id=? AND storage_status IN ('WAITING','UPLOADING','VALIDATING','FAILED')",safe(error),Timestamp.from(Instant.now()),a.tenantId(),a.id());
    }
    public void promotedToOss(String tenant,String id) {
        jdbc.update("UPDATE creative_asset SET storage_mode='oss',updated_at=? WHERE tenant_id=? AND id=? AND storage_mode='local' AND storage_status='STORED'",Timestamp.from(Instant.now()),tenant,id);
    }
    public void addSync(Asset a, String accountId) {
        var now = Timestamp.from(Instant.now());
        try { jdbc.update("INSERT INTO creative_asset_platform(id,tenant_id,asset_id,account_id,status,next_attempt_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(),a.tenantId(),a.id(),accountId,"WAITING_STORAGE",now,now,now); }
        catch (DuplicateKeyException ignored) { /* Unique per asset/account. Do not re-upload completed targets. */ }
    }
    public List<Sync> syncs(String tenant, String asset) { return jdbc.query("SELECT * FROM creative_asset_platform WHERE tenant_id=? AND asset_id=? ORDER BY created_at,id",SYNC,tenant,asset); }
    public void review(Sync sync,String status) {
        if(!Set.of("REVIEWING","REJECTED","PASS").contains(status)) return;
        jdbc.update("UPDATE creative_asset_platform SET review_status=? WHERE tenant_id=? AND id=? AND status='UPLOADED'",status,sync.tenantId(),sync.id());
    }
    public void activate(String tenant, String id) {
        String approved="EXISTS (SELECT 1 FROM creative_asset_review r WHERE r.tenant_id=creative_asset_platform.tenant_id AND r.asset_id=creative_asset_platform.asset_id AND r.status='APPROVED')";
        jdbc.update("UPDATE creative_asset_platform SET status='QUEUED',updated_at=? WHERE tenant_id=? AND asset_id=? AND status IN ('WAITING_STORAGE','WAITING_REVIEW') AND "+approved,Timestamp.from(Instant.now()),tenant,id);
        jdbc.update("UPDATE creative_asset_platform SET status='WAITING_REVIEW',updated_at=? WHERE tenant_id=? AND asset_id=? AND status='WAITING_STORAGE' AND NOT "+approved,Timestamp.from(Instant.now()),tenant,id);
    }
    public void carouselComponent(String tenant, String id) { jdbc.update("UPDATE creative_asset SET library_type='CAROUSEL' WHERE tenant_id=? AND id=?",tenant,id); }
    public void exposeStandalone(String tenant, String id) { jdbc.update("UPDATE creative_asset SET library_type=NULL WHERE tenant_id=? AND id=?",tenant,id); }
    public boolean isCarouselComponent(String tenant, String id) { return "CAROUSEL".equals(jdbc.queryForObject("SELECT library_type FROM creative_asset WHERE tenant_id=? AND id=?",String.class,tenant,id)); }
    public List<Asset> list(String tenant) { return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND storage_status='STORED' AND (library_type IS NULL OR library_type<>'CAROUSEL') ORDER BY created_at DESC",ASSET,tenant); }
    public List<Sync> due(int limit) {
        return jdbc.query("SELECT p.* FROM creative_asset_platform p JOIN creative_asset a ON a.id=p.asset_id AND a.tenant_id=p.tenant_id JOIN creative_asset_review r ON r.asset_id=a.id AND r.tenant_id=a.tenant_id WHERE a.storage_status='STORED' AND r.status='APPROVED' AND p.status='QUEUED' AND p.next_attempt_at<=? ORDER BY p.next_attempt_at,p.id LIMIT ?",SYNC,Timestamp.from(Instant.now()),limit);
    }
    public boolean claim(Sync s) {
        var now = Instant.now();
        return jdbc.update("UPDATE creative_asset_platform SET status='RUNNING',attempts=attempts+1,lease_until=?,updated_at=? WHERE tenant_id=? AND id=? AND status='QUEUED' AND next_attempt_at<=? AND EXISTS (SELECT 1 FROM creative_asset_review r WHERE r.asset_id=creative_asset_platform.asset_id AND r.tenant_id=creative_asset_platform.tenant_id AND r.status='APPROVED')",Timestamp.from(now.plusSeconds(900)),Timestamp.from(now),s.tenantId(),s.id(),Timestamp.from(now))==1;
    }
    public void heartbeat(Sync s) { jdbc.update("UPDATE creative_asset_platform SET lease_until=? WHERE tenant_id=? AND id=? AND status='RUNNING' AND attempts=?",Timestamp.from(Instant.now().plusSeconds(900)),s.tenantId(),s.id(),s.attempts()+1); }
    public void finish(Sync s, String advertiser, String object, String material, String request) {
        jdbc.update("UPDATE creative_asset_platform SET status='UPLOADED',advertiser_id=?,object_id=?,material_id=?,request_id=?,review_status='UNKNOWN',error_message=NULL,lease_until=NULL,updated_at=? WHERE tenant_id=? AND id=? AND status='RUNNING' AND attempts=?",advertiser,object,material,request,Timestamp.from(Instant.now()),s.tenantId(),s.id(),s.attempts()+1);
    }
    public void fail(Sync s, String status, String error, int delaySec) {
        var now=Instant.now();
        jdbc.update("UPDATE creative_asset_platform SET status=?,error_message=?,lease_until=NULL,next_attempt_at=?,updated_at=? WHERE tenant_id=? AND id=? AND status='RUNNING' AND attempts=?",status,safe(error),Timestamp.from(now.plusSeconds(delaySec)),Timestamp.from(now),s.tenantId(),s.id(),s.attempts()+1);
    }
    public int retry(String tenant, String asset, String account, boolean acknowledgeUncertain) {
        String sql="UPDATE creative_asset_platform SET status='QUEUED',error_message=NULL,next_attempt_at=?,updated_at=? WHERE tenant_id=? AND asset_id=? AND status IN ('FAILED','WAITING_AUTH','PAUSED'"+(acknowledgeUncertain ? ",'UNCERTAIN'" : "")+")";
        var now=Timestamp.from(Instant.now());
        if (account == null || account.isBlank()) return jdbc.update(sql,now,now,tenant,asset);
        return jdbc.update(sql+" AND account_id=?",now,now,tenant,asset,account);
    }
    public void recoverExpired() {
        var now=Timestamp.from(Instant.now());
        jdbc.update("UPDATE creative_asset_platform SET status='UNCERTAIN',error_message='任务中断，千川上传结果待确认，请核对后重试',lease_until=NULL,updated_at=? WHERE status='RUNNING' AND lease_until<?",now,now);
        jdbc.update("UPDATE creative_asset_platform SET status='QUEUED',updated_at=? WHERE status IN ('WAITING_STORAGE','WAITING_REVIEW') AND EXISTS (SELECT 1 FROM creative_asset a JOIN creative_asset_review r ON r.asset_id=a.id AND r.tenant_id=a.tenant_id WHERE a.id=creative_asset_platform.asset_id AND a.tenant_id=creative_asset_platform.tenant_id AND a.storage_status='STORED' AND r.status='APPROVED')",now);
        jdbc.update("UPDATE creative_asset_platform SET status='WAITING_REVIEW',updated_at=? WHERE status='WAITING_STORAGE' AND EXISTS (SELECT 1 FROM creative_asset a WHERE a.id=creative_asset_platform.asset_id AND a.tenant_id=creative_asset_platform.tenant_id AND a.storage_status='STORED')",now);
    }
    public List<Sync> waitingAuth() { return jdbc.query("SELECT * FROM creative_asset_platform WHERE status='WAITING_AUTH' ORDER BY updated_at LIMIT 100",SYNC); }
    public void attemptedCredential(Sync s,String fingerprint) { jdbc.update("UPDATE creative_asset_platform SET credential_fingerprint=? WHERE tenant_id=? AND id=? AND status='RUNNING' AND attempts=?",fingerprint,s.tenantId(),s.id(),s.attempts()+1); }
    public String attemptedCredential(Sync s) { return jdbc.queryForObject("SELECT credential_fingerprint FROM creative_asset_platform WHERE tenant_id=? AND id=?",String.class,s.tenantId(),s.id()); }
    public List<Asset> abandoned() { return jdbc.query("SELECT * FROM creative_asset WHERE storage_status IN ('WAITING','UPLOADING','VALIDATING','FAILED') AND updated_at<?",ASSET,Timestamp.from(Instant.now().minusSeconds(7200))); }
    public void expired(Asset a) { jdbc.update("UPDATE creative_asset SET storage_status='EXPIRED',error_message='上传会话过期，请重新选择文件',upload_ticket_json=NULL,updated_at=? WHERE tenant_id=? AND id=? AND storage_status IN ('WAITING','UPLOADING','VALIDATING','FAILED')",Timestamp.from(Instant.now()),a.tenantId(),a.id()); }
    public Review aiReview(String tenant,String id) { return jdbc.query("SELECT * FROM creative_asset_review WHERE tenant_id=? AND asset_id=?",REVIEW,tenant,id).stream().findFirst().orElse(null); }
    public boolean approved(String tenant,String id) { var r=aiReview(tenant,id); return r!=null && "APPROVED".equals(r.status()); }
    public void queueReview(String tenant,String id) {
        var now=Timestamp.from(Instant.now());
        try { jdbc.update("INSERT INTO creative_asset_review(asset_id,tenant_id,status,next_attempt_at,created_at,updated_at) SELECT id,tenant_id,'QUEUED',?,?,? FROM creative_asset WHERE tenant_id=? AND id=? AND storage_status='STORED'",now,now,now,tenant,id); }
        catch(DuplicateKeyException ignored) { /* Same canonical file shares a single review across accounts. */ }
    }
    public List<Review> dueReviews(int limit) { return jdbc.query("SELECT r.* FROM creative_asset_review r JOIN creative_asset a ON a.id=r.asset_id AND a.tenant_id=r.tenant_id WHERE a.storage_status='STORED' AND r.status IN ('QUEUED','PENDING') AND r.next_attempt_at<=? ORDER BY r.next_attempt_at,r.asset_id LIMIT ?",REVIEW,Timestamp.from(Instant.now()),limit); }
    public boolean claimReview(Review r) {
        var now=Instant.now(); return jdbc.update("UPDATE creative_asset_review SET status='RUNNING',attempts=attempts+1,lease_until=?,updated_at=? WHERE tenant_id=? AND asset_id=? AND attempts=? AND status IN ('QUEUED','PENDING') AND next_attempt_at<=?",Timestamp.from(now.plusSeconds(600)),Timestamp.from(now),r.tenantId(),r.assetId(),r.attempts(),Timestamp.from(now))==1;
    }
    public void finishReview(Review r,com.lynx.ad.modules.creative.domain.port.AssetReviewPort.Result result) {
        try {
            var now=Instant.now();
            jdbc.update("UPDATE creative_asset_review SET status=?,review_id=?,decision=?,summary=?,reasons_json=?,error_message=?,lease_until=NULL,next_attempt_at=?,updated_at=? WHERE tenant_id=? AND asset_id=? AND status='RUNNING' AND attempts=?",result.status(),"FAILED".equals(result.status()) ? null : result.reviewId(),result.decision(),safe(result.summary()),JSON.writeValueAsString(result.reasons()),safe(result.error()),Timestamp.from(now.plusSeconds(5)),Timestamp.from(now),r.tenantId(),r.assetId(),r.attempts()+1);
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    public void failReview(Review r,String error) { jdbc.update("UPDATE creative_asset_review SET status='FAILED',error_message=?,lease_until=NULL,updated_at=? WHERE tenant_id=? AND asset_id=? AND status='RUNNING' AND attempts=?",safe(error),Timestamp.from(Instant.now()),r.tenantId(),r.assetId(),r.attempts()+1); }
    public void retryReview(String tenant,String id) {
        var now=Timestamp.from(Instant.now());
        jdbc.update("UPDATE creative_asset_review SET status='QUEUED',error_message=NULL,summary=NULL,reasons_json=NULL,decision=NULL,next_attempt_at=?,created_at=?,updated_at=? WHERE tenant_id=? AND asset_id=? AND status='FAILED'",now,now,now,tenant,id);
    }
    public void recoverReviews() {
        var now=Timestamp.from(Instant.now());
        jdbc.update("UPDATE creative_asset_review SET status=CASE WHEN review_id IS NULL THEN 'FAILED' ELSE 'PENDING' END,error_message=CASE WHEN review_id IS NULL THEN '审核请求中断，结果未知，请重试审核' ELSE NULL END,lease_until=NULL,next_attempt_at=?,updated_at=? WHERE status='RUNNING' AND lease_until<?",now,now,now);
        for(var a:jdbc.query("SELECT a.* FROM creative_asset a WHERE a.storage_status='STORED' AND NOT EXISTS (SELECT 1 FROM creative_asset_review r WHERE r.asset_id=a.id AND r.tenant_id=a.tenant_id) LIMIT 100",ASSET)) queueReview(a.tenantId(),a.id());
    }
    private static String safe(String value) { return value==null ? null : value.substring(0,Math.min(500,value.length())); }
}
