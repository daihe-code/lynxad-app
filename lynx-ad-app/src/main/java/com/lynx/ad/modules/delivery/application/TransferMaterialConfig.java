package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferRebuildStore;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import org.springframework.stereotype.Component;

@Component
public class TransferMaterialConfig {
    private final AdPlanRepository plans;
    private final PlanTransferStore transfers;
    private final PlanTransferRebuildStore rebuilds;
    private final CreationQianchuanGateway gateway;

    public TransferMaterialConfig(AdPlanRepository plans, PlanTransferStore transfers,
                                  PlanTransferRebuildStore rebuilds, CreationQianchuanGateway gateway) {
        this.plans = plans;
        this.transfers = transfers;
        this.rebuilds = rebuilds;
        this.gateway = gateway;
    }

    public JsonNode archivedMaterials(AdPlan plan) {
        var tenant = plan.tenantId();
        var group = transfers.group(tenant, plan.id());
        if (group != null && !plan.id().value().equals(group.active())) {
            var active = plans.findById(tenant, new PlanId(group.active())).orElse(null);
            if (active != null && active.status() != PlanStatus.DELETED && active.qcPlanId().isPresent()
                    && plan.accountId().equals(active.accountId()) && plan.awemeId().equals(active.awemeId())
                    && active.id().value().equals(group.target(active.setting().bidType()))) {
                var id = active.qcPlanId().orElseThrow().value();
                try {
                    var detail = gateway.detail(tenant, active.accountId().value(), active.awemeId().value(), id);
                    if (detail != null && id.equals(QianchuanPlanMapping.id(detail))
                            && detail.path("multi_product_creative_list").isArray() && !detail.path("multi_product_creative_list").isEmpty())
                        return detail;
                } catch (BizException unavailable) {
                    // The verified snapshot remains readable when the active instance cannot be queried.
                }
            }
        }
        return rebuilds.savedPayload(tenant, plan.id().value())
                .orElseThrow(() -> new BizException(1403, "未找到已保存的投放素材"));
    }
}
