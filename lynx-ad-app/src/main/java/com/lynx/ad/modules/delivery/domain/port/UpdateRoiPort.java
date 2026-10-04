package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;

public interface UpdateRoiPort {
    void updateRoi(Credential c, QcPlanId id, BigDecimal roi);
}
