package com.lynx.ad.modules.identity.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.identity.api.dto.AccountVo;
import com.lynx.ad.modules.identity.api.dto.AwemeVo;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OauthQueryService {

    private final OauthAccountRepository repository;

    public OauthQueryService(OauthAccountRepository repository) {
        this.repository = repository;
    }

    public List<AccountVo> list(TenantId tenantId) {
        return repository.findByTenant(tenantId).stream().map(OauthQueryService::toVo).toList();
    }

    public boolean hasBoundQianchuan(TenantId tenantId) {
        return !repository.findByTenant(tenantId).isEmpty();
    }

    public static AccountVo toVo(OauthAccount account) {
        return new AccountVo(
                account.id().value(),
                account.advertiserId().value(),
                account.accountName(),
                account.platform().name(),
                account.status(),
                account.awemes().stream()
                        .map(a -> new AwemeVo(
                                a.awemeId().value(),
                                a.name(),
                                a.avatar(),
                                a.type().name(),
                                a.primary(),
                                a.sceneHint()))
                        .toList(),
                account.authorizedByUserId().orElse(null));
    }
}
