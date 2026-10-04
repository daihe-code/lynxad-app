package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.modules.identity.domain.model.Credential;

public interface AdCreatePort {
    QcPlanId create(Credential c, OceanAdCreateCommand cmd);
}
