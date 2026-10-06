package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;

public record OceanPlanRow(
        String qcPlanId, String name, String status, SmartBidType bidType, Money budget, String coverUrl, String guarantee,
        com.fasterxml.jackson.databind.JsonNode payload) {
    public OceanPlanRow(String qcPlanId, String name, String status, SmartBidType bidType, Money budget, String coverUrl, String guarantee) {
        this(qcPlanId, name, status, bidType, budget, coverUrl, guarantee, null);
    }
}
