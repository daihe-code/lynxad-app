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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LiveQianchuanAuthorizationService liveAuthorization;
    private AccountBindingRegistry bindings;
    private AccountGrantSelector grantSelector;
    @org.springframework.beans.factory.annotation.Autowired public void bindings(AccountBindingRegistry value) { bindings=value; }
    @org.springframework.beans.factory.annotation.Autowired public void grantSelector(AccountGrantSelector value) { grantSelector=value; }

    public QianchuanCredentialFacade(OauthAccountRepository repository, Encryptor encryptor) {
        this.repository = repository;
        this.encryptor = encryptor;
    }

    public Credential get(AccountId accountId) {
        if (TenantContext.tenantId().isPresent()) return getUsableCredential(TenantContext.requireTenantId(), accountId);
        if (liveAuthorization != null && liveAuthorization.enabled()) throw new BizException(403, "读取授权凭证必须指定灵投账户");
        return credential(repository.findByIdOnly(accountId).orElseThrow(() -> new BizException(1201, "授权账户不存在")));
    }

    /** Tenant-scoped credentials; refresh remains inside the authorization module. */
    public Credential getUsableCredential(TenantId tenantId, AccountId accountId) {
        if(bindings!=null)bindings.executionScope(tenantId.value(),accountId.value(),TenantContext.tenantId().isPresent()?TenantContext.userId().value():null);
        OauthAccount account = repository.findById(tenantId, accountId)
                .orElseThrow(() -> new BizException(1201, "授权账户不存在或不属于当前灵投账户"));
        if (liveAuthorization != null && liveAuthorization.enabled()) {
            if(!"ACTIVE".equals(account.status())&&grantSelector!=null&&grantSelector.activateAlternative(tenantId.value(),accountId.value(),account.grantId()))
                account=repository.findById(tenantId,accountId).orElseThrow();
            if (account.grantId() == null) throw new BizException(1211, "请先完成真实千川授权，演示账户不能用于真实接口");
            if ("ACTIVE".equals(account.status()) && account.needsRefresh(Instant.now())) {
                try {liveAuthorization.refresh(tenantId, accountId, false, false);}
                catch(BizException ex) {
                    boolean invalid=ex.code()==1211||ex instanceof com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter.RemoteFailure failure&&failure.permanent();
                    if(!invalid||grantSelector==null||!grantSelector.activateAlternative(tenantId.value(),accountId.value(),account.grantId()))throw ex;
                }
                account = repository.findById(tenantId, accountId).orElseThrow(() -> new BizException(1201, "授权账户不存在"));
            }
        }
        return credential(account);
    }

    private Credential credential(OauthAccount account) {
        if (account.platform() != com.lynx.ad.modules.identity.domain.model.Platform.QIANCHUAN) throw new BizException(1205, "该授权凭证不属于巨量千川");
        if (!"ACTIVE".equals(account.status())) throw new BizException(1211, "授权已取消或失效，请重新授权");
        if (account.tokenExpiresAt() != null && !account.tokenExpiresAt().isAfter(Instant.now()))
            throw new BizException(1211, "授权令牌已过期，等待授权模块更新或重新授权");
        if (account.accessToken() == null || account.advertiserId() == null)
            throw new BizException(1205, "授权信息不完整");
        String token = encryptor.decrypt(account.accessToken().cipherText());
        if (token == null || token.isBlank()) throw new BizException(1205, "授权信息不完整");
        return new Credential(token, account.advertiserId(),account.id().value());
    }
}
