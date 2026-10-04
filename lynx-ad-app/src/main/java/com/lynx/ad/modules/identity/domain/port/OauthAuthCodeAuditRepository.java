package com.lynx.ad.modules.identity.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.identity.domain.model.OauthAuthCodeAudit;
import java.util.Optional;

public interface OauthAuthCodeAuditRepository {

    void save(OauthAuthCodeAudit audit);

    Optional<OauthAuthCodeAudit> findByTenantAndHash(TenantId tenantId, String authCodeHash);
}
