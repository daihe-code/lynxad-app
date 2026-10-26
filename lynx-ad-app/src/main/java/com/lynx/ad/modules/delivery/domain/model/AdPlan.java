package com.lynx.ad.modules.delivery.domain.model;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.delivery.domain.service.PlanStateMachine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AdPlan {

    private final PlanId id;
    private final TenantId tenantId;
    private final AccountId accountId;
    private AwemeId awemeId;
    private PlanName name;
    private PlanStatus status;
    private PlanSource source;
    private DeliverySetting setting;
    private final List<QcProductId> productIds;
    private QcPlanId qcPlanId;
    private String qcStatus;
    private String rejectReason;
    private int publishVersion;
    private IdempotencyKey publishKey;
    private final List<PlanCreativeBinding> bindings;

    public AdPlan(
            PlanId id,
            TenantId tenantId,
            AccountId accountId,
            AwemeId awemeId,
            PlanName name,
            PlanStatus status,
            PlanSource source,
            DeliverySetting setting,
            List<QcProductId> productIds,
            QcPlanId qcPlanId,
            String qcStatus,
            String rejectReason,
            int publishVersion,
            IdempotencyKey publishKey,
            List<PlanCreativeBinding> bindings) {
        this.id = id;
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.awemeId = awemeId;
        this.name = name;
        this.status = status;
        this.source = source;
        this.setting = setting;
        this.productIds = new ArrayList<>(productIds == null ? List.of() : productIds);
        this.qcPlanId = qcPlanId;
        this.qcStatus = qcStatus;
        this.rejectReason = rejectReason;
        this.publishVersion = publishVersion;
        this.publishKey = publishKey;
        this.bindings = new ArrayList<>(bindings == null ? List.of() : bindings);
    }

    public static AdPlan draft(
            TenantId tenantId,
            AccountId accountId,
            AwemeId awemeId,
            PlanName name,
            DeliverySetting setting,
            List<QcProductId> productIds) {
        return new AdPlan(
                new PlanId(UUID.randomUUID().toString()),
                tenantId,
                accountId,
                awemeId,
                name,
                PlanStatus.DRAFT,
                PlanSource.LINGTOU,
                setting,
                productIds,
                null,
                null,
                null,
                0,
                null,
                new ArrayList<>());
    }

    public void rename(PlanName name) {
        this.name = name;
    }

    public void updateSetting(DeliverySetting setting) {
        this.setting = setting;
    }

    public void replaceProducts(List<QcProductId> next) {
        if (status != PlanStatus.DRAFT && status != PlanStatus.REJECTED) {
            throw new DomainException(1403, "当前状态不可修改推广商品");
        }
        productIds.clear();
        productIds.addAll(next == null ? List.of() : next);
    }

    public void publish(CreativePackage pkg) {
        if (pkg == null || !pkg.isConfirmed()) {
            throw new DomainException(1403, "请先确认素材包");
        }
        new PlanStateMachine().transit(this, PlanTransition.PUBLISH);
        this.publishVersion++;
        this.publishKey = IdempotencyKey.ofPublish(id, publishVersion);
    }

    public void acceptExternalId(QcPlanId qcPlanId) {
        this.qcPlanId = qcPlanId;
        new PlanStateMachine().transit(this, PlanTransition.ACCEPT_QC_ID);
    }

    public void markDelivering() {
        new PlanStateMachine().transit(this, PlanTransition.AUDIT_PASS);
        this.qcStatus = "DELIVERING";
    }

    public void markRejected(String reason) {
        this.rejectReason = reason;
        new PlanStateMachine().transit(this, PlanTransition.AUDIT_REJECT);
    }

    public void pause() {
        new PlanStateMachine().transit(this, PlanTransition.DISABLE);
    }

    public void resume() {
        new PlanStateMachine().transit(this, PlanTransition.ENABLE);
    }

    public void finish() {
        new PlanStateMachine().transit(this, PlanTransition.FINISH);
    }

    public void revertToDraft() {
        new PlanStateMachine().transit(this, PlanTransition.REVERT_TO_DRAFT);
    }

    public void bindCreatives(List<PlanCreativeBinding> next) {
        this.bindings.clear();
        if (next != null) {
            this.bindings.addAll(next);
        }
    }

    public void apply(PlanStatus next) {
        if (status == PlanStatus.DELETED && next != PlanStatus.DELETED) return;
        this.status = next;
    }
    public void markDeleted() {
        this.status = PlanStatus.DELETED;
        this.qcStatus = "DELETE";
    }
    public void syncPlatformStatus(String value) { this.qcStatus = value; }

    public PlanStatus status() {
        return status;
    }

    public PlanSource source() {
        return source;
    }

    public int publishVersion() {
        return publishVersion;
    }

    public Optional<IdempotencyKey> publishKey() {
        return Optional.ofNullable(publishKey);
    }

    public PlanId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public AccountId accountId() {
        return accountId;
    }

    public AwemeId awemeId() {
        return awemeId;
    }

    /** Historical list imports may omit this immutable binding; only restore a missing ID. */
    public void restorePlatformAweme(String value) {
        if ("unknown".equals(awemeId.value()) && value != null && value.matches("[1-9][0-9]{0,18}"))
            awemeId = new AwemeId(value);
    }

    public PlanName name() {
        return name;
    }

    public DeliverySetting setting() {
        return setting;
    }

    public List<QcProductId> productIds() {
        return List.copyOf(productIds);
    }

    public Optional<QcPlanId> qcPlanId() {
        return Optional.ofNullable(qcPlanId);
    }

    public String qcStatus() {
        return qcStatus;
    }

    public String rejectReason() {
        return rejectReason;
    }

    public List<PlanCreativeBinding> bindings() {
        return List.copyOf(bindings);
    }
}
