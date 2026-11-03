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
            int attempts, Instant nextAttemptAt, Instant leaseUntil, String errorMessage,Verification verification) {
        public Sync(String id,String tenantId,String assetId,String accountId,String advertiserId,String status,String objectId,String materialId,String requestId,String reviewStatus,int attempts,Instant nextAttemptAt,Instant leaseUntil,String errorMessage){this(id,tenantId,assetId,accountId,advertiserId,status,objectId,materialId,requestId,reviewStatus,attempts,nextAttemptAt,leaseUntil,errorMessage,null);}
    }
    public record Verification(String state,Instant checkedAt,String message) {}
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
    private static final RowMapper<Sync> SYNC = (r,n) -> new Sync(r.getString("id"), r.getString("tenant_id"), r.getString("asset_id"), r.getString("account_id"), r.getString("advertiser_id"), r.getString("status"), r.getString("object_id"), r.getString("material_id"), r.getString("request_id"), r.getString("review_status"), r.getInt("attempts"), instant(r,"next_attempt_at"), instant(r,"lease_until"), r.getString("error_message"),r.getString("verification_state")==null?null:new Verification(r.getString("verification_state"),instant(r,"verification_checked_at"),r.getString("verification_message")));
    public Asset require(String tenant, String id) {
        return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND id=?", ASSET, tenant, id).stream().findFirst().orElseThrow(() -> new BizException(1522, "素材不存在或不属于当前账户"));
    }
    /** Image-only preview lookup for plan lists; tenant and storage state are checked in SQL. */
    public List<Asset> storedImages(String tenant, java.util.Collection<String> requested) {
        var ids=new ArrayList<>(new java.util.LinkedHashSet<>(requested));
        var result=new ArrayList<Asset>();
        for(int from=0;from<ids.size();from+=500){
            var page=ids.subList(from,Math.min(ids.size(),from+500));
            var params=new ArrayList<Object>();params.add(tenant);params.addAll(page);
            result.addAll(jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND kind='IMAGE' AND storage_status='STORED' AND id IN ("
                    +String.join(",",java.util.Collections.nCopies(page.size(),"?"))+")",ASSET,params.toArray()));
        }
        return result;
    }
    public Optional<Asset> request(String tenant, String request) { return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND request_id=?", ASSET, tenant, request).stream().findFirst(); }
    public Optional<Asset> object(String tenant,String key) { return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND object_key=? AND storage_status='STORED'",ASSET,tenant,key).stream().findFirst(); }
    public Optional<Asset> platformObject(String tenant,String account,String kind,String objectId) {
        return jdbc.query("SELECT a.* FROM creative_asset a JOIN creative_asset_platform p ON p.tenant_id=a.tenant_id AND p.asset_id=a.id WHERE a.tenant_id=? AND p.account_id=? AND a.kind=? AND p.object_id=? AND p.status='UPLOADED' AND a.storage_status='STORED' ORDER BY a.created_at LIMIT 1",
            ASSET,tenant,account,kind,objectId).stream().findFirst();
    }
    public Optional<Asset> content(String tenant, String content) { return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND content_key=? AND storage_status='STORED'", ASSET, tenant, content).stream().findFirst(); }
    public void insert(Asset a) {
        jdbc.update("INSERT INTO creative_asset(id,tenant_id,user_id,request_id,kind,filename,content_type,size_bytes,object_key,storage_status,storage_mode,is_aigc,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            a.id(),a.tenantId(),a.userId(),a.requestId(),a.kind(),a.filename(),a.contentType(),a.sizeBytes(),a.objectKey(),a.storageStatus(),a.storageMode(),a.isAigc(),Timestamp.from(a.createdAt()),Timestamp.from(a.updatedAt()));
    }
    public record StorageMetadata(String bucket,String endpoint,String pathVersion,String origin,Long durationMs,String etag,String previewAssetId,String objectKey) {
        public StorageMetadata(String bucket,String endpoint,String pathVersion,String origin,Long durationMs,String etag,String previewAssetId){this(bucket,endpoint,pathVersion,origin,durationMs,etag,previewAssetId,null);}
    }
    public StorageMetadata storageMetadata(String tenant,String id) {
        return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND id=?",(r,n)->new StorageMetadata(r.getString("bucket"),r.getString("storage_endpoint"),r.getString("path_version"),r.getString("origin"),(Long)r.getObject("duration_ms"),r.getString("etag"),r.getString("preview_asset_id"),r.getString("object_key")),tenant,id).stream().findFirst().orElse(null);
    }
    public Optional<Asset> storageObject(String tenant,String key) {
        return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND object_key=? AND canonical_id IS NULL",ASSET,tenant,key).stream().findFirst();
    }
    public void storageMetadata(String tenant,String id,String bucket,String endpoint,String version,String origin) {
        jdbc.update("UPDATE creative_asset SET bucket=?,storage_endpoint=?,path_version=?,origin=? WHERE tenant_id=? AND id=?",bucket,endpoint,version,origin,tenant,id);
    }
    public void duration(String tenant,String id,long milliseconds) { jdbc.update("UPDATE creative_asset SET duration_ms=? WHERE tenant_id=? AND id=?",milliseconds,tenant,id); }
    public void etag(String tenant,String id,String value) { jdbc.update("UPDATE creative_asset SET etag=? WHERE tenant_id=? AND id=?",value,tenant,id); }
    public void preview(String tenant,String id,String preview) {
        var image=require(tenant,preview);if(!"IMAGE".equals(image.kind()))throw new BizException(1520,"封面必须是图片素材");
        require(tenant,id);jdbc.update("UPDATE creative_asset SET preview_asset_id=? WHERE tenant_id=? AND id=?",preview,tenant,id);
    }
    public record EngineReference(String assetId,String tenantId,String jobId,String materialId,String component,String sourceUrl) {}
    public EngineReference engineReference(String tenant,String id) {
        return jdbc.query("SELECT * FROM creative_engine_asset WHERE tenant_id=? AND asset_id=?",
                (r,n)->new EngineReference(r.getString("asset_id"),r.getString("tenant_id"),r.getString("job_id"),r.getString("material_id"),r.getString("component"),r.getString("source_url")),tenant,id)
                .stream().findFirst().orElseThrow(()->new BizException(1522,"引擎素材引用不存在或不属于当前账户"));
    }
    @org.springframework.transaction.annotation.Transactional
    public void registerEngine(Asset a,EngineReference ref) {
        insert(a);
        jdbc.update("UPDATE creative_asset SET width=?,height=?,text_content=? WHERE tenant_id=? AND id=?",a.width(),a.height(),a.textContent(),a.tenantId(),a.id());
        jdbc.update("INSERT INTO creative_engine_asset(asset_id,tenant_id,job_id,material_id,component,source_url) VALUES(?,?,?,?,?,?)",a.id(),a.tenantId(),ref.jobId(),ref.materialId(),ref.component(),ref.sourceUrl());
    }
    @org.springframework.transaction.annotation.Transactional
    public void registerEngine(Asset a,EngineReference ref,StorageMetadata meta) {
        registerEngine(a,ref);
        storageMetadata(a.tenantId(),a.id(),meta.bucket(),meta.endpoint(),meta.pathVersion(),"AI_GENERATED");
        jdbc.update("UPDATE creative_asset SET duration_ms=?,etag=? WHERE tenant_id=? AND id=?",meta.durationMs(),meta.etag(),a.tenantId(),a.id());
    }
    private static String eligibleSql(String table) {
        return "(EXISTS (SELECT 1 FROM creative_asset_review r WHERE r.tenant_id="+table+".tenant_id AND r.asset_id="+table+".asset_id AND r.status='APPROVED') OR EXISTS (SELECT 1 FROM creative_engine_asset e WHERE e.tenant_id="+table+".tenant_id AND e.asset_id="+table+".asset_id))";
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
    public void engineSignature(String tenant,String id,String md5) {
        jdbc.update("UPDATE creative_asset SET md5=?,updated_at=? WHERE tenant_id=? AND id=? AND storage_mode='engine_oss'",md5,Timestamp.from(Instant.now()),tenant,id);
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
        String approved=eligibleSql("creative_asset_platform");
        jdbc.update("UPDATE creative_asset_platform SET status='QUEUED',updated_at=? WHERE tenant_id=? AND asset_id=? AND status IN ('WAITING_STORAGE','WAITING_REVIEW') AND "+approved,Timestamp.from(Instant.now()),tenant,id);
        jdbc.update("UPDATE creative_asset_platform SET status='WAITING_REVIEW',updated_at=? WHERE tenant_id=? AND asset_id=? AND status='WAITING_STORAGE' AND NOT "+approved,Timestamp.from(Instant.now()),tenant,id);
    }
    public void carouselComponent(String tenant, String id) { jdbc.update("UPDATE creative_asset SET library_type='CAROUSEL' WHERE tenant_id=? AND id=?",tenant,id); }
    public void exposeStandalone(String tenant, String id) { jdbc.update("UPDATE creative_asset SET library_type=NULL WHERE tenant_id=? AND id=?",tenant,id); }
    public boolean isCarouselComponent(String tenant, String id) { return "CAROUSEL".equals(jdbc.queryForObject("SELECT library_type FROM creative_asset WHERE tenant_id=? AND id=?",String.class,tenant,id)); }
    public List<Asset> list(String tenant) { return jdbc.query("SELECT * FROM creative_asset WHERE tenant_id=? AND storage_status='STORED' AND (library_type IS NULL OR library_type<>'CAROUSEL') ORDER BY created_at DESC",ASSET,tenant); }
    public record LibraryMetadata(Long durationMs, String previewKey, String previewMode, boolean engine) {}
    public record LibraryDetails(Map<String, LibraryMetadata> metadata, Map<String, List<Sync>> syncs,
            Map<String, Review> reviews) {}

    /** Load all list decorations in bounded query count instead of querying each asset separately. */
    public LibraryDetails libraryDetails(String tenant, List<Asset> assets) {
        if (assets.isEmpty()) return new LibraryDetails(Map.of(), Map.of(), Map.of());
        String eligible = "a.tenant_id=? AND a.storage_status='STORED' AND (a.library_type IS NULL OR a.library_type<>'CAROUSEL')";
        Map<String, LibraryMetadata> metadata = new HashMap<>();
        jdbc.query("SELECT a.id,a.duration_ms,cover.object_key AS preview_key,cover.storage_mode AS preview_mode,"
                        + "e.asset_id AS engine_id FROM creative_asset a "
                        + "LEFT JOIN creative_asset cover ON cover.id=a.preview_asset_id AND cover.tenant_id=a.tenant_id "
                        + "LEFT JOIN creative_engine_asset e ON e.asset_id=a.id AND e.tenant_id=a.tenant_id WHERE " + eligible,
                (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                    metadata.put(r.getString("id"), new LibraryMetadata((Long) r.getObject("duration_ms"),
                            r.getString("preview_key"), r.getString("preview_mode"), r.getString("engine_id") != null));
                }, tenant);
        Map<String, List<Sync>> syncs = new HashMap<>();
        for (Sync sync : jdbc.query("SELECT p.* FROM creative_asset_platform p JOIN creative_asset a "
                        + "ON a.id=p.asset_id AND a.tenant_id=p.tenant_id WHERE " + eligible + " ORDER BY p.created_at,p.id",
                SYNC, tenant)) syncs.computeIfAbsent(sync.assetId(), ignored -> new ArrayList<>()).add(sync);
        Map<String, Review> reviews = new HashMap<>();
        for (Review review : jdbc.query("SELECT r.* FROM creative_asset_review r JOIN creative_asset a "
                        + "ON a.id=r.asset_id AND a.tenant_id=r.tenant_id WHERE " + eligible,
                REVIEW, tenant)) reviews.put(review.assetId(), review);
        for (Asset asset : assets) {
            LibraryMetadata meta = metadata.get(asset.id());
            if (meta != null && meta.engine()) reviews.put(asset.id(), new Review(asset.id(), tenant,
                    "NOT_REQUIRED", null, null, "AI 引擎生成素材，无需上传审核", List.of(), null, 0, asset.createdAt()));
        }
        return new LibraryDetails(metadata, syncs, reviews);
    }
    public List<Sync> due(int limit) {
        return jdbc.query("SELECT p.* FROM creative_asset_platform p JOIN creative_asset a ON a.id=p.asset_id AND a.tenant_id=p.tenant_id WHERE a.storage_status='STORED' AND "+eligibleSql("p")+" AND p.status='QUEUED' AND p.next_attempt_at<=? ORDER BY p.next_attempt_at,p.id LIMIT ?",SYNC,Timestamp.from(Instant.now()),limit);
    }
    public void resumeRecoverableSyncs() {
        jdbc.update("UPDATE creative_asset_platform SET status='QUEUED',updated_at=? WHERE status='FAILED' AND attempts<3 AND next_attempt_at<=? AND "+eligibleSql("creative_asset_platform"),Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
    }
    public List<Sync> uncertainSyncs() {
        return jdbc.query("SELECT * FROM creative_asset_platform WHERE status='UNCERTAIN' AND (verification_checked_at IS NULL OR verification_checked_at<?) ORDER BY updated_at LIMIT 2",SYNC,Timestamp.from(Instant.now().minusSeconds(60)));
    }
    /** A server-only format conversion inherits its approved source's content decision. */
    public void approveTechnicalRepair(String tenant,String source,String target) {
        if(!approved(tenant,source))throw new BizException(1525,"源素材尚未通过内容检查");
        var original=require(tenant,source);var repaired=require(tenant,target);
        if(!("sync-format-v1:"+source).equals(repaired.requestId())||!original.kind().equals(repaired.kind())||original.isAigc()!=repaired.isAigc())throw new BizException(1520,"技术格式文件与源素材不匹配");
        var now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO creative_asset_review(asset_id,tenant_id,status,summary,next_attempt_at,created_at,updated_at) VALUES(?,?,'APPROVED',?,?,?,?)",target,tenant,"技术格式修复，沿用已通过的内容检查",now,now,now);
    }
    public boolean claim(Sync s) {
        var now = Instant.now();
        return jdbc.update("UPDATE creative_asset_platform SET status='RUNNING',attempts=attempts+1,lease_until=?,updated_at=?,upload_evidence_version=1,attempted_signature=NULL,attempted_advertiser_id=NULL,upload_dispatched_at=NULL,verification_key=NULL,verification_lease_until=NULL,verification_state=NULL,verification_checked_at=NULL,verification_message=NULL WHERE tenant_id=? AND id=? AND status='QUEUED' AND attempts=? AND next_attempt_at<=? AND "+eligibleSql("creative_asset_platform"),Timestamp.from(now.plusSeconds(900)),Timestamp.from(now),s.tenantId(),s.id(),s.attempts(),Timestamp.from(now))==1;
    }
    /** This durable marker must commit before the multipart request can start. */
    public boolean dispatched(Sync s,String advertiser,String signature) {
        if(signature==null||!signature.matches("(?i)[0-9a-f]{32}"))throw new BizException(1520,"上传缺少有效文件签名");
        return jdbc.update("UPDATE creative_asset_platform SET attempted_advertiser_id=?,attempted_signature=?,upload_dispatched_at=?,upload_evidence_version=1 WHERE tenant_id=? AND id=? AND status='RUNNING' AND attempts=? AND upload_dispatched_at IS NULL",advertiser,signature.toLowerCase(Locale.ROOT),Timestamp.from(Instant.now()),s.tenantId(),s.id(),s.attempts()+1)==1;
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
        if(acknowledgeUncertain)throw new BizException(1523,"待确认任务只能核查结果，不可通过确认按钮重新上传");
        String sql="UPDATE creative_asset_platform SET status='QUEUED',error_message=NULL,next_attempt_at=?,updated_at=? WHERE tenant_id=? AND asset_id=? AND status IN ('FAILED','WAITING_AUTH','PAUSED')";
        var now=Timestamp.from(Instant.now());
        if (account == null || account.isBlank()) return jdbc.update(sql,now,now,tenant,asset);
        return jdbc.update(sql+" AND account_id=?",now,now,tenant,asset,account);
    }
    public void recoverExpired() {
        var now=Timestamp.from(Instant.now());
        jdbc.update("UPDATE creative_asset_platform SET status='QUEUED',error_message='发送前任务中断，正在重新排队',lease_until=NULL,next_attempt_at=?,updated_at=? WHERE status='RUNNING' AND lease_until<? AND upload_evidence_version=1 AND upload_dispatched_at IS NULL",now,now,now);
        jdbc.update("UPDATE creative_asset_platform SET status='UNCERTAIN',error_message='任务中断，千川上传结果待确认，请核查，不会重复上传',lease_until=NULL,updated_at=? WHERE status='RUNNING' AND lease_until<?",now,now);
        jdbc.update("UPDATE creative_asset_platform SET status='QUEUED',updated_at=? WHERE status IN ('WAITING_STORAGE','WAITING_REVIEW') AND EXISTS (SELECT 1 FROM creative_asset a WHERE a.id=creative_asset_platform.asset_id AND a.tenant_id=creative_asset_platform.tenant_id AND a.storage_status='STORED') AND "+eligibleSql("creative_asset_platform"),now);
        jdbc.update("UPDATE creative_asset_platform SET status='WAITING_REVIEW',updated_at=? WHERE status='WAITING_STORAGE' AND EXISTS (SELECT 1 FROM creative_asset a WHERE a.id=creative_asset_platform.asset_id AND a.tenant_id=creative_asset_platform.tenant_id AND a.storage_status='STORED')",now);
    }
    public List<Asset> unassigned() {
        return jdbc.query("SELECT a.* FROM creative_asset a WHERE a.storage_status='STORED' AND a.kind<>'TEXT' AND (a.library_type IS NULL OR a.library_type<>'CAROUSEL') AND NOT EXISTS (SELECT 1 FROM creative_asset_platform p WHERE p.asset_id=a.id AND p.tenant_id=a.tenant_id) ORDER BY a.created_at LIMIT 100",ASSET);
    }
    public List<Sync> waitingAuth() { return jdbc.query("SELECT * FROM creative_asset_platform WHERE status='WAITING_AUTH' ORDER BY updated_at LIMIT 100",SYNC); }
    public void attemptedCredential(Sync s,String fingerprint) { jdbc.update("UPDATE creative_asset_platform SET credential_fingerprint=? WHERE tenant_id=? AND id=? AND status='RUNNING' AND attempts=?",fingerprint,s.tenantId(),s.id(),s.attempts()+1); }
    public String attemptedCredential(Sync s) { return jdbc.queryForObject("SELECT credential_fingerprint FROM creative_asset_platform WHERE tenant_id=? AND id=?",String.class,s.tenantId(),s.id()); }
    public List<Asset> abandoned() { return jdbc.query("SELECT * FROM creative_asset WHERE storage_status IN ('WAITING','UPLOADING','VALIDATING','FAILED') AND updated_at<?",ASSET,Timestamp.from(Instant.now().minusSeconds(7200))); }
    public void expired(Asset a) { jdbc.update("UPDATE creative_asset SET storage_status='EXPIRED',error_message='上传会话过期，请重新选择文件',upload_ticket_json=NULL,updated_at=? WHERE tenant_id=? AND id=? AND storage_status IN ('WAITING','UPLOADING','VALIDATING','FAILED')",Timestamp.from(Instant.now()),a.tenantId(),a.id()); }
    public boolean isEngineAsset(String tenant,String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM creative_engine_asset WHERE tenant_id=? AND asset_id=?",Integer.class,tenant,id)>0; }
    public Review aiReview(String tenant,String id) {
        if(isEngineAsset(tenant,id)) return new Review(id,tenant,"NOT_REQUIRED",null,null,"AI 引擎生成素材，无需上传审核",List.of(),null,0,require(tenant,id).createdAt());
        return jdbc.query("SELECT * FROM creative_asset_review WHERE tenant_id=? AND asset_id=?",REVIEW,tenant,id).stream().findFirst().orElse(null);
    }
    public boolean approved(String tenant,String id) { var r=aiReview(tenant,id); return r!=null && Set.of("APPROVED","NOT_REQUIRED").contains(r.status()); }
    public void queueReview(String tenant,String id) {
        var now=Timestamp.from(Instant.now());
        try { jdbc.update("INSERT INTO creative_asset_review(asset_id,tenant_id,status,next_attempt_at,created_at,updated_at) SELECT id,tenant_id,'QUEUED',?,?,? FROM creative_asset WHERE tenant_id=? AND id=? AND storage_status='STORED' AND NOT EXISTS (SELECT 1 FROM creative_engine_asset e WHERE e.asset_id=creative_asset.id AND e.tenant_id=creative_asset.tenant_id)",now,now,now,tenant,id); }
        catch(DuplicateKeyException ignored) { /* Same canonical file shares a single review across accounts. */ }
    }
    public List<Review> dueReviews(int limit) { return jdbc.query("SELECT r.* FROM creative_asset_review r JOIN creative_asset a ON a.id=r.asset_id AND a.tenant_id=r.tenant_id WHERE a.storage_status='STORED' AND r.status IN ('QUEUED','PENDING') AND r.next_attempt_at<=? AND NOT EXISTS (SELECT 1 FROM creative_engine_asset e WHERE e.asset_id=a.id AND e.tenant_id=a.tenant_id) ORDER BY r.next_attempt_at,r.asset_id LIMIT ?",REVIEW,Timestamp.from(Instant.now()),limit); }
    public boolean claimReview(Review r) {
        var now=Instant.now(); return jdbc.update("UPDATE creative_asset_review SET status='RUNNING',attempts=attempts+1,lease_until=?,updated_at=? WHERE tenant_id=? AND asset_id=? AND attempts=? AND status IN ('QUEUED','PENDING') AND next_attempt_at<=? AND NOT EXISTS (SELECT 1 FROM creative_engine_asset e WHERE e.asset_id=creative_asset_review.asset_id AND e.tenant_id=creative_asset_review.tenant_id)",Timestamp.from(now.plusSeconds(600)),Timestamp.from(now),r.tenantId(),r.assetId(),r.attempts(),Timestamp.from(now))==1;
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
        for(var a:jdbc.query("SELECT a.* FROM creative_asset a WHERE a.storage_status='STORED' AND NOT EXISTS (SELECT 1 FROM creative_asset_review r WHERE r.asset_id=a.id AND r.tenant_id=a.tenant_id) AND NOT EXISTS (SELECT 1 FROM creative_engine_asset e WHERE e.asset_id=a.id AND e.tenant_id=a.tenant_id) LIMIT 100",ASSET)) queueReview(a.tenantId(),a.id());
    }
    private static String safe(String value) { return value==null ? null : value.substring(0,Math.min(500,value.length())); }
}
