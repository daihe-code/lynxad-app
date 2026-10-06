package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.agents.creative.CreativeAgent;
import com.lynx.ad.aiclient.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.creative.application.CreativeCommandService;
import com.lynx.ad.modules.creative.application.CreativeQueryService;
import com.lynx.ad.modules.creative.domain.model.*;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;

class AsyncGenerationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncConfig {}

    @Test
    void submissionReturnsWhileAiIsBlockedAndTaskProgressCanBeQueried() throws Exception {
        var repository = mock(CreativeTaskRepository.class);
        var saved = new AtomicReference<CreativeTask>();
        var aiEntered = new CountDownLatch(1);
        var releaseAi = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        doAnswer(call -> {
            CreativeTask task = call.getArgument(0);
            saved.set(task);
            if (task.status() == TaskStatus.SUCCEEDED) finished.countDown();
            return null;
        }).when(repository).save(any());
        when(repository.findByIdOnly(any())).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(repository.findById(any(), any())).thenAnswer(call -> Optional.ofNullable(saved.get())
                .filter(task -> task.tenantId().equals(call.getArgument(0))));
        ChatCompletionClient chat = (name, vars) -> {
            if (name.equals("title")) {
                aiEntered.countDown();
                try {
                    if (!releaseAi.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("AI test timed out");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }
            return "测试素材";
        };
        var video = mock(VideoGenClient.class);
        when(video.submit(any())).thenReturn("video-job");
        when(video.poll(any())).thenReturn(new VideoJobStatus("video-job", "SUCCESS", ""));
        var storage = mock(StoragePort.class);
        when(storage.put(any(), any(), any(), any())).thenAnswer(call ->
                new StorageObject(call.getArgument(1), "LOCAL", 1L, call.getArgument(3)));
        var properties = new LynxAdProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<MaterialUploadPort> upload = mock(ObjectProvider.class);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AsyncConfig.class);
            context.registerBean(CreativeAgent.class, () -> new CreativeAgent(repository, properties,
                    mock(InboxCommandService.class), storage, chat, text -> new byte[0], video, upload));
            context.refresh();
            var service = new CreativeCommandService(repository, context.getBean(CreativeAgent.class),
                    mock(AuditRecorder.class), storage, properties);
            var tenant = new TenantId("async-tenant");
            try {
                var id = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> service.submit(tenant,
                        Optional.empty(), List.of(new QcProductId("async-product")), CreativeMode.STANDARD));
                assertTrue(aiEntered.await(2, TimeUnit.SECONDS));
                var progress = new CreativeQueryService(repository, storage).getTask(tenant, id);
                assertEquals("RUNNING", progress.status());
                assertEquals(List.of("PARSE:SUCCEEDED"), progress.steps());
                assertNull(progress.packageId());
                releaseAi.countDown();
                assertTrue(finished.await(3, TimeUnit.SECONDS));
                var result = new CreativeQueryService(repository, storage).getTask(tenant, id);
                assertEquals("SUCCEEDED", result.status());
                assertEquals(9, result.steps().size());
                assertEquals(1, result.items().size());
            } finally { releaseAi.countDown(); }
        }
    }
}
