package com.lynx.ad.common.idempotency;

import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.TenantId;
import java.util.Optional;

public interface IdempotencyService {

    Optional<String> findReplay(TenantId tenantId, IdempotencyKey key);

    void saveReplay(TenantId tenantId, IdempotencyKey key, String responseJson);
}
