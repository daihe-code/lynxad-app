package com.lynx.ad.modules.identity.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.RequestMeta;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.audit.application.SuccessAuditCommand;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.delivery.application.PlanAccountLinkService;
import com.lynx.ad.modules.identity.api.dto.AccountVo;
import com.lynx.ad.modules.identity.api.dto.OauthUrlResponse;
import com.lynx.ad.modules.identity.domain.model.Aweme;
import com.lynx.ad.modules.identity.domain.model.EncryptedToken;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.model.OauthAuthCodeAudit;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAppBindingRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAuthCodeAuditRepository;
import com.lynx.ad.modules.identity.domain.port.OauthTokenPort;
import com.lynx.ad.modules.identity.domain.service.OauthStateSigner;
import com.lynx.ad.modules.identity.infrastructure.adapter.MockQianchuanClient;
import com.lynx.ad.modules.tenant.application.TenantQuerySupport;

@Service
public class OauthCommandService {

    private final OauthAccountRepository repository;
    private final OauthAppBindingRepository bindingRepository;
    private final OauthAuthCodeAuditRepository auditRepository;
    private final TransactionTemplate transactionTemplate;
    private final MockQianchuanClient mockClient;
    private final LynxAdProperties properties;
    private final OauthStateSigner stateSigner;
    private final ObjectProvider<OauthTokenPort> tokenPort;
    private final Encryptor encryptor;
    private final TenantQuerySupport tenantQuery;
    private final AuditRecorder auditRecorder;
    private final PlanAccountLinkService planAccountLinkService;

    @org.springframework.beans.factory.annotation.Autowired
    private LiveQianchuanAuthorizationService liveAuthorization;

    public OauthCommandService(
            OauthAccountRepository repository,
            OauthAppBindingRepository bindingRepository,
            OauthAuthCodeAuditRepository auditRepository,
            MockQianchuanClient mockClient,
            LynxAdProperties properties,
            ObjectProvider<OauthTokenPort> tokenPort,
            Encryptor encryptor,
            TenantQuerySupport tenantQuery,
            AuditRecorder auditRecorder,
            PlanAccountLinkService planAccountLinkService,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.bindingRepository = bindingRepository;
        this.auditRepository = auditRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.mockClient = mockClient;
        this.properties = properties;
        this.tokenPort = tokenPort;
        this.encryptor = encryptor;
        this.tenantQuery = tenantQuery;
        this.auditRecorder = auditRecorder;
        this.planAccountLinkService = planAccountLinkService;
        this.stateSigner = new OauthStateSigner(properties.getCrypto().getAesKey());
    }

    public OauthUrlResponse buildAuthorizeUrl(TenantId tenantId, Platform platform) {
        if (platform != Platform.QIANCHUAN) {
            throw new BizException(1204, "即将上线");
        }
        UserId userId = TenantContext.userId();
        if (properties.getOauth().getQianchuan().isLive()) return liveAuthorization.start(tenantId, userId, "/campaigns");
        String redirect = URLEncoder.encode(properties.getOauth().getQianchuan().getRedirectUri(), StandardCharsets.UTF_8);
        String state = URLEncoder.encode(stateSigner.sign(tenantId, userId), StandardCharsets.UTF_8);
        String authorizeUrl = properties.getOauth().getQianchuan().getAuthorizeUrl();
        String url = authorizeUrl
                + "?app_id="
                + properties.getOauth().getQianchuan().getAppId()
                + "&redirect_uri="
                + redirect
                + "&state="
                + state;
        return new OauthUrlResponse(url);
    }

