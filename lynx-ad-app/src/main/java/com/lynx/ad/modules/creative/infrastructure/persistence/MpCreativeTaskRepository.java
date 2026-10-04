package com.lynx.ad.modules.creative.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TaskId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.model.CreativeMode;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.model.CreativeTask;
import com.lynx.ad.modules.creative.domain.model.TaskStatus;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MpCreativeTaskRepository implements CreativeTaskRepository {

    private final CreativeTaskMapper taskMapper;
    private final CreativePackageMapper packageMapper;
    private final CreativeJson json;

    public MpCreativeTaskRepository(
            CreativeTaskMapper taskMapper, CreativePackageMapper packageMapper, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.packageMapper = packageMapper;
        this.json = new CreativeJson(objectMapper);
    }

    @Override
    @Transactional
    public void save(CreativeTask task) {
        CreativeTaskPo po = taskMapper.selectById(task.id().value());
        boolean insert = po == null;
        if (insert) {
            po = new CreativeTaskPo();
            po.setId(task.id().value());
            po.setCreatedAt(Instant.now());
        }
        po.setTenantId(task.tenantId().value());
        po.setPlanId(task.planId().map(PlanId::value).orElse(null));
        po.setStatus(task.status().name());
        po.setMode(task.mode().name());
        po.setErrorMsg(task.errorMsg());
        po.setFinishedAt(task.finishedAt());
        po.setProductIdsJson(json.productIds(task.productIds()));
        po.setStepsJson(json.steps(task.steps()));
        if (task.currentPackage().isPresent()) {
            CreativePackage pkg = task.currentPackage().orElseThrow();
            upsertPackage(pkg, task.tenantId(), task.id());
            po.setPackageId(pkg.id().value());
        }
        if (insert) {
            taskMapper.insert(po);
        } else {
            taskMapper.updateById(po);
        }
    }

    @Override
    @Transactional
    public void savePackage(CreativePackage pkg) {
        CreativePackagePo existing = packageMapper.selectById(pkg.id().value());
        if (existing == null) {
            throw new IllegalStateException("素材包不存在");
        }
        existing.setVersion(pkg.version());
        existing.setConfirmedAt(pkg.confirmedAt());
        existing.setArtifactsJson(json.artifacts(pkg.items()));
        packageMapper.updateById(existing);
    }

    @Override
    public Optional<CreativeTask> findById(TenantId tenantId, TaskId id) {
        return Optional.ofNullable(taskMapper.selectById(id.value()))
                .filter(po -> tenantId.value().equals(po.getTenantId()))
                .map(this::toDomain);
    }

    @Override
    public Optional<CreativePackage> findPackage(TenantId tenantId, PackageId id) {
        return Optional.ofNullable(packageMapper.selectById(id.value()))
                .filter(po -> tenantId.value().equals(po.getTenantId()))
                .map(this::toPackage);
    }

    @Override
    public Optional<CreativeTask> findByIdOnly(TaskId id) {
        return Optional.ofNullable(taskMapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<CreativeTask> listByTenant(TenantId tenantId) {
        return taskMapper
                .selectList(new LambdaQueryWrapper<CreativeTaskPo>().eq(CreativeTaskPo::getTenantId, tenantId.value()))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<CreativePackage> listPackagesByTenant(TenantId tenantId) {
        return packageMapper
                .selectList(new LambdaQueryWrapper<CreativePackagePo>()
                        .eq(CreativePackagePo::getTenantId, tenantId.value()))
                .stream()
                .map(this::toPackage)
                .toList();
    }

    private void upsertPackage(CreativePackage pkg, TenantId tenantId, TaskId taskId) {
        CreativePackagePo po = packageMapper.selectById(pkg.id().value());
        boolean insert = po == null;
        if (insert) {
            po = new CreativePackagePo();
            po.setId(pkg.id().value());
            po.setCreatedAt(pkg.createdAt() == null ? Instant.now() : pkg.createdAt());
        }
        po.setTenantId(tenantId.value());
        po.setTaskId(taskId.value());
        po.setVersion(pkg.version());
        po.setConfirmedAt(pkg.confirmedAt());
        po.setArtifactsJson(json.artifacts(pkg.items()));
        if (insert) {
            packageMapper.insert(po);
        } else {
            packageMapper.updateById(po);
        }
    }

    private CreativeTask toDomain(CreativeTaskPo po) {
        CreativePackage pkg = null;
        if (po.getPackageId() != null && !po.getPackageId().isBlank()) {
            pkg = Optional.ofNullable(packageMapper.selectById(po.getPackageId())).map(this::toPackage).orElse(null);
        }
        if (pkg == null) {
            CreativePackagePo latest = packageMapper.selectOne(new LambdaQueryWrapper<CreativePackagePo>()
                    .eq(CreativePackagePo::getTaskId, po.getId())
                    .orderByDesc(CreativePackagePo::getVersion)
                    .last("limit 1"));
            pkg = latest == null ? null : toPackage(latest);
        }
        return new CreativeTask(
                new TaskId(po.getId()),
                new TenantId(po.getTenantId()),
                Optional.ofNullable(po.getPlanId()).map(PlanId::new),
                json.readProductIds(po.getProductIdsJson()),
                CreativeMode.valueOf(po.getMode()),
                TaskStatus.valueOf(po.getStatus()),
                json.readSteps(po.getStepsJson()),
                pkg,
                po.getErrorMsg(),
                po.getFinishedAt());
    }

    private CreativePackage toPackage(CreativePackagePo po) {
        return new CreativePackage(
                new PackageId(po.getId()),
                po.getVersion() == null ? 1 : po.getVersion(),
                po.getConfirmedAt(),
                json.readArtifacts(po.getArtifactsJson()),
                po.getCreatedAt());
    }
}
