package com.lynx.ad.modules.identity.domain.port;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.TenantId;

public interface OauthAppBindingRepository {

    void upsertQianchuan(TenantId tenantId, String advertiserId, AccountId accountId);

    void deleteByAccount(AccountId accountId);
}
