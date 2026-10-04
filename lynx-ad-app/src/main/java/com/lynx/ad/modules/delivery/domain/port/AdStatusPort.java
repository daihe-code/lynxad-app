package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.modules.delivery.domain.model.PlanAction;
import com.lynx.ad.modules.identity.domain.model.Credential;

public interface AdStatusPort {
    void updateStatus(Credential c, QcPlanId id, PlanAction action);
}
