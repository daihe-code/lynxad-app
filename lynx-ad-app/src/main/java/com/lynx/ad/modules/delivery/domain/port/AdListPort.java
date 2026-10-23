package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.List;

public interface AdListPort {
    List<OceanPlanRow> list(Credential c, SmartBidType bidType);
    /** Explicit synchronization includes tombstones; operation/recovery reads keep their original scope. */
    default List<OceanPlanRow> listIncludingDeleted(Credential c, SmartBidType bidType) { return list(c,bidType); }
}
