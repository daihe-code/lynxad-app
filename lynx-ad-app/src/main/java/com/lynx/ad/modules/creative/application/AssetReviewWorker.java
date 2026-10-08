package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Review once per stored canonical file; no account credential is sent to the AI engine. */
@Component
public class AssetReviewWorker {
    private final AssetUploadRepository repository;
    private final StoragePort storage;
    private final AssetReviewPort engine;
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final Semaphore slots=new Semaphore(2);
    public AssetReviewWorker(AssetUploadRepository repository,StoragePort storage,AssetReviewPort engine) {
        this.repository=repository; this.storage=storage; this.engine=engine;
    }
    @Scheduled(fixedDelayString="${lynx.ad.asset-review.poll-ms:3000}")
    public void poll() {
        repository.recoverReviews();
        for(var review:repository.dueReviews(2)) {
            if(!slots.tryAcquire()) break;
            boolean submitted=false;
            try {
                if(!repository.claimReview(review)) continue;
                executor.submit(()->{ try { process(review); } finally { slots.release(); } });
                submitted=true;
            } finally { if(!submitted) slots.release(); }
        }
    }
    public void process(AssetUploadRepository.Review review) {
        try {
            var asset=repository.require(review.tenantId(),review.assetId());
            if(!"STORED".equals(asset.storageStatus())) throw new BizException(1525,"文件尚未入库，无法审核");
            var kind=AssetKind.valueOf(asset.kind());
            if(kind==AssetKind.AUDIO && asset.sizeBytes()>10L*1024*1024) throw new BizException(1525,"AI 语音审核支持不超过 10MB 的文件，请压缩后重新上传");
            if(review.createdAt().isBefore(Instant.now().minusSeconds(1800))) throw new BizException(1525,"AI 审核等待超过 30 分钟，请检查引擎任务后重试");
            if(review.reviewId()==null && (kind==AssetKind.VIDEO || kind==AssetKind.IMAGE) && "local".equalsIgnoreCase(asset.storageMode()))
                throw new BizException(1525,"素材已保存到本地，云端审核无法读取本机链接；请重试审核，先转存平台 OSS");
            var result=review.reviewId()==null
                    ? engine.submit(kind,kind==AssetKind.TEXT ? null : RoutedStoragePort.select(storage,asset.storageMode()).getUrl(new TenantId(asset.tenantId()),asset.objectKey()),asset.textContent(),asset.tenantId(),asset.id())
                    : engine.poll(kind,review.reviewId());
            repository.finishReview(review,result);
            if("APPROVED".equals(result.status())) repository.activate(asset.tenantId(),asset.id());
        } catch(BizException ex) { repository.failReview(review,ex.getMessage()); }
        catch(Exception ex) { repository.failReview(review,"AI 审核暂时失败，未放行千川同步，请稍后重试"); }
    }
    @PreDestroy public void stop() { executor.shutdownNow(); }
}
