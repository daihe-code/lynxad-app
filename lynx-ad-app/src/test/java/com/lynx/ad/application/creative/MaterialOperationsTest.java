package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.agents.creative.CreativeAgent;
import com.lynx.ad.aiclient.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.creative.application.CreativeCommandService;
import com.lynx.ad.modules.creative.domain.model.*;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.InMemoryCreativeTaskRepository;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MaterialOperationsTest {
    final TenantId tenant = new TenantId("material-owner");
    final QcProductId product = new QcProductId("product-1");
    final InMemoryCreativeTaskRepository repository = new InMemoryCreativeTaskRepository();
    final LynxAdProperties properties = new LynxAdProperties();
    final CreativeAgent dispatcher = mock(CreativeAgent.class);
    final StoragePort storage = mock(StoragePort.class);
    final CreativeCommandService service = new CreativeCommandService(repository, dispatcher,
            mock(AuditRecorder.class), storage, properties);

    CreativePackage seed() {
        var items = List.of(artifact(product, "video-old", "image-old"),
                artifact(new QcProductId("product-2"), "video-other", "image-other"));
        var pkg = CreativePackage.of(items);
        pkg.confirm();
        repository.save(new CreativeTask(new TaskId("original"), tenant, Optional.empty(), List.of(product),
                CreativeMode.STANDARD, TaskStatus.SUCCEEDED, List.of(), pkg, null, null));
        return pkg;
    }

    CreativeArtifact artifact(QcProductId id, String video, String cover) {
        return new CreativeArtifact(id, List.of(new AdTitle("原有标题", TitleType.CUSTOM)),
                Optional.of(new VideoId("vid-" + id.value())), Optional.of(new CoverImageId("cov-" + id.value())))
                .withStorage(video, cover, "LOCAL", "original.mp4", 5L, "VIDEO_VERTICAL", null);
    }

    @SuppressWarnings("unchecked")
    CreativeAgent worker(ChatCompletionClient chat) {
        var video = mock(VideoGenClient.class);
        when(video.submit(any())).thenReturn("job");
        when(video.poll(any())).thenReturn(new VideoJobStatus("job", "SUCCESS", ""));
        when(storage.put(any(), any(), any(), any())).thenAnswer(call ->
                new StorageObject(call.getArgument(1), "LOCAL", 10L, call.getArgument(3)));
        return new CreativeAgent(repository, properties, mock(InboxCommandService.class), storage,
                chat, text -> new byte[0], video, mock(ObjectProvider.class));
    }

    @Test void regeneratesOnlyChosenVideoWithoutCreatingAnotherPackage() {
        var original = seed();
        var id = service.regenerate(tenant, original.id(), product, MaterialType.VIDEO);
        verify(dispatcher).run(id);
        assertEquals(List.of(product), repository.findById(tenant, id).orElseThrow().productIds());
        worker((name, vars) -> "新标题").runNow(id);
        var task = repository.findById(tenant, id).orElseThrow();
        assertEquals(TaskStatus.SUCCEEDED, task.status());
        var pkg = repository.findPackage(tenant, original.id()).orElseThrow();
        var item = pkg.items().stream().filter(i -> i.productId().equals(product)).findFirst().orElseThrow();
        assertNotEquals("video-old", item.videoObjectKey());
        assertEquals("image-old", item.coverObjectKey());
        assertEquals("原有标题", item.titles().getFirst().text());
        assertEquals(original.items().get(1), pkg.items().stream().filter(i -> !i.productId().equals(product)).findFirst().orElseThrow());
        assertFalse(pkg.isConfirmed());
        assertEquals(original.version() + 1, pkg.version());
        assertEquals(1, repository.listPackagesByTenant(tenant).size());
        verify(storage, times(1)).put(any(), any(), any(), eq("video/mp4"));
        verify(storage, never()).put(any(), any(), any(), eq("image/jpeg"));
    }

    @Test void regenerationFeedbackReachesTheWorkerAndRejectsOverlongDescriptions() {
        var original = seed();
        var feedback = new MaterialFeedback(List.of("画面质量不佳", "文案不符合预期"), "突出商品使用场景");
        var id = service.regenerate(tenant, original.id(), product, MaterialType.VIDEO, feedback);
        assertEquals(feedback, repository.findById(tenant, id).orElseThrow().feedback());
        worker((name, vars) -> {
            assertEquals(feedback.reasons(), vars.get("regenerationReasons"));
            assertEquals(feedback.description(), vars.get("expectedDescription"));
            return "title";
        }).runNow(id);
        assertEquals(TaskStatus.SUCCEEDED, repository.findById(tenant, id).orElseThrow().status());
        assertThrows(BizException.class, () -> new MaterialFeedback(List.of(), "长".repeat(51)));
        assertThrows(BizException.class, () -> new MaterialFeedback(java.util.Collections.nCopies(11, "原因"), ""));
    }

    @Test void deletingImagePreservesVideoAndPreventsEmptyMaterialConfirmation() {
        var original = seed();
        service.deleteMaterial(tenant, original.id(), product, MaterialType.IMAGE);
        var pkg = repository.findPackage(tenant, original.id()).orElseThrow();
        var item = pkg.items().stream().filter(i -> i.productId().equals(product)).findFirst().orElseThrow();
        assertEquals("video-old", item.videoObjectKey());
        assertNull(item.coverObjectKey());
        assertTrue(item.coverImageId().isEmpty());
        assertFalse(pkg.isConfirmed());
        service.deleteMaterial(tenant, original.id(), product, MaterialType.VIDEO);
        assertThrows(DomainException.class, () -> repository.findPackage(tenant, original.id()).orElseThrow().confirm());
    }

    @Test void failedRegenerationKeepsOriginalAndOtherTenantCannotMutateIt() {
        var original = seed();
        assertThrows(BizException.class, () -> service.deleteMaterial(new TenantId("other"), original.id(), product, MaterialType.VIDEO));
        var id = service.regenerate(tenant, original.id(), product, MaterialType.IMAGE);
        assertThrows(BizException.class, () -> worker((name, vars) -> { throw new BizException(8002, "AI failure"); }).runNow(id));
        assertEquals(TaskStatus.FAILED, repository.findById(tenant, id).orElseThrow().status());
        assertEquals(original.items(), repository.findPackage(tenant, original.id()).orElseThrow().items());
        assertEquals(original.version(), repository.findPackage(tenant, original.id()).orElseThrow().version());
    }

    @Test void regenerationDoesNotRestoreAMaterialDeletedWhileItWasRunning() {
        var original = seed();
        var id = service.regenerate(tenant, original.id(), product, MaterialType.VIDEO);
        service.deleteMaterial(tenant, original.id(), product, MaterialType.VIDEO);
        assertThrows(BizException.class, () -> worker((name, vars) -> "title").runNow(id));
        assertEquals(TaskStatus.FAILED, repository.findById(tenant, id).orElseThrow().status());
        assertNull(repository.findPackage(tenant, original.id()).orElseThrow().items().stream()
                .filter(i -> i.productId().equals(product)).findFirst().orElseThrow().videoObjectKey());
    }
}