    public OauthCallbackResult handleCallback(String authCode, String state) {
        OauthAuthCodeAudit audit = null;
        try {
            OauthStateSigner.VerifiedState verified = stateSigner.verify(state);
            TenantId tenantId = verified.tenantId();
            String operatorUserId = verified.userId().value();
            if (tenantQuery.findById(tenantId).isEmpty()) {
                return OauthCallbackResult.fail("授权状态无效");
            }
            if (authCode == null || authCode.isBlank()) {
                return OauthCallbackResult.fail("缺少授权码");
            }
            String hash = OauthAuthCodeAudit.sha256Hex(authCode);
            OauthAuthCodeAudit existing = auditRepository.findByTenantAndHash(tenantId, hash).orElse(null);
            if (existing != null && existing.used()) {
                return OauthCallbackResult.fail("授权码已使用");
            }
            audit = existing == null ? OauthAuthCodeAudit.received(tenantId, operatorUserId, authCode) : existing;
            if (existing == null) {
                auditRepository.save(audit);
            }
            if (!properties.getMock().isQianchuan() && !properties.isQianchuanLiveConfigured()) {
                throw new BizException(1205, "千川授权未配置");
            }
            OauthTokenPort port = tokenPort.getIfAvailable();
            if (port == null) {
                throw new BizException(1205, "千川授权未配置");
            }
            OauthTokenPort.OauthTokenResult exchanged = port.exchange(authCode);
            List<Aweme> awemes = exchanged.awemes().stream()
                    .map(d -> new Aweme(
                            new AwemeId(d.awemeId()),
                            d.name(),
                            d.avatar(),
                            d.type(),
                            d.primary(),
                            d.sceneHint()))
                    .toList();
            EncryptedToken access = new EncryptedToken(encryptor.encrypt(exchanged.accessTokenPlain()));
            EncryptedToken refresh = new EncryptedToken(encryptor.encrypt(exchanged.refreshTokenPlain()));
            OauthAccount existingAccount = findQianchuan(tenantId, exchanged.advertiserId());
            OauthAccount account = existingAccount == null
                    ? OauthAccount.authorize(
                            tenantId,
                            new AdvertiserId(exchanged.advertiserId()),
                            exchanged.accountName(),
                            access,
                            refresh,
                            exchanged.accessExpiresAt(),
                            exchanged.refreshExpiresAt(),
                            awemes,
                            operatorUserId)
                    : OauthAccount.reconfirm(
                            existingAccount,
                            exchanged.accountName(),
                            access,
                            refresh,
                            exchanged.accessExpiresAt(),
                            exchanged.refreshExpiresAt(),
                            awemes,
                            operatorUserId);
            boolean renew = existingAccount != null;
            OauthAuthCodeAudit auditRow = audit;
            transactionTemplate.executeWithoutResult(status -> {
                if (renew) {
                    repository.saveRenewingAuthorization(account);
                } else {
                    repository.save(account);
                }
                bindingRepository.upsertQianchuan(tenantId, exchanged.advertiserId(), account.id());
                auditRow.markUsed(exchanged.advertiserId());
                auditRepository.save(auditRow);
            });
            recordOauth(tenantId, account.id().value(), account.accountName(), operatorUserId);
            return OauthCallbackResult.success();
        } catch (BizException ex) {
            if (audit != null) {
                audit.markFailed();
                auditRepository.save(audit);
            }
            return OauthCallbackResult.fail(ex.getMessage());
        }
    }

    public AccountVo mockBind(TenantId tenantId) {
        if (properties.getOauth().getQianchuan().isLive()) throw new BizException(1205, "真实授权模式不支持演示绑定");
        if (!mockClient.mockEnabled()) {
            throw new BizException(1205, "Mock 授权未开启");
        }
        OauthAccount draft = mockClient.bindDemo(tenantId);
        OauthAccount existing = findQianchuan(tenantId, draft.advertiserId().value());
        OauthAccount account;
        if (existing == null) {
            account = draft;
            repository.save(account);
        } else {
            String operator = existing.authorizedByUserId().orElse(null);
            try {
                operator = TenantContext.userId().value();
            } catch (RuntimeException ignored) {
                // 没有登录用户时保留原操作者
            }
            account = OauthAccount.reconfirm(
                    existing,
                    draft.accountName(),
                    draft.accessToken(),
                    draft.refreshToken(),
                    draft.tokenExpiresAt(),
                    draft.refreshExpiresAt(),
                    draft.awemes(),
                    operator);
            repository.saveRenewingAuthorization(account);
        }
        recordOauth(tenantId, account.id().value(), account.accountName(), null);
        return OauthQueryService.toVo(account);
    }

