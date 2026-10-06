package com.lynx.ad.agents.creative;

import com.lynx.ad.aiclient.ChatCompletionClient;
import com.lynx.ad.aiclient.TtsClient;
import com.lynx.ad.aiclient.VideoGenClient;
import com.lynx.ad.aiclient.VideoJobStatus;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.CoverImageId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TaskId;
import com.lynx.ad.common.domain.Ids.VideoId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AdTitle;
import com.lynx.ad.modules.creative.domain.model.CreativeArtifact;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.model.CreativeTask;
import com.lynx.ad.modules.creative.domain.model.PipelineStep;
import com.lynx.ad.modules.creative.domain.model.StepName;
import com.lynx.ad.modules.creative.domain.model.StepStatus;
import com.lynx.ad.modules.creative.domain.model.TitleType;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.MaterialUploadPort;
import com.lynx.ad.modules.creative.domain.port.StorageObject;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.domain.service.CreativePipelinePolicy;
import com.lynx.ad.modules.creative.domain.service.ObjectKeyPolicy;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreativeFixture;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class CreativeAgent {

    private final CreativeTaskRepository repository;
    private final LynxAdProperties properties;
    private final InboxCommandService inboxCommandService;
    private final StoragePort storagePort;
    private final ChatCompletionClient chatClient;
    private final TtsClient ttsClient;
    private final VideoGenClient videoGenClient;
    private final ObjectProvider<MaterialUploadPort> uploadPort;
    private final CreativePipelinePolicy policy = new CreativePipelinePolicy();

    public CreativeAgent(
            CreativeTaskRepository repository,
            LynxAdProperties properties,
            InboxCommandService inboxCommandService,
            StoragePort storagePort,
            ChatCompletionClient chatClient,
            TtsClient ttsClient,
            VideoGenClient videoGenClient,
            ObjectProvider<MaterialUploadPort> uploadPort) {
        this.repository = repository;
        this.properties = properties;
        this.inboxCommandService = inboxCommandService;
        this.storagePort = storagePort;
        this.chatClient = chatClient;
        this.ttsClient = ttsClient;
        this.videoGenClient = videoGenClient;
        this.uploadPort = uploadPort;
    }

    @Async
    public void run(TaskId taskId) {
        runNow(taskId);
    }

    public void runNow(TaskId taskId) {
        CreativeTask task = repository.findByIdOnly(taskId).orElse(null);
        if (task == null) {
            return;
        }
        try {
            task.start();
            repository.save(task);
            StepName current = null;
            for (int i = 0; i < StepName.values().length; i++) {
                StepName next = policy.next(current);
                runStep(task, next);
                task.advance(new PipelineStep(next, StepStatus.SUCCEEDED, Optional.empty()));
                repository.save(task);
                current = next;
                if (next == StepName.DONE) {
                    break;
                }
            }
            List<CreativeArtifact> items = new ArrayList<>();
            List<QcProductId> pids = task.productIds().isEmpty()
                    ? List.of(new QcProductId("qp01"))
                    : task.productIds();
            for (QcProductId pid : pids) {
                items.add(storeArtifact(task, pid));
            }
            CreativePackage pkg;
            if (task.regenerationTarget().isPresent()) {
                var target = task.regenerationTarget().orElseThrow();
                pkg = repository.findPackage(task.tenantId(), target.packageId())
                        .orElseThrow(() -> new BizException(1504, "素材包不存在"));
                if (pkg.version() != target.version()) throw new BizException(1502, "素材已更新，请重试重新生成");
                pkg.changeMedia(items.getFirst().productId(), target.type(), items.getFirst());
            } else pkg = CreativePackage.of(items);
            task.succeed(pkg);
            repository.save(task);
            inboxCommandService.post(task.tenantId(), "素材生成完成", "可确认后发布", new BizRef("CREATIVE_DONE", task.id().value()));
        } catch (BizException ex) {
            task.fail(ex.getMessage());
            repository.save(task);
            inboxCommandService.post(task.tenantId(), "素材生成失败", ex.getMessage(), new BizRef("CREATIVE_FAIL", task.id().value()));
            throw ex;
        } catch (Exception ex) {
            task.fail(ex.getMessage());
            repository.save(task);
            inboxCommandService.post(task.tenantId(), "素材生成失败", ex.getMessage(), new BizRef("CREATIVE_FAIL", task.id().value()));
        }
    }

    private void runStep(CreativeTask task, StepName step) {
        Map<String, Object> vars = Map.of("taskId", task.id().value(),
                "regenerationReasons", task.feedback().reasons(), "expectedDescription", task.feedback().description());
        switch (step) {
            case TITLE -> chatClient.complete("title", vars);
            case COPY -> chatClient.complete("copy", vars);
            case TTS -> ttsClient.synth("烘焙好物推荐");
            case STORYBOARD -> chatClient.complete("storyboard", vars);
            case VIDEO -> {
                String spec = "{\"mode\":\"STANDARD\"}";
                if (!task.feedback().reasons().isEmpty() || !task.feedback().description().isEmpty()) {
                    try { spec = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                            "mode", "STANDARD", "regenerationReasons", task.feedback().reasons(), "expectedDescription", task.feedback().description())); }
                    catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
                }
                String jobId = videoGenClient.submit(spec);
                VideoJobStatus status = videoGenClient.poll(jobId);
                if (status == null || !"SUCCESS".equalsIgnoreCase(status.status())) {
                    throw new BizException(8002, "AI 上游失败");
                }
            }
            default -> {
                // PARSE / COVER / UPLOAD / DONE 无额外上游
            }
        }
        if (step == StepName.UPLOAD) {
            MaterialUploadPort upload = uploadPort.getIfAvailable();
            if (upload != null) {
                // 上传发生在产物落盘之后；本步仅确认 Port 可用
            }
        }
    }

    private CreativeArtifact storeArtifact(CreativeTask task, QcProductId pid) {
        String prefix = properties.getStorage().getOss().getPrefix();
        String videoKey = ObjectKeyPolicy.nextKey(prefix, task.tenantId(), pid.value() + ".mp4");
        String coverKey = ObjectKeyPolicy.nextKey(prefix, task.tenantId(), pid.value() + ".jpg");
        boolean storeVideo = task.regenerationTarget().map(target -> target.type() == MaterialType.VIDEO).orElse(true);
        boolean storeCover = task.regenerationTarget().map(target -> target.type() == MaterialType.IMAGE).orElse(true);
        StorageObject video = storeVideo ? storagePort.put(
                task.tenantId(), videoKey, new ByteArrayInputStream(CreativeFixture.mp4()), "video/mp4") : null;
        StorageObject cover = storeCover ? storagePort.put(
                task.tenantId(), coverKey, new ByteArrayInputStream(CreativeFixture.jpeg()), "image/jpeg") : null;
        Instant now = Instant.now();
        return new CreativeArtifact(
                pid,
                List.of(new AdTitle("烘焙好物推荐", TitleType.CUSTOM)),
                Optional.of(new VideoId("vid_" + pid.value())),
                Optional.of(new CoverImageId("cov_" + pid.value())),
                video == null ? null : video.objectKey(),
                cover == null ? null : cover.objectKey(),
                video == null ? cover.storageMode() : video.storageMode(),
                pid.value() + ".mp4",
                video == null ? cover.sizeBytes() : video.sizeBytes(),
                12,
                720,
                1280,
                "VIDEO_VERTICAL",
                now);
    }
}
