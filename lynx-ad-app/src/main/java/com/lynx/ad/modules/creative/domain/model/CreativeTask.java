package com.lynx.ad.modules.creative.domain.model;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TaskId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.creative.domain.service.CreativePipelinePolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CreativeTask {

    private final TaskId id;
    private final TenantId tenantId;
    private final Optional<PlanId> planId;
    private final List<QcProductId> productIds;
    private final CreativeMode mode;
    private TaskStatus status;
    private final List<PipelineStep> steps;
    private CreativePackage currentPackage;
    private String errorMsg;
    private Instant finishedAt;
    private RegenerationTarget regenerationTarget;
    private MaterialFeedback feedback = MaterialFeedback.empty();

    public CreativeTask(
            TaskId id,
            TenantId tenantId,
            Optional<PlanId> planId,
            List<QcProductId> productIds,
            CreativeMode mode,
            TaskStatus status,
            List<PipelineStep> steps,
            CreativePackage currentPackage,
            String errorMsg,
            Instant finishedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.planId = planId == null ? Optional.empty() : planId;
        this.productIds = productIds == null ? List.of() : List.copyOf(productIds);
        this.mode = mode;
        this.status = status;
        this.steps = new ArrayList<>(steps == null ? List.of() : steps);
        this.currentPackage = currentPackage;
        this.errorMsg = errorMsg;
        this.finishedAt = finishedAt;
    }

    public static CreativeTask submitStandard(TenantId tenantId, Optional<PlanId> planId, List<QcProductId> productIds) {
        new CreativePipelinePolicy().rejectReserved(CreativeMode.STANDARD);
        return new CreativeTask(
                new TaskId(UUID.randomUUID().toString()),
                tenantId,
                planId,
                productIds,
                CreativeMode.STANDARD,
                TaskStatus.PENDING,
                new ArrayList<>(),
                null,
                null,
                null);
    }

    public void start() {
        if (status != TaskStatus.PENDING) {
            throw new DomainException(1502, "任务不可启动");
        }
        this.status = TaskStatus.RUNNING;
    }

    public void advance(PipelineStep step) {
        if (status != TaskStatus.RUNNING) {
            throw new DomainException(1502, "仅运行中任务可推进步骤");
        }
        CreativePipelinePolicy policy = new CreativePipelinePolicy();
        StepName expected = steps.isEmpty() ? StepName.PARSE : policy.next(steps.get(steps.size() - 1).name());
        if (step.name() != expected) {
            throw new DomainException(1502, "步骤顺序不正确");
        }
        steps.add(step);
    }

    public void succeed(CreativePackage pkg) {
        if (status != TaskStatus.RUNNING) {
            throw new DomainException(1502, "不能从非运行状态直接成功");
        }
        boolean done = steps.stream().anyMatch(s -> s.name() == StepName.DONE && s.status() == StepStatus.SUCCEEDED);
        if (!done) {
            throw new DomainException(1502, "不能跳过步骤直接成功");
        }
        this.currentPackage = pkg;
        this.status = TaskStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
    }

    public void fail(String reason) {
        if (regenerationTarget != null) currentPackage = null;
        this.status = TaskStatus.FAILED;
        this.errorMsg = reason;
        this.finishedAt = Instant.now();
    }

    public void cancel() {
        if (status == TaskStatus.SUCCEEDED) {
            throw new DomainException(1502, "已成功任务不可取消");
        }
        this.status = TaskStatus.CANCELLED;
        this.finishedAt = Instant.now();
    }

    public TaskStatus status() {
        return status;
    }

    public Optional<CreativePackage> currentPackage() {
        return Optional.ofNullable(currentPackage);
    }

    public TaskId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Optional<PlanId> planId() {
        return planId;
    }

    public List<QcProductId> productIds() {
        return productIds;
    }

    public CreativeMode mode() {
        return mode;
    }

    public List<PipelineStep> steps() {
        return List.copyOf(steps);
    }

    public String errorMsg() {
        return errorMsg;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public Optional<RegenerationTarget> regenerationTarget() { return Optional.ofNullable(regenerationTarget); }

    public void setRegenerationTarget(RegenerationTarget target) { this.regenerationTarget = target; }

    public MaterialFeedback feedback() { return feedback; }
    public void setFeedback(MaterialFeedback value) { feedback = value == null ? MaterialFeedback.empty() : value; }
}