    public void refresh(TenantId tenantId, AccountId id) {
        OauthAccount account = repository.findById(tenantId, id).orElseThrow(() -> new BizException(1201, "授权账户不存在"));
        if (account.grantId() != null) { liveAuthorization.refresh(tenantId, id, true, true); return; }
        if (properties.getOauth().getQianchuan().isLive()) throw new BizException(1211, "请先完成真实千川授权");
        if (!"ACTIVE".equals(account.status())) {
            throw new BizException(1211, "授权已取消或失效，请重新授权");
        }
        try {
            applyPortRefresh(account);
            repository.save(account);
        } catch (BizException ex) {
            if (ex.code() == 1201) {
                throw ex;
            }
            throw new BizException(1211, "授权已失效，请重新授权");
        } catch (RuntimeException ex) {
            throw new BizException(1211, "授权已失效，请重新授权");
        }
    }

    public void applyPortRefresh(OauthAccount account) {
        if (!"ACTIVE".equals(account.status())) {
            throw new BizException(1211, "授权已取消或失效，请重新授权");
        }
        OauthTokenPort port = tokenPort.getIfAvailable();
        if (port == null) {
            throw new BizException(1205, "千川授权未配置");
        }
        String refreshPlain = encryptor.decrypt(account.refreshToken().cipherText());
        OauthTokenPort.OauthTokenResult result = port.refresh(refreshPlain);
        Instant accessExp = result.accessExpiresAt();
        EncryptedToken access = new EncryptedToken(encryptor.encrypt(result.accessTokenPlain()));
        EncryptedToken refresh = result.refreshTokenPlain() == null
                ? account.refreshToken()
                : new EncryptedToken(encryptor.encrypt(result.refreshTokenPlain()));
        account.replaceTokens(access, refresh, accessExp, result.refreshExpiresAt());
    }

    public void refreshStoredTokens(OauthAccount account, boolean force) {
        if (account.grantId() != null) { liveAuthorization.refresh(account.tenantId(), account.id(), force, false); return; }
        if (properties.getOauth().getQianchuan().isLive()) throw new BizException(1211, "请先完成真实千川授权");
        applyPortRefresh(account);
        repository.save(account);
    }
    public boolean liveAuthorizationEnabled() { return properties.getOauth().getQianchuan().isLive(); }

    public void unbind(TenantId tenantId, AccountId id) {
        OauthAccount account = repository.findById(tenantId, id).orElseThrow(() -> new BizException(1201, "授权账户不存在"));
        if ("DELETED".equals(account.status())) {
            throw new BizException(1201, "授权账户不存在");
        }
        if(account.grantId()!=null) liveAuthorization.changeLocalStatus(tenantId,id,false);
        else { account.unbind(); repository.save(account); }
        recordOauth(tenantId, account.id().value(), "取消广告账户授权「" + account.accountName() + "」", null);
    }

    public void remove(TenantId tenantId, AccountId id) {
        OauthAccount account = repository.findById(tenantId, id).orElseThrow(() -> new BizException(1201, "授权账户不存在"));
        if (stillUsable(account)) {
            throw new BizException(1208, "授权仍有效，请先取消授权");
        }
        transactionTemplate.executeWithoutResult(status -> {
            repository.deleteAwemes(id);
            bindingRepository.deleteByAccount(id);
            planAccountLinkService.detach(id);
            repository.deleteById(id);
        });
    }

    private static boolean stillUsable(OauthAccount account) {
        if (!"ACTIVE".equals(account.status())) {
            return false;
        }
        Instant exp = account.refreshExpiresAt();
        return exp == null || exp.isAfter(Instant.now());
    }

    private OauthAccount findQianchuan(TenantId tenantId, String advertiserId) {
        return repository.findByTenant(tenantId).stream()
                .filter(account -> account.platform() == Platform.QIANCHUAN
                        && account.advertiserId().value().equals(advertiserId))
                .findFirst()
                .orElse(null);
    }

    private void recordOauth(TenantId tenantId, String accountId, String name, String stateUserId) {
        String jti = null;
        com.lynx.ad.common.domain.Ids.UserId uid = null;
        if (stateUserId != null && !stateUserId.isBlank()) {
            uid = new UserId(stateUserId);
        } else {
            try {
                jti = TenantContext.require().sessionId().orElse(null);
                uid = TenantContext.userId();
            } catch (Exception ignored) {
                // callback may have no JWT
            }
        }
        auditRecorder.record(new SuccessAuditCommand(
                tenantId,
                uid,
                "商家",
                AuditActionType.OAUTH,
                "OAUTH_ACCOUNT",
                accountId,
                name,
                RequestMeta.ip().orElse(null),
                jti,
                RequestMeta.userAgent().orElse(null),
                null));
    }
}
