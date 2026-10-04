package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.agents.plan.PlanAgent;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.idempotency.IdempotencyService;
import com.lynx.ad.modules.agentops.domain.model.ActionType;
import com.lynx.ad.modules.agentops.domain.model.AgentAction;
import com.lynx.ad.modules.agentops.domain.port.AgentActionRepository;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.dataops.domain.model.AuditVerdict;
import com.lynx.ad.modules.delivery.api.dto.PlanDraftRequest;
import com.lynx.ad.modules.delivery.api.dto.PlanDraftResponse;
import com.lynx.ad.modules.delivery.api.dto.PlanVo;
import com.lynx.ad.modules.delivery.api.dto.PublishResponse;
import com.lynx.ad.modules.creative.domain.model.CreativeArtifact;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.AudienceJson;
import com.lynx.ad.modules.delivery.domain.model.PlanCreativeBinding;
import com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore;
import com.lynx.ad.modules.delivery.domain.model.DeliverySetting;
import com.lynx.ad.modules.delivery.domain.model.PlanAction;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import com.lynx.ad.modules.delivery.domain.model.RoiGoal;
import com.lynx.ad.modules.delivery.domain.model.Schedule;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.port.AdStatusPort;
import com.lynx.ad.modules.delivery.domain.port.SuggestBudgetPort;
import com.lynx.ad.modules.delivery.domain.port.SuggestRoiPort;
import com.lynx.ad.modules.delivery.domain.service.PlanNamePolicy;
import com.lynx.ad.modules.delivery.domain.service.PublishPolicy;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.audit.application.SuccessAuditCommand;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.common.security.RequestMeta;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanCommandService {

    private final AdPlanRepository planRepository;
    private final PlanNamePolicy namePolicy = new PlanNamePolicy();
    private final PublishPolicy publishPolicy = new PublishPolicy();
    private final SuggestBudgetPort suggestBudgetPort;
    private final SuggestRoiPort suggestRoiPort;
    private final IdempotencyService idempotencyService;
    private final PlanAgent planAgent;
    private final CreativeTaskRepository creativeTaskRepository;
    private final BillingTrialService billingTrialService;
    private final AdStatusPort adStatusPort;
    private final QianchuanCredentialFacade credentialFacade;
    private final AgentActionRepository agentActionRepository;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;
    private final AdPlanCreativeBindingStore bindingStore;

    public PlanCommandService(
            AdPlanRepository planRepository,
            SuggestBudgetPort suggestBudgetPort,
            SuggestRoiPort suggestRoiPort,
            IdempotencyService idempotencyService,
            PlanAgent planAgent,
            CreativeTaskRepository creativeTaskRepository,
            BillingTrialService billingTrialService,
            AdStatusPort adStatusPort,
            QianchuanCredentialFacade credentialFacade,
            AgentActionRepository agentActionRepository,
            ObjectMapper objectMapper,
            AuditRecorder auditRecorder,
            AdPlanCreativeBindingStore bindingStore) {
        this.planRepository = planRepository;
        this.suggestBudgetPort = suggestBudgetPort;
        this.suggestRoiPort = suggestRoiPort;
        this.idempotencyService = idempotencyService;
        this.planAgent = planAgent;
        this.creativeTaskRepository = creativeTaskRepository;
        this.billingTrialService = billingTrialService;
        this.adStatusPort = adStatusPort;
        this.credentialFacade = credentialFacade;
        this.agentActionRepository = agentActionRepository;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
        this.bindingStore = bindingStore;
    }

    @Transactional
    public PlanDraftResponse createDraft(TenantId tenantId, PlanDraftRequest cmd) {
        billingTrialService.assertWritable(tenantId, Instant.now(), WriteAction.SAVE_PLAN);
        PlanName name = new PlanName(cmd.name());
        namePolicy.assertUnique(planRepository.existsUnfinishedName(tenantId, new AwemeId(cmd.awemeId()), name, Optional.empty()));
        DeliverySetting setting = settingOf(cmd);
        List<QcProductId> pids = cmd.productIds() == null ? List.of() : cmd.productIds().stream().map(QcProductId::new).toList();
        AdPlan plan = AdPlan.draft(tenantId, new AccountId(cmd.accountId()), new AwemeId(cmd.awemeId()), name, setting, pids);
        planRepository.save(plan);
        recordPlan(tenantId, plan.id().value(), plan.name().text());
        Credential cred = credentialFacade.get(new AccountId(cmd.accountId()));
        var budget = suggestBudgetPort.suggest(cred, plan.awemeId(), setting.dailyBudget());
        var roi = suggestRoiPort.suggest(cred, plan.awemeId());
        return new PlanDraftResponse(
                toVo(plan),
                budget.isEmpty() ? setting.dailyBudget().amount() : budget.get(0).amount().amount(),
                roi.map(r -> r.roi()).orElse(null));
    }

    @Transactional
    public PlanVo updateDraft(TenantId tenantId, PlanId id, PlanDraftRequest cmd) {
        billingTrialService.assertWritable(tenantId, Instant.now(), WriteAction.SAVE_PLAN);
        AdPlan plan = require(tenantId, id);
        PlanName name = new PlanName(cmd.name());
        namePolicy.assertUnique(planRepository.existsUnfinishedName(tenantId, plan.awemeId(), name, Optional.of(id)));
        plan.rename(name);
        plan.updateSetting(settingOf(cmd));
        planRepository.save(plan);
        recordPlan(tenantId, plan.id().value(), plan.name().text());
        return toVo(plan);
    }

    @Transactional
    public PublishResponse publish(TenantId tenantId, PlanId id, IdempotencyKey headerKey) {
        billingTrialService.assertWritable(tenantId, Instant.now(), WriteAction.PUBLISH);
        AdPlan plan = require(tenantId, id);
        IdempotencyKey key = headerKey == null ? publishPolicy.nextKey(plan) : headerKey;
        Optional<String> replay = idempotencyService.findReplay(tenantId, key);
        if (replay.isPresent()) {
            try {
                return objectMapper.readValue(replay.get(), PublishResponse.class);
            } catch (Exception ex) {
                throw new BizException(9999, "幂等回放失败");
            }
        }
        boolean confirmed = cmdPackageConfirmed(tenantId, plan);
        publishPolicy.assertPublishable(plan, true, confirmed, Math.max(1, plan.productIds().size()));
        CreativePackage pkg = resolvePackage(tenantId, plan);
        if (!pkg.isConfirmed()) {
            pkg.confirm();
        }
        if (plan.status() == com.lynx.ad.modules.delivery.domain.model.PlanStatus.REJECTED) {
            plan.revertToDraft();
        }
        plan.publish(pkg);
        planRepository.save(plan);
        bindingStore.upsert(plan.id(), toBindings(pkg));
        planAgent.publishNow(plan.id());
        recordPlan(tenantId, plan.id().value(), plan.name().text());
        AdPlan latest = require(tenantId, id);
        PublishResponse resp = new PublishResponse(latest.qcPlanId().map(QcPlanId::value).orElse(null), latest.status().name());
        try {
            idempotencyService.saveReplay(tenantId, key, objectMapper.writeValueAsString(resp));
        } catch (Exception ignored) {
            // ignore
        }
        return resp;
    }

    @Transactional
    public PlanVo act(TenantId tenantId, PlanId id, PlanAction action) {
        AdPlan plan = require(tenantId, id);
        String before = plan.status().name();
        if (plan.qcPlanId().isPresent()) {
            adStatusPort.updateStatus(credentialFacade.get(plan.accountId()), plan.qcPlanId().get(), action);
        }
        switch (action) {
            case DISABLE -> plan.pause();
            case ENABLE -> plan.resume();
            case FINISH -> plan.finish();
        }
        planRepository.save(plan);
        ActionType type = switch (action) {
            case ENABLE -> ActionType.MANUAL_ENABLE;
            case DISABLE -> ActionType.MANUAL_DISABLE;
            case FINISH -> ActionType.MANUAL_FINISH;
        };
        agentActionRepository.saveIgnoreDup(AgentAction.recordManual(tenantId, id, type, before, plan.status().name()));
        recordPlan(tenantId, plan.id().value(), plan.name().text());
        return toVo(plan);
    }

    public void acceptExternalId(TenantId tenantId, PlanId id, QcPlanId qcPlanId) {
        AdPlan plan = require(tenantId, id);
        plan.acceptExternalId(qcPlanId);
        planRepository.save(plan);
    }

    public void markAudit(TenantId tenantId, PlanId id, AuditVerdict verdict, String reason) {
        AdPlan plan = require(tenantId, id);
        if (verdict == AuditVerdict.PASS) {
            plan.markDelivering();
        } else if (verdict == AuditVerdict.REJECT) {
            plan.markRejected(reason);
        }
        planRepository.save(plan);
    }

    private boolean cmdPackageConfirmed(TenantId tenantId, AdPlan plan) {
        return creativeTaskRepository.listByTenant(tenantId).stream()
                .anyMatch(t -> t.currentPackage().isPresent() && t.currentPackage().get().isConfirmed());
    }

    private CreativePackage resolvePackage(TenantId tenantId, AdPlan plan) {
        return creativeTaskRepository.listByTenant(tenantId).stream()
                .filter(t -> t.planId().map(p -> p.equals(plan.id())).orElse(false))
                .flatMap(t -> t.currentPackage().stream())
                .findFirst()
                .or(() -> creativeTaskRepository.findPackage(tenantId, new PackageId(plan.id().value())))
                .orElseGet(() -> stubConfirmed(plan));
    }

    private List<PlanCreativeBinding> toBindings(CreativePackage pkg) {
        return pkg.items().stream()
                .map(item -> new PlanCreativeBinding(
                        pkg.id(),
                        item.productId(),
                        item.videoId().orElse(null),
                        item.coverImageId().orElse(null),
                        item.videoId().map(v -> v.value()).orElse(null),
                        item.titles()))
                .toList();
    }

    private CreativePackage stubConfirmed(AdPlan plan) {
        CreativePackage pkg = CreativePackage.of(List.of(new com.lynx.ad.modules.creative.domain.model.CreativeArtifact(
                plan.productIds().isEmpty() ? new QcProductId("qp01") : plan.productIds().get(0),
                List.of(new com.lynx.ad.modules.creative.domain.model.AdTitle(
                        "烘焙好物", com.lynx.ad.modules.creative.domain.model.TitleType.CUSTOM)),
                Optional.empty(),
                Optional.empty())));
        pkg.confirm();
        return pkg;
    }

    private void recordPlan(TenantId tenantId, String planId, String name) {
        String jti = null;
        try {
            jti = TenantContext.require().sessionId().orElse(null);
        } catch (Exception ignored) {
            // job / unauthenticated
        }
        auditRecorder.record(new SuccessAuditCommand(
                tenantId,
                safeUser(),
                "商家",
                AuditActionType.PLAN,
                "PLAN",
                planId,
                name,
                RequestMeta.ip().orElse(null),
                jti,
                RequestMeta.userAgent().orElse(null),
                null));
    }

    private static com.lynx.ad.common.domain.Ids.UserId safeUser() {
        try {
            return TenantContext.userId();
        } catch (Exception ex) {
            return null;
        }
    }

    private AdPlan require(TenantId tenantId, PlanId id) {
        return planRepository.findById(tenantId, id).orElseThrow(() -> new BizException(1405, "计划不存在"));
    }

    private static DeliverySetting settingOf(PlanDraftRequest cmd) {
        return new DeliverySetting(
                SmartBidType.fromOcean(cmd.smartBidType()),
                Money.ofYuan(cmd.dailyBudget() == null ? java.math.BigDecimal.valueOf(300) : cmd.dailyBudget()),
                Optional.ofNullable(cmd.roi2Goal()).map(RoiGoal::new),
                Schedule.fromNow(),
                AudienceJson.empty());
    }

    static PlanVo toVo(AdPlan plan) {
        return new PlanVo(
                plan.id().value(),
                plan.name().text(),
                plan.status().name(),
                plan.source().name(),
                plan.qcPlanId().map(QcPlanId::value).orElse(null),
                plan.setting().bidType().oceanValue(),
                plan.setting().dailyBudget().amount(),
                plan.awemeId().value(),
                plan.rejectReason(),
                plan.source() == com.lynx.ad.modules.delivery.domain.model.PlanSource.LINGTOU,
                "展示字段",
                null,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO);
    }
}
