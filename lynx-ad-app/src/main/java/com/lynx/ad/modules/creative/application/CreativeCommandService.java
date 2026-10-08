package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TaskId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.RequestMeta;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.audit.application.SuccessAuditCommand;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.creative.domain.model.CreativeArtifact;
import com.lynx.ad.modules.creative.domain.model.CreativeMode;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.model.MaterialFeedback;
import com.lynx.ad.modules.creative.domain.model.RegenerationTarget;
import com.lynx.ad.modules.creative.domain.model.CreativeTask;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.StorageObject;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.domain.service.CreativePipelinePolicy;
import com.lynx.ad.modules.creative.domain.service.ObjectKeyPolicy;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CreativeCommandService {

    private static final com.fasterxml.jackson.databind.ObjectMapper FEEDBACK_JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private static String feedbackJson(MaterialFeedback feedback) {
        try { return FEEDBACK_JSON.writeValueAsString(feedback); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    private final CreativeTaskRepository repository;
    private final CreativePipelinePolicy pipelinePolicy = new CreativePipelinePolicy();
    private final com.lynx.ad.agents.creative.CreativeAgent creativeAgent;
    private final AuditRecorder auditRecorder;
    private final StoragePort storagePort;
    private final LynxAdProperties properties;

    public CreativeCommandService(
            CreativeTaskRepository repository,
            com.lynx.ad.agents.creative.CreativeAgent creativeAgent,
            AuditRecorder auditRecorder,
            StoragePort storagePort,
            LynxAdProperties properties) {
        this.repository = repository;
        this.creativeAgent = creativeAgent;
        this.auditRecorder = auditRecorder;
        this.storagePort = storagePort;
        this.properties = properties;
    }

    public TaskId submit(TenantId tenantId, Optional<PlanId> planId, List<QcProductId> productIds, CreativeMode mode) {
        pipelinePolicy.rejectReserved(mode);
        CreativeTask task = CreativeTask.submitStandard(tenantId, planId, productIds);
        repository.save(task);
        creativeAgent.run(task.id());
        recordCreative(tenantId, task.id().value(), "素材任务");
        return task.id();
    }

    public void cancel(TenantId tenantId, TaskId taskId) {
        CreativeTask task = repository.findById(tenantId, taskId).orElseThrow(() -> new BizException(1504, "任务不存在"));
        task.cancel();
        repository.save(task);
        recordCreative(tenantId, taskId.value(), "取消素材");
    }

    public TaskId regenerate(TenantId tenantId, PackageId packageId, QcProductId productId, MaterialType type) {
        return regenerate(tenantId, packageId, productId, type, MaterialFeedback.empty());
    }

    public TaskId regenerate(TenantId tenantId, PackageId packageId, QcProductId productId, MaterialType type, MaterialFeedback feedback) {
        CreativePackage pkg = repository.findPackage(tenantId, packageId).orElseThrow(() -> new BizException(1504, "素材包不存在"));
        pkg.requireMedia(productId, type);
        CreativeTask task = CreativeTask.submitStandard(tenantId, Optional.empty(), List.of(productId));
        task.setRegenerationTarget(new RegenerationTarget(packageId, type, pkg.version()));
        task.setFeedback(feedback);
        repository.save(task);
        creativeAgent.run(task.id());
        recordCreative(tenantId, task.id().value(), "重新生成素材", feedbackJson(task.feedback()));
        return task.id();
    }

    public void deleteMaterial(TenantId tenantId, PackageId packageId, QcProductId productId, MaterialType type) {
        deleteMaterial(tenantId, packageId, productId, type, MaterialFeedback.empty());
    }

    public void deleteMaterial(TenantId tenantId, PackageId packageId, QcProductId productId, MaterialType type, MaterialFeedback feedback) {
        CreativePackage pkg = repository.findPackage(tenantId, packageId).orElseThrow(() -> new BizException(1504, "素材包不存在"));
        int version = pkg.version();
        pkg.changeMedia(productId, type, null);
        repository.savePackageAtVersion(pkg, version);
        recordCreative(tenantId, packageId.value(), "删除素材", feedbackJson(feedback));
    }

    public void confirm(TenantId tenantId, PackageId packageId) {
        CreativePackage pkg = repository.findPackage(tenantId, packageId).orElseThrow(() -> new BizException(1504, "素材包不存在"));
        pkg.confirm();
        repository.savePackage(pkg);
        recordCreative(tenantId, packageId.value(), "确认素材包");
    }

    public void replaceItem(TenantId tenantId, PackageId packageId, QcProductId productId, InputStream file, String filename) {
        CreativePackage pkg = repository.findPackage(tenantId, packageId).orElseThrow(() -> new BizException(1504, "素材包不存在"));
        CreativeArtifact old = pkg.items().stream()
                .filter(i -> i.productId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new BizException(1504, "素材包不存在"));
        byte[] bytes;
        try {
            bytes = file.readAllBytes();
        } catch (Exception ex) {
            throw new BizException(1510, "替换上传文件为空或超过上限");
        }
        long max = 100L * 1024 * 1024;
        if (bytes.length == 0 || bytes.length > max) {
            throw new BizException(1510, "替换上传文件为空或超过上限");
        }
        String key = ObjectKeyPolicy.nextKey(properties.getStorage().getOss().getPrefix(), tenantId, filename);
        String contentType = filename != null && filename.toLowerCase().endsWith(".mp4") ? "video/mp4" : "image/jpeg";
        StorageObject stored = storagePort.put(tenantId, key, new ByteArrayInputStream(bytes), contentType);
        boolean video = filename != null && (filename.toLowerCase().endsWith(".mp4") || filename.toLowerCase().endsWith(".mov"));
        CreativeArtifact next = old.withStorage(
                video ? stored.objectKey() : old.videoObjectKey(),
                video ? old.coverObjectKey() : stored.objectKey(),
                stored.storageMode(),
                filename,
                stored.sizeBytes(),
                old.imageMode(),
                Instant.now()).withMockContent(video ? false : old.mockContent());
        pkg.replaceItem(productId, next);
        repository.savePackage(pkg);
        recordCreative(tenantId, packageId.value(), "替换素材");
    }

    private void recordCreative(TenantId tenantId, String objectId, String label) {
        recordCreative(tenantId, objectId, label, null);
    }

    private void recordCreative(TenantId tenantId, String objectId, String label, String extra) {
        String jti = null;
        try {
            jti = TenantContext.require().sessionId().orElse(null);
        } catch (Exception ignored) {
            // ignore
        }
        com.lynx.ad.common.domain.Ids.UserId uid = null;
        try {
            uid = TenantContext.userId();
        } catch (Exception ignored) {
            // ignore
        }
        auditRecorder.record(new SuccessAuditCommand(
                tenantId,
                uid,
                "商家",
                AuditActionType.CREATIVE,
                "CREATIVE",
                objectId,
                label,
                RequestMeta.ip().orElse(null),
                jti,
                RequestMeta.userAgent().orElse(null),
                extra));
    }
}
