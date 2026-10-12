package com.lynx.ad.agents.plan;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.port.AdCreatePort;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.port.AdUpdatePort;
import com.lynx.ad.modules.delivery.domain.port.OceanAdCreateCommand;
import com.lynx.ad.modules.delivery.domain.port.OceanAdUpdateCommand;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PlanAgent {

    private final AdPlanRepository planRepository;
    private final AdCreatePort adCreatePort;
    private final AdUpdatePort adUpdatePort;
    private final QianchuanCredentialFacade credentialFacade;

    public PlanAgent(
            AdPlanRepository planRepository,
            AdCreatePort adCreatePort,
            AdUpdatePort adUpdatePort,
            QianchuanCredentialFacade credentialFacade) {
        this.planRepository = planRepository;
        this.adCreatePort = adCreatePort;
        this.adUpdatePort = adUpdatePort;
        this.credentialFacade = credentialFacade;
    }

    @Async
    public void publish(PlanId planId) {
        publishNow(planId);
    }

    public void publishNow(PlanId planId) {
        AdPlan plan = planRepository.findByIdOnly(planId).orElseThrow(() -> new BizException(1405, "计划不存在"));
        execute(plan);
    }

    public void publishNow(AdPlan plan) {
        execute(plan);
    }

    public void assertLegacyCreationAllowed() {
        if (adCreatePort instanceof com.lynx.ad.modules.delivery.infrastructure.adapter.LiveDeliveryAdapters)
            throw new BizException(1403,"请使用四步创建投放流程，旧素材包不支持真实发布");
    }

    private void execute(AdPlan plan) {
        Credential cred = credentialFacade.getUsableCredential(plan.tenantId(), plan.accountId());
        if (plan.qcPlanId().isEmpty()) {
            // The legacy package path contains fixture IDs and cannot create real platform plans.
            assertLegacyCreationAllowed();
            var qcId = adCreatePort.create(
                    cred,
                    new OceanAdCreateCommand(
                            plan.id().value(),
                            plan.name().text(),
                            plan.setting().bidType().oceanValue(),
                            plan.setting().dailyBudget()));
            plan.acceptExternalId(qcId);
        } else {
            adUpdatePort.update(
                    cred,
                    new OceanAdUpdateCommand(plan.qcPlanId().get().value(), plan.name().text(), plan.setting().dailyBudget()));
        }
        plan.bindCreatives(plan.bindings());
        planRepository.save(plan);
    }
}
