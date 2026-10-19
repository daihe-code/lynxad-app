package com.lynx.ad.modules.catalog.application;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OwningAccountLookup {

    private final OauthAccountRepository accounts;

    public OwningAccountLookup(OauthAccountRepository accounts) {
        this.accounts = accounts;
    }

    public AccountId require(TenantId tenantId, String awemeId) {
        List<OauthAccount> matches = accounts.findByTenant(tenantId).stream()
                .filter(account -> account.awemes().stream()
                        .anyMatch(aweme -> aweme.awemeId().value().equals(awemeId)))
                .toList();
        if (matches.size() != 1) {
            throw new BizException(1306, "商品池查询失败");
        }
        return matches.get(0).id();
    }
}
