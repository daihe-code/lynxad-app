package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.TaskId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.model.CreativeTask;
import java.util.List;
import java.util.Optional;

public interface CreativeTaskRepository {

    void save(CreativeTask task);

    void savePackage(CreativePackage pkg);

    void savePackageAtVersion(CreativePackage pkg, int expectedVersion);

    Optional<CreativeTask> findById(TenantId tenantId, TaskId id);

    Optional<CreativePackage> findPackage(TenantId tenantId, PackageId id);

    Optional<CreativeTask> findByIdOnly(TaskId id);

    List<CreativeTask> listByTenant(TenantId tenantId);

    List<CreativePackage> listPackagesByTenant(TenantId tenantId);
}
