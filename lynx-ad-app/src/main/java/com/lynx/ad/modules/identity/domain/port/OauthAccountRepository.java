package com.lynx.ad.modules.identity.domain.port;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OauthAccountRepository {

    void save(OauthAccount account);

    void saveRenewingAuthorization(OauthAccount account);

    List<OauthAccount> findByTenant(TenantId id);

    Optional<OauthAccount> findById(TenantId tenantId, AccountId id);

    List<OauthAccount> findDueRefresh(Instant deadline);

    Optional<OauthAccount> findByIdOnly(AccountId id);

    void deleteAwemes(AccountId id);

    void deleteById(AccountId id);
}
