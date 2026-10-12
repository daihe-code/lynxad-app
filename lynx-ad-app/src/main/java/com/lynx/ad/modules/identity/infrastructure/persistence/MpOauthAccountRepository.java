package com.lynx.ad.modules.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.identity.domain.model.Aweme;
import com.lynx.ad.modules.identity.domain.model.AwemeType;
import com.lynx.ad.modules.identity.domain.model.EncryptedToken;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.identity.domain.service.AwemeCatalog;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@TableName("oauth_account")
class OauthAccountPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String platform;
    private String advertiserId;
    private String accountName;
    private String accessTokenEnc;
    private String refreshTokenEnc;
    private Instant tokenExpiresAt;
    private Instant refreshExpiresAt;
    private String status;
    private Instant authorizedAt;
    private String authorizedByUserId;
    private String grantId;
    private String applicationId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String catalogError;
    public String getGrantId() { return grantId; }
    public void setGrantId(String value) { grantId = value; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String value) { applicationId = value; }
    public String getCatalogError() { return catalogError; }
    public void setCatalogError(String value) { catalogError = value; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getAdvertiserId() { return advertiserId; }
    public void setAdvertiserId(String advertiserId) { this.advertiserId = advertiserId; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccessTokenEnc() { return accessTokenEnc; }
    public void setAccessTokenEnc(String accessTokenEnc) { this.accessTokenEnc = accessTokenEnc; }
    public String getRefreshTokenEnc() { return refreshTokenEnc; }
    public void setRefreshTokenEnc(String refreshTokenEnc) { this.refreshTokenEnc = refreshTokenEnc; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public Instant getRefreshExpiresAt() { return refreshExpiresAt; }
    public void setRefreshExpiresAt(Instant refreshExpiresAt) { this.refreshExpiresAt = refreshExpiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getAuthorizedAt() { return authorizedAt; }
    public void setAuthorizedAt(Instant authorizedAt) { this.authorizedAt = authorizedAt; }
    public String getAuthorizedByUserId() { return authorizedByUserId; }
    public void setAuthorizedByUserId(String authorizedByUserId) { this.authorizedByUserId = authorizedByUserId; }
}

@TableName("oauth_aweme")
class OauthAwemePo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String oauthAccountId;
    private String awemeId;
    private String name;
    private String avatar;
    private String awemeType;
    private Boolean isPrimary;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOauthAccountId() { return oauthAccountId; }
    public void setOauthAccountId(String oauthAccountId) { this.oauthAccountId = oauthAccountId; }
    public String getAwemeId() { return awemeId; }
    public void setAwemeId(String awemeId) { this.awemeId = awemeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getAwemeType() { return awemeType; }
    public void setAwemeType(String awemeType) { this.awemeType = awemeType; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
}

@Mapper
interface OauthAccountMapper extends BaseMapper<OauthAccountPo> {}

@Mapper
interface OauthAwemeMapper extends BaseMapper<OauthAwemePo> {}

@Repository
public class MpOauthAccountRepository implements OauthAccountRepository {

    private final OauthAccountMapper accountMapper;
    private final OauthAwemeMapper awemeMapper;
    private final AwemeCatalog catalog = new AwemeCatalog();

    public MpOauthAccountRepository(OauthAccountMapper accountMapper, OauthAwemeMapper awemeMapper) {
        this.accountMapper = accountMapper;
        this.awemeMapper = awemeMapper;
    }

    @Override
    @Transactional
    public void save(OauthAccount account) {
        OauthAccountPo po = accountMapper.selectById(account.id().value());
        boolean insert = po == null;
        if (insert) {
            po = new OauthAccountPo();
            po.setId(account.id().value());
            po.setAuthorizedAt(Instant.now());
        }
        po.setTenantId(account.tenantId().value());
        po.setPlatform(account.platform().name());
        po.setAdvertiserId(account.advertiserId().value());
        po.setAccountName(account.accountName());
        po.setAccessTokenEnc(account.accessToken().cipherText());
        po.setRefreshTokenEnc(account.refreshToken() == null ? null : account.refreshToken().cipherText());
        po.setTokenExpiresAt(account.tokenExpiresAt());
        po.setRefreshExpiresAt(account.refreshExpiresAt());
        po.setStatus(account.status());
        po.setAuthorizedByUserId(account.authorizedByUserId().orElse(null));
        po.setGrantId(account.grantId());
        po.setApplicationId(account.applicationId());
        po.setCatalogError(account.catalogError());
        if (account.authorizedAt() != null) po.setAuthorizedAt(account.authorizedAt());
        if (insert) {
            accountMapper.insert(po);
        } else {
            accountMapper.updateById(po);
        }
        awemeMapper.delete(new LambdaQueryWrapper<OauthAwemePo>().eq(OauthAwemePo::getOauthAccountId, account.id().value()));
        for (Aweme a : account.awemes()) {
            OauthAwemePo ap = new OauthAwemePo();
            ap.setId(java.util.UUID.randomUUID().toString());
            ap.setOauthAccountId(account.id().value());
            ap.setAwemeId(a.awemeId().value());
            ap.setName(a.name());
            ap.setAvatar(a.avatar());
            ap.setAwemeType(a.type().name());
            ap.setIsPrimary(a.primary());
            awemeMapper.insert(ap);
        }
    }

    @Override
    public List<OauthAccount> findByTenant(TenantId id) {
        return accountMapper.selectList(new LambdaQueryWrapper<OauthAccountPo>().eq(OauthAccountPo::getTenantId, id.value()))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<OauthAccount> findById(TenantId tenantId, AccountId id) {
        return findByIdOnly(id).filter(a -> a.tenantId().equals(tenantId));
    }

    @Override
    public Optional<OauthAccount> findByIdOnly(AccountId id) {
        return Optional.ofNullable(accountMapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<OauthAccount> findDueRefresh(Instant deadline) {
        return accountMapper.selectList(new LambdaQueryWrapper<OauthAccountPo>()
                        .eq(OauthAccountPo::getStatus, "ACTIVE")
                        .le(OauthAccountPo::getTokenExpiresAt, deadline))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private OauthAccount toDomain(OauthAccountPo po) {
        List<Aweme> awemes = awemeMapper.selectList(new LambdaQueryWrapper<OauthAwemePo>()
                        .eq(OauthAwemePo::getOauthAccountId, po.getId()))
                .stream()
                .map(a -> new Aweme(
                        new AwemeId(a.getAwemeId()),
                        a.getName(),
                        a.getAvatar(),
                        AwemeType.valueOf(a.getAwemeType()),
                        Boolean.TRUE.equals(a.getIsPrimary()),
                        catalog.sceneHint(AwemeType.valueOf(a.getAwemeType()))))
                .toList();
        return new OauthAccount(
                new AccountId(po.getId()),
                new TenantId(po.getTenantId()),
                Platform.valueOf(po.getPlatform()),
                new AdvertiserId(po.getAdvertiserId()),
                po.getAccountName(),
                new EncryptedToken(po.getAccessTokenEnc()),
                po.getRefreshTokenEnc() == null ? null : new EncryptedToken(po.getRefreshTokenEnc()),
                po.getTokenExpiresAt(),
                po.getRefreshExpiresAt(),
                po.getStatus(),
                awemes,
                po.getAuthorizedByUserId()).withAuthorizedAt(po.getAuthorizedAt())
                .withAuthorizationBinding(po.getGrantId(), po.getApplicationId(), po.getCatalogError());
    }
}
