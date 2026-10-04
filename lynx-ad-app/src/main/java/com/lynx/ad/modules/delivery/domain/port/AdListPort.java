package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.List;

public interface AdListPort {
    List<OceanPlanRow> list(Credential c, SmartBidType bidType);
}
