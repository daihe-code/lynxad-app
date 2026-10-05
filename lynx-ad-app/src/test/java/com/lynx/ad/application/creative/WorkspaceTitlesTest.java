package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.CreativeQueryService;
import com.lynx.ad.modules.creative.domain.model.*;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkspaceTitlesTest {
    @Test
    void workspaceReturnsGeneratedTitlesAndTenantScopedPreviewUrls() {
        var repository = mock(CreativeTaskRepository.class);
        var storage = mock(StoragePort.class);
        var tenant = new TenantId("tenant-1");
        var id = new TaskId("task-1");
        var title = new AdTitle("商品的真实生成标题", TitleType.CUSTOM);
        var artifact = new CreativeArtifact(new QcProductId("product-1"), List.of(title), Optional.empty(), Optional.empty())
                .withStorage("video.mp4", "cover.jpg", "LOCAL", "video.mp4", 100L, "VIDEO_VERTICAL", null);
        var task = new CreativeTask(id, tenant, Optional.empty(), List.of(), CreativeMode.STANDARD,
                TaskStatus.SUCCEEDED, List.of(), CreativePackage.of(List.of(artifact)), null, null);
        when(repository.findById(tenant, id)).thenReturn(Optional.of(task));
        when(storage.getUrl(tenant, "video.mp4")).thenReturn("/video-preview");
        when(storage.getUrl(tenant, "cover.jpg")).thenReturn("/cover-preview");
        var result = new CreativeQueryService(repository, storage).getTask(tenant, id);
        assertEquals(List.of("商品的真实生成标题"), result.items().getFirst().titles());
        assertEquals("/video-preview", result.items().getFirst().playUrl());
        assertEquals("/cover-preview", result.items().getFirst().posterUrl());
        assertThrows(BizException.class, () -> new CreativeQueryService(repository, storage)
                .getTask(new TenantId("other-tenant"), id));
    }
}
