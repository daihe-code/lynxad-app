package com.lynx.ad.modules.creative.infrastructure;

import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.TaskId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.model.CreativeTask;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
public class InMemoryCreativeTaskRepository implements CreativeTaskRepository {

    private final Map<String, CreativeTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, CreativePackage> packages = new ConcurrentHashMap<>();

    @Override
    public void save(CreativeTask task) {
        if (task.currentPackage().isPresent() && task.regenerationTarget().isPresent()) {
            var target = task.regenerationTarget().orElseThrow();
            var current = packages.get(target.packageId().value());
            if (current == null || current.version() != target.version())
                throw new com.lynx.ad.common.exception.DomainException(1502, "素材已更新，请重试");
        }
        tasks.put(task.id().value(), task);
        task.currentPackage().ifPresent(pkg -> packages.put(pkg.id().value(), pkg));
    }

    @Override
    public Optional<CreativeTask> findById(TenantId tenantId, TaskId id) {
        return Optional.ofNullable(tasks.get(id.value())).filter(t -> t.tenantId().equals(tenantId)).map(this::snapshot);
    }

    @Override
    public Optional<CreativePackage> findPackage(TenantId tenantId, PackageId id) {
        return Optional.ofNullable(packages.get(id.value())).filter(pkg -> tasks.values().stream()
                .anyMatch(task -> task.tenantId().equals(tenantId) && task.currentPackage().map(p -> p.id().equals(id)).orElse(false)))
                .map(pkg -> new CreativePackage(pkg.id(), pkg.version(), pkg.confirmedAt(), pkg.items(), pkg.createdAt()));
    }

    @Override
    public Optional<CreativeTask> findByIdOnly(TaskId id) {
        return Optional.ofNullable(tasks.get(id.value())).map(this::snapshot);
    }

    @Override
    public List<CreativeTask> listByTenant(TenantId tenantId) {
        return tasks.values().stream().filter(t -> t.tenantId().equals(tenantId)).toList();
    }

    @Override
    public void savePackage(CreativePackage pkg) {
        packages.put(pkg.id().value(), pkg);
    }

    @Override
    public void savePackageAtVersion(CreativePackage pkg, int expectedVersion) {
        packages.compute(pkg.id().value(), (id, current) -> {
            if (current == null || current.version() != expectedVersion)
                throw new com.lynx.ad.common.exception.DomainException(1502, "素材已更新，请刷新后重试");
            return pkg;
        });
    }

    private CreativeTask snapshot(CreativeTask task) {
        CreativePackage pkg = task.currentPackage().map(p -> packages.get(p.id().value())).orElse(null);
        var result = new CreativeTask(task.id(), task.tenantId(), task.planId(), task.productIds(), task.mode(),
                task.status(), task.steps(), pkg, task.errorMsg(), task.finishedAt());
        task.regenerationTarget().ifPresent(result::setRegenerationTarget);
        result.setFeedback(task.feedback());
        return result;
    }

    @Override
    public List<CreativePackage> listPackagesByTenant(TenantId tenantId) {
        return List.copyOf(packages.values());
    }
}
