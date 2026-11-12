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
    @org.springframework.beans.factory.annotation.Autowired
    private com.lynx.ad.modules.tenant.application.WorkspaceAccess workspace;
    private boolean visible(TenantId tenantId,OauthAccount account) {
        return workspace==null||workspace.canReadBinding(tenantId.value(),account.id().value());
    }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.lynx.ad.common.config.LynxAdProperties properties;
    @org.springframework.beans.factory.annotation.Autowired
    private com.lynx.ad.modules.identity.infrastructure.persistence.OauthAuthorizationStore authorizationStore;
    private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();

    public OauthQueryService(OauthAccountRepository repository) {
        this.repository = repository;
    }

    public List<AccountVo> list(TenantId tenantId) {
        return repository.findByTenant(tenantId).stream()
                .filter(a -> !"DELETED".equals(a.status()) && visible(tenantId,a))
                .map(OauthQueryService::toVo).toList();
    }

    public boolean hasBoundQianchuan(TenantId tenantId) {
        return repository.findByTenant(tenantId).stream().anyMatch(a -> a.platform() == com.lynx.ad.modules.identity.domain.model.Platform.QIANCHUAN
                && "ACTIVE".equals(a.status()) && visible(tenantId,a)
                && (properties == null || !properties.getOauth().getQianchuan().isLive() || a.grantId() != null)
                && (a.refreshExpiresAt() == null || a.refreshExpiresAt().isAfter(java.time.Instant.now())));
    }
    public com.lynx.ad.modules.identity.api.dto.AccountAuthorizationDetailVo detail(TenantId tenantId,com.lynx.ad.common.domain.Ids.AccountId id) {
        OauthAccount account=repository.findById(tenantId,id).filter(a->!"DELETED".equals(a.status())&&visible(tenantId,a))
            .orElseThrow(()->new com.lynx.ad.common.exception.BizException(1201,"授权账户不存在或不属于当前坤境账户"));
        var metadata=authorizationStore.identityMetadata(tenantId.value(),id.value());
        String role=null,parent=null;
        if(metadata.catalogJson()!=null) {
            try {
                for(var candidate:json.readTree(metadata.catalogJson())) if(idMatches(account,candidate)) {
                    role=candidate.path("accountRole").asText(null); parent=candidate.path("parentAccountId").asText(null); break;
                }
            } catch(com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new com.lynx.ad.common.exception.BizException(1205,"授权来源信息读取失败，请重试");
            }
        }
        return new com.lynx.ad.modules.identity.api.dto.AccountAuthorizationDetailVo(toVo(account),tenantId.value(),metadata.appId(),role,parent,metadata.authorizedByName(),
            account.grantId()!=null && account.accessToken()!=null && !account.accessToken().cipherText().isBlank(),
            account.grantId()!=null && account.refreshToken()!=null && !account.refreshToken().cipherText().isBlank());
    }
    private boolean idMatches(OauthAccount account,com.fasterxml.jackson.databind.JsonNode candidate) {
        return account.advertiserId().value().equals(candidate.path("advertiserId").asText());
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
                                a.sceneHint(), a.authTypes(), a.isAllowMall()))
                        .toList(),
                account.authorizedByUserId().orElse(null),
                account.authorizedAt(),
                account.tokenExpiresAt(),
                account.refreshExpiresAt(),
                account.applicationId(),
                account.grantId() != null ? "LIVE" : account.advertiserId().value().matches("[0-9]+") ? "LEGACY" : "MOCK",
                account.catalogError());
    }
}
