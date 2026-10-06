package com.lynx.ad.modules.identity.application;

import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import java.time.Instant;
import com.lynx.ad.modules.identity.domain.model.Credential;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import org.springframework.stereotype.Component;

@Component
public class QianchuanCredentialFacade {

    private final OauthAccountRepository repository;
    private final Encryptor encryptor;

    public QianchuanCredentialFacade(OauthAccountRepository repository, Encryptor encryptor) {
        this.repository = repository;
        this.encryptor = encryptor;
    }

    public Credential get(AccountId accountId) {
        if (TenantContext.tenantId().isPresent()) return getUsableCredential(TenantContext.requireTenantId(), accountId);
        return credential(repository.findByIdOnly(accountId).orElseThrow(() -> new BizException(1201, "授权账户不存在")));
    }

    /** Reads the authorization module's persisted credentials; never refreshes or returns them to the browser. */
    public Credential getUsableCredential(TenantId tenantId, AccountId accountId) {
        return credential(repository.findById(tenantId, accountId)
                .orElseThrow(() -> new BizException(1201, "授权账户不存在或不属于当前灵投账户")));
    }

    private Credential credential(OauthAccount account) {
        if (!"ACTIVE".equals(account.status())) throw new BizException(1211, "授权已取消或失效，请重新授权");
        if (account.tokenExpiresAt() != null && !account.tokenExpiresAt().isAfter(Instant.now()))
            throw new BizException(1211, "授权令牌已过期，等待授权模块更新或重新授权");
        if (account.accessToken() == null || account.advertiserId() == null)
            throw new BizException(1205, "授权信息不完整");
        String token = encryptor.decrypt(account.accessToken().cipherText());
        if (token == null || token.isBlank()) throw new BizException(1205, "授权信息不完整");
        return new Credential(token, account.advertiserId());
    }
}
