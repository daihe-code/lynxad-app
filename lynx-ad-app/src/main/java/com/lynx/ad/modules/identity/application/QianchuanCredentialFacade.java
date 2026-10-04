package com.lynx.ad.modules.identity.application;

import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.exception.BizException;
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
        var account = repository.findByIdOnly(accountId).orElseThrow(() -> new BizException(1201, "授权账户不存在"));
        String token = encryptor.decrypt(account.accessToken().cipherText());
        return new Credential(token, account.advertiserId());
    }
}
