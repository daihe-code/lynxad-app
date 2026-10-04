package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.TaskId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.api.dto.LibraryVideoVo;
import com.lynx.ad.modules.creative.api.dto.PackageItemVo;
import com.lynx.ad.modules.creative.api.dto.TaskVo;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.model.CreativeTask;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CreativeQueryService {

    private final CreativeTaskRepository repository;
    private final StoragePort storagePort;

    public CreativeQueryService(CreativeTaskRepository repository, StoragePort storagePort) {
        this.repository = repository;
        this.storagePort = storagePort;
    }

    public TaskVo getTask(TenantId tenantId, TaskId taskId) {
        CreativeTask task = repository.findById(tenantId, taskId).orElseThrow(() -> new BizException(1504, "任务不存在"));
        String packageId = task.currentPackage().map(p -> p.id().value()).orElse(null);
        List<PackageItemVo> items = task.currentPackage().map(pkg -> toItems(tenantId, pkg)).orElse(List.of());
        return new TaskVo(
                task.id().value(),
                task.status().name(),
                task.steps().stream().map(s -> s.name().name() + ":" + s.status().name()).toList(),
                packageId,
                task.errorMsg(),
                items);
    }

    public List<LibraryVideoVo> library(TenantId tenantId) {
        return repository.listPackagesByTenant(tenantId).stream()
                .flatMap(p -> p.items().stream())
                .map(i -> new LibraryVideoVo(
                        i.videoId().map(v -> v.value()).orElse(""),
                        i.productId().value(),
                        i.coverImageId().map(c -> c.value()).orElse(null)))
                .toList();
    }

    private List<PackageItemVo> toItems(TenantId tenantId, CreativePackage pkg) {
        return pkg.items().stream()
                .map(i -> new PackageItemVo(
                        i.productId().value(),
                        i.videoObjectKey() == null ? null : storagePort.getUrl(tenantId, i.videoObjectKey()),
                        i.coverObjectKey() == null ? null : storagePort.getUrl(tenantId, i.coverObjectKey())))
                .toList();
    }
}
