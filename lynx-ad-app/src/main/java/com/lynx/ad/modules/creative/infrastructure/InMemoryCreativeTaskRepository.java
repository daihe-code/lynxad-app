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
        tasks.put(task.id().value(), task);
        task.currentPackage().ifPresent(pkg -> packages.put(pkg.id().value(), pkg));
    }

    @Override
    public Optional<CreativeTask> findById(TenantId tenantId, TaskId id) {
        return Optional.ofNullable(tasks.get(id.value())).filter(t -> t.tenantId().equals(tenantId));
    }

    @Override
    public Optional<CreativePackage> findPackage(TenantId tenantId, PackageId id) {
        return Optional.ofNullable(packages.get(id.value()));
    }

    @Override
    public Optional<CreativeTask> findByIdOnly(TaskId id) {
        return Optional.ofNullable(tasks.get(id.value()));
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
    public List<CreativePackage> listPackagesByTenant(TenantId tenantId) {
        return List.copyOf(packages.values());
    }
}
