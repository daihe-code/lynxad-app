package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.modules.identity.domain.model.Credential;

public interface AdUpdatePort {
    void update(Credential c, OceanAdUpdateCommand cmd);
}
