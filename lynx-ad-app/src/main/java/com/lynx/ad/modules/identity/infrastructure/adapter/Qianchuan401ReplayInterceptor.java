package com.lynx.ad.modules.identity.infrastructure.adapter;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.Qianchuan401ReplayCodes;
import com.lynx.ad.modules.identity.application.OauthCommandService;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class Qianchuan401ReplayInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Qianchuan401ReplayInterceptor.class);

    private final OauthAccountRepository repository;
    private final ObjectProvider<OauthCommandService> commandService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.lynx.ad.modules.identity.application.QianchuanCredentialFacade credentials;
    public String currentAccessToken(AccountId id) {
        OauthAccount account = repository.findByIdOnly(id).orElseThrow(() -> new BizException(1201, "授权账户不存在"));
        if (com.lynx.ad.common.security.TenantContext.tenantId().isPresent()
                && !com.lynx.ad.common.security.TenantContext.requireTenantId().equals(account.tenantId())) throw new BizException(403, "授权账户不属于当前坤境账户");
        return credentials == null ? null : credentials.getUsableCredential(account.tenantId(), id).accessToken();
    }

    public Qianchuan401ReplayInterceptor(
            OauthAccountRepository repository, ObjectProvider<OauthCommandService> commandService) {
        this.repository = repository;
        this.commandService = commandService;
    }

    public void refreshOnce(AccountId accountId) {
        OauthAccount account = repository.findByIdOnly(accountId).orElseThrow(() -> new BizException(1201, "授权账户不存在"));
        if (com.lynx.ad.common.security.TenantContext.tenantId().isPresent()
                && !com.lynx.ad.common.security.TenantContext.requireTenantId().equals(account.tenantId())) throw new BizException(403, "授权账户不属于当前坤境账户");
        if (account.grantId() != null) { commandService.getObject().refreshStoredTokens(account, true); return; }
        try {
            commandService.getObject().applyPortRefresh(account);
            repository.save(account);
        } catch (BizException ex) {
            if (ex.code() == 1201) {
                throw ex;
            }
            log.warn("refresh after 401 failed account={}", accountId.value());
            account.markRefreshFailed();
            repository.save(account);
            throw new BizException(1211, "授权已失效，请重新授权");
        } catch (RuntimeException ex) {
            log.warn("refresh after 401 failed account={}", accountId.value());
            account.markRefreshFailed();
            repository.save(account);
            throw new BizException(1211, "授权已失效，请重新授权");
        }
    }

    public <T> T executeWithReplay(Supplier<T> call, AccountId accountId) {
        try {
            return call.get();
        } catch (BizException ex) {
            if (!Qianchuan401ReplayCodes.isAuthExpired(ex)) {
                throw ex;
            }
            refreshOnce(accountId);
            try {
                return call.get();
            } catch (BizException second) {
                if (Qianchuan401ReplayCodes.isAuthExpired(second)) {
                    throw new BizException(1211, "授权已失效，请重新授权");
                }
                throw second;
            }
        }
    }

    public static boolean isAuthExpired(BizException ex) {
        return Qianchuan401ReplayCodes.isAuthExpired(ex);
    }
}
