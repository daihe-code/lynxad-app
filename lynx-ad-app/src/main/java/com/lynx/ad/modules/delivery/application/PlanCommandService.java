package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.agents.plan.PlanAgent;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.IdempotencyKey;
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
import com.lynx.ad.modules.delivery.domain.model.PlanPauseReason;
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
    private com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore creations;
    private com.lynx.ad.modules.delivery.infrastructure.persistence.DraftExpiryStore draftExpiry;
    @org.springframework.beans.factory.annotation.Autowired public void draftExpiry(com.lynx.ad.modules.delivery.infrastructure.persistence.DraftExpiryStore value){draftExpiry=value;}
    @org.springframework.beans.factory.annotation.Autowired public void creationStore(com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore value) { creations=value; }
    private com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore transfers;
    @org.springframework.beans.factory.annotation.Autowired public void transferStore(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore value) { transfers=value; }
    private PlanTransferService transferService;
    private com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore draftProducts;
    @org.springframework.beans.factory.annotation.Autowired public void draftProducts(com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore value) { draftProducts=value; }
    @org.springframework.beans.factory.annotation.Autowired public void transferService(PlanTransferService value) { transferService=value; }

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
        if(draftProducts!=null)draftProducts.assertAvailable(tenantId.value(),cmd.accountId(),cmd.productIds(),null);
        PlanName name = new PlanName(cmd.name());
        namePolicy.assertUnique(planRepository.existsUnfinishedName(tenantId, new AwemeId(cmd.awemeId()), name, Optional.empty()));
        DeliverySetting setting = settingOf(cmd);
        List<QcProductId> pids = cmd.productIds() == null ? List.of() : cmd.productIds().stream().map(QcProductId::new).toList();
        AdPlan plan = AdPlan.draft(tenantId, new AccountId(cmd.accountId()), new AwemeId(cmd.awemeId()), name, setting, pids);
        planRepository.save(plan);
        saveDraftProducts(plan);
        recordPlan(tenantId, plan.id().value(), plan.name().text());
        Credential cred = credentialFacade.getUsableCredential(tenantId, new AccountId(cmd.accountId()));
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
        if(draftProducts!=null)draftProducts.assertAvailable(tenantId.value(),cmd.accountId(),cmd.productIds(),id.value());
        AdPlan plan = require(tenantId, id);
        if (plan.status() != com.lynx.ad.modules.delivery.domain.model.PlanStatus.DRAFT
                && plan.status() != com.lynx.ad.modules.delivery.domain.model.PlanStatus.REJECTED) {
            throw new BizException(1403, "已提交平台的计划暂不支持在线修改");
        }
        if (!plan.accountId().value().equals(cmd.accountId()) || !plan.awemeId().value().equals(cmd.awemeId())) {
            throw new BizException(1403, "草稿不可切换广告账户或抖音号，请新建计划");
        }
        if(creations!=null) {
            boolean hasCreation;
            try {hasCreation=creations.get(tenantId.value(),id.value())!=null;}
            catch(BizException missing){if(missing.code()!=1403)throw missing;hasCreation=false;}
            if(hasCreation)creations.mutate(tenantId.value(),id.value(),null,
                    com.lynx.ad.modules.delivery.domain.service.CreationProductSelection::assertEditable);
        }
        PlanName name = new PlanName(cmd.name());
        namePolicy.assertUnique(planRepository.existsUnfinishedName(tenantId, plan.awemeId(), name, Optional.of(id)));
        plan.rename(name);
        DeliverySetting requested = settingOf(cmd);
        plan.updateSetting(new DeliverySetting(requested.bidType(), requested.dailyBudget(),
                requested.roi2Goal(), plan.setting().schedule(), requested.audience()));
        plan.replaceProducts(cmd.productIds() == null ? List.of() : cmd.productIds().stream().map(QcProductId::new).toList());
        planRepository.save(plan);
        saveDraftProducts(plan);
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
        CreativePackage pkg = resolvePackage(tenantId, plan);
        publishPolicy.assertPublishable(plan, true, pkg.isConfirmed(), plan.productIds().size());
        if (plan.qcPlanId().isEmpty()) planAgent.assertLegacyCreationAllowed();
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
    public void discardDraft(TenantId tenantId, PlanId id) {
        AdPlan plan = require(tenantId, id);
        if (plan.status() != com.lynx.ad.modules.delivery.domain.model.PlanStatus.DRAFT || plan.qcPlanId().isPresent())
            throw new BizException(1403, "仅可丢弃尚未投放的草稿");
        if (!planRepository.discardDraft(tenantId, id)) throw new BizException(1404, "计划状态已变化，请刷新后操作");
        bindingStore.upsert(id, List.of());
        recordPlan(tenantId, id.value(), "丢弃草稿：" + plan.name().text());
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlanVo act(TenantId tenantId, PlanId id, PlanAction action) {
        return act(tenantId, id, action, null);
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlanVo act(TenantId tenantId, PlanId id, PlanAction action, String pauseReason) {
        if(action==PlanAction.DELETE) throw new BizException(1402,"请使用删除计划操作");
        if(transfers!=null)transfers.assertIdle(tenantId,id);
        billingTrialService.assertWritable(tenantId, Instant.now(), WriteAction.SAVE_PLAN);
        AdPlan plan = require(tenantId, id);
        String reason = action == PlanAction.DISABLE ? PlanPauseReason.require(pauseReason) : null;
        String before = plan.status().name();
        if (action == PlanAction.FINISH && plan.qcPlanId().isPresent())
            throw new BizException(1403, "结束计划尚无对应接口，不能替代为删除操作");
        if ((action == PlanAction.DISABLE && plan.status() != com.lynx.ad.modules.delivery.domain.model.PlanStatus.DELIVERING)
                || (action == PlanAction.ENABLE && plan.status() != com.lynx.ad.modules.delivery.domain.model.PlanStatus.PAUSED))
            throw new BizException(1404, "计划状态已变化，请刷新后操作");
        var group=transfers==null?null:transfers.group(tenantId,id);
        boolean pairedEnable=action==PlanAction.ENABLE && group!=null && group.conservative()!=null && group.custom()!=null;
        if(pairedEnable) {
            if(transferService==null)throw new BizException(1523,"转投服务尚不可用，请稍后重试");
            var result=transferService.enableExisting(tenantId,id);
            if(!"SUCCEEDED".equals(result.status()))throw new BizException(1523,result.message());
            plan=require(tenantId,id);
        } else if (plan.qcPlanId().isPresent()) {
            adStatusPort.updateStatus(credentialFacade.getUsableCredential(tenantId, plan.accountId()), plan.qcPlanId().get(), action);
        }
        if(!pairedEnable)switch (action) {
            case DISABLE -> plan.pause();
            case ENABLE -> plan.resume();
            case FINISH -> plan.finish();
            case DELETE -> throw new BizException(1402,"请使用删除计划操作");
        }
        planRepository.save(plan);
        if(transfers!=null)transfers.reconcileActive(tenantId,planRepository.findByTenant(tenantId));
        ActionType type = switch (action) {
            case ENABLE -> ActionType.MANUAL_ENABLE;
            case DISABLE -> ActionType.MANUAL_DISABLE;
            case FINISH -> ActionType.MANUAL_FINISH;
            case DELETE -> throw new BizException(1402,"请使用删除计划操作");
        };
        String summary = reason == null ? type.name() : "暂停投放：" + reason;
        agentActionRepository.saveIgnoreDup(AgentAction.recordManual(tenantId, id, type, before, plan.status().name(), summary));
        recordPlan(tenantId, plan.id().value(), reason == null ? plan.name().text() : "暂停计划：" + plan.name().text() + "；暂停原因：" + reason);
        return toVo(plan);
    }

    @Transactional
    public void deletePlan(TenantId tenantId, PlanId id) {
        billingTrialService.assertWritable(tenantId, Instant.now(), WriteAction.SAVE_PLAN);
        AdPlan plan=require(tenantId,id);
        if(plan.status()==com.lynx.ad.modules.delivery.domain.model.PlanStatus.DELETED)return;
        if(plan.qcPlanId().isEmpty()) {
            discardDraft(tenantId,id);
            return;
        }
        if(transfers!=null)transfers.lockForDeletion(plan);
        plan=planRepository.findByIdForUpdate(tenantId,id).orElseThrow(()->new BizException(1405,"计划不存在"));
        if(plan.status()==com.lynx.ad.modules.delivery.domain.model.PlanStatus.DELETED)return;
        adStatusPort.updateStatus(credentialFacade.getUsableCredential(tenantId,plan.accountId()),plan.qcPlanId().orElseThrow(),PlanAction.DELETE);
        try {
            plan.markDeleted();
            planRepository.save(plan);
            if(transfers!=null)transfers.detachDeleted(tenantId,id);
            recordPlan(tenantId,id.value(),"删除计划："+plan.name().text());
        } catch(RuntimeException ex) {
            throw new BizException(1406,"千川已接受删除，本地结果待确认，请同步核查，勿重复提交");
        }
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

    private CreativePackage resolvePackage(TenantId tenantId, AdPlan plan) {
        return creativeTaskRepository.listByTenant(tenantId).stream()
                .filter(t -> t.planId().map(p -> p.equals(plan.id())).orElse(false))
                .flatMap(t -> t.currentPackage().stream())
                .max(java.util.Comparator.comparing(CreativePackage::createdAt))
                .orElseThrow(() -> new BizException(1503, "当前计划尚未生成素材包，请先准备并确认素材"));
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

    private void saveDraftProducts(AdPlan plan) {
        // Keep draft selections across reloads without pretending any media is already bound.
        bindingStore.upsert(plan.id(), plan.productIds().stream()
                .map(productId -> new PlanCreativeBinding(null, productId, null, null, List.of()))
                .toList());
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
        if(draftExpiry!=null)draftExpiry.assertAvailable(tenantId.value(),id.value(),Instant.now());
        return planRepository.findById(tenantId, id).orElseThrow(() -> new BizException(1405, "计划不存在"));
    }

    private static DeliverySetting settingOf(PlanDraftRequest cmd) {
        var mode=DeliveryInputPolicy.mode(cmd.smartBidType());
        DeliveryInputPolicy.settings(mode,cmd.dailyBudget(),cmd.roi2Goal(),DeliveryInputPolicy.RULES.creation());
        return new DeliverySetting(
                mode,
                Money.ofYuan(cmd.dailyBudget()),
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
                null,
                null,
                null,
                null,
                null);
    }
}
