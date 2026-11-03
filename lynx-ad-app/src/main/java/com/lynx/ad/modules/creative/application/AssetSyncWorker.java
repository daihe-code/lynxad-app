package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.Qianchuan401ReplayCodes;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.AssetUploadPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssetSyncWorker {
    private final AssetUploadRepository repository;
    private final AssetUploadService files;
    private final QianchuanCredentialFacade credentials;
    private final AssetUploadPort upload;
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final Semaphore slots=new Semaphore(2);
    private final ConcurrentMap<String,AssetUploadRepository.Sync> active=new ConcurrentHashMap<>();
    private AssetSyncReconciliationService reconciliation;
    @org.springframework.beans.factory.annotation.Autowired public void reconciliation(AssetSyncReconciliationService value){reconciliation=value;}
    public AssetSyncWorker(AssetUploadRepository repository, AssetUploadService files, QianchuanCredentialFacade credentials, AssetUploadPort upload) {
        this.repository=repository; this.files=files; this.credentials=credentials; this.upload=upload;
    }
    @Scheduled(fixedDelayString="${lynx.ad.asset-sync.poll-ms:3000}")
    public void poll() {
        repository.recoverExpired();
        repository.resumeRecoverableSyncs();
        for(var sync:repository.due(2)) {
            if (!slots.tryAcquire()) break;
            boolean submitted=false;
            try {
                if (!repository.claim(sync)) continue;
                executor.submit(() -> { try { process(sync); } finally { slots.release(); } });
                submitted=true;
            } finally { if(!submitted) slots.release(); }
        }
    }
    public void process(AssetUploadRepository.Sync sync) {
        active.put(sync.id(),sync);
        Path temp=null;
        boolean dispatched=false;
        try {
            if(!repository.approved(sync.tenantId(),sync.assetId())) {
                repository.fail(sync,"WAITING_REVIEW","AI 审核尚未通过，等待审核结果",0);
                return;
            }
            var tenant=new TenantId(sync.tenantId());
            // Never rely on a web request's ThreadLocal or capture a token from the upload request.
            var credential=credentials.getUsableCredential(tenant,new AccountId(sync.accountId()));
            repository.attemptedCredential(sync,fingerprint(credential));
            upload.checkAvailable();
            var asset=repository.require(sync.tenantId(),sync.assetId());
            var payload=files.syncPayload(tenant,asset,sync.attempts()>0&&sync.errorMessage()!=null&&sync.errorMessage().contains("1511"));
            if(payload!=null)asset=payload;
            temp=files.download(tenant,asset);
            String signature=asset.md5();
            if(repository.isEngineAsset(tenant.value(),asset.id())) {
                // The one download is only a temporary file for Qianchuan's multipart API.
                var digest=java.security.MessageDigest.getInstance("MD5");
                try(var in=java.nio.file.Files.newInputStream(temp)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
                signature=java.util.HexFormat.of().formatHex(digest.digest());
                if(asset.md5()!=null&&!asset.md5().equalsIgnoreCase(signature))throw new BizException(1520,"引擎 OSS 内容与登记校验信息不一致");
                repository.engineSignature(tenant.value(),asset.id(),signature);
            }
            if(!repository.dispatched(sync,credential.advertiserId().value(),signature))return;
            dispatched=true;
            var result=upload.upload(credential,AssetKind.valueOf(asset.kind()),temp,asset.filename(),asset.contentType(),signature,asset.isAigc());
            repository.finish(sync,credential.advertiserId().value(),result.objectId(),result.materialId(),result.requestId());
        } catch (BizException ex) {
            String status;
            if (ex.code()==1211 || ex.code()==1201 || ex.code()==1205 || Qianchuan401ReplayCodes.isAuthExpired(ex)) status="WAITING_AUTH";
            else if (ex.code()==1523 || dispatched && ex.code()!=1511 && ex.code()!=429) status="UNCERTAIN";
            else if (ex.code()==1524) status="PAUSED";
            else if ((ex.code()==429 || ex.code()==1511 || !dispatched&&Set.of(1521,1526).contains(ex.code())) && sync.attempts()<2) status="QUEUED";
            else status="FAILED";
            // Do not persist upstream raw messages which could echo sensitive request fields.
            String error=switch(status) { case "WAITING_AUTH" -> "授权信息不可用，等待授权模块更新"; case "UNCERTAIN" -> "上传结果待后台核查"; case "PAUSED" -> "本地模拟模式未调用千川"; case "QUEUED" -> "后台自动处理（错误码 "+ex.code()+"）"; default -> "平台上传未完成（错误码 " + ex.code() + "）"; };
            repository.fail(sync,status,error,Math.min(300,15 << Math.min(sync.attempts(),4)));
        } catch (Exception ex) { repository.fail(sync,dispatched?"UNCERTAIN":sync.attempts()<2?"QUEUED":"FAILED",dispatched?"上传已发送，等待后台核查":"文件读取未完成，等待后台处理",30); }
        finally { AssetUploadService.remove(temp); active.remove(sync.id(),sync); }
    }
    @Scheduled(fixedDelayString="${lynx.ad.asset-sync.heartbeat-ms:30000}")
    public void heartbeat() { active.values().forEach(repository::heartbeat); }
    @Scheduled(fixedDelayString="${lynx.ad.asset-sync.verify-ms:60000}")
    public void verifyPending() {
        if(reconciliation==null)return;
        for(var sync:repository.uncertainSyncs()) {
            if(!slots.tryAcquire())break;
            try{executor.submit(()->{try{reconciliation.reconcile(new TenantId(sync.tenantId()),sync.assetId(),sync.accountId());}catch(RuntimeException ignored){/* Durable verification lease bounds read-only retries. */}finally{slots.release();}});}
            catch(RejectedExecutionException stopped){slots.release();}
        }
    }
    @Scheduled(fixedDelayString="${lynx.ad.asset-sync.cleanup-ms:3600000}")
    public void cleanup() {
        for(var a:repository.abandoned()) { try { files.cancel(new TenantId(a.tenantId()),a.id()); } catch (Exception ignored) {} }
    }
    @Scheduled(fixedDelayString="${lynx.ad.asset-sync.auth-check-ms:60000}")
    public void resumeAuthorized() {
        files.queueUnassigned();
        for(var sync:repository.waitingAuth()) {
            try {
                var credential=credentials.getUsableCredential(new TenantId(sync.tenantId()),new AccountId(sync.accountId()));
                if (!fingerprint(credential).equals(repository.attemptedCredential(sync)))
                    repository.retry(sync.tenantId(),sync.assetId(),sync.accountId(),false);
            } catch(BizException ignored) { /* Authorization module owns renewal. */ }
        }
    }
    private static String fingerprint(com.lynx.ad.modules.identity.domain.model.Credential credential) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((credential.accessToken()+":"+credential.advertiserId().value()).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @PreDestroy public void stop() { executor.shutdownNow(); }
}
