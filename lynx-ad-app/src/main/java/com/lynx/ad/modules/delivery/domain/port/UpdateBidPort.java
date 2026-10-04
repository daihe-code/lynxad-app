package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.identity.domain.model.Credential;

public interface UpdateBidPort {
    void updateBid(Credential c, QcPlanId id, Money bid);
}
