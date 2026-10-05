package com.lynx.ad.modules.identity.domain.model;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OauthAccount {

    public static final Duration REFRESH_LEAD = Duration.ofMinutes(30);

    private final AccountId id;
    private final TenantId tenantId;
    private final Platform platform;
    private final AdvertiserId advertiserId;
    private final String accountName;
    private EncryptedToken accessToken;
    private EncryptedToken refreshToken;
    private Instant tokenExpiresAt;
    private Instant refreshExpiresAt;
    private String status;
    private final List<Aweme> awemes;
    private final String authorizedByUserId;
    private Instant authorizedAt;

    public OauthAccount(
            AccountId id,
            TenantId tenantId,
            Platform platform,
            AdvertiserId advertiserId,
            String accountName,
            EncryptedToken accessToken,
            EncryptedToken refreshToken,
            Instant tokenExpiresAt,
            Instant refreshExpiresAt,
            String status,
            List<Aweme> awemes) {
        this(
                id,
                tenantId,
                platform,
                advertiserId,
                accountName,
                accessToken,
                refreshToken,
                tokenExpiresAt,
                refreshExpiresAt,
                status,
                awemes,
                null);
    }

    public OauthAccount(
            AccountId id,
            TenantId tenantId,
            Platform platform,
            AdvertiserId advertiserId,
            String accountName,
            EncryptedToken accessToken,
            EncryptedToken refreshToken,
            Instant tokenExpiresAt,
            Instant refreshExpiresAt,
            String status,
            List<Aweme> awemes,
            String authorizedByUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.platform = platform;
        this.advertiserId = advertiserId;
        this.accountName = accountName;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.status = status;
        this.awemes = new ArrayList<>(awemes == null ? List.of() : awemes);
        this.authorizedByUserId = authorizedByUserId;
    }

    public static OauthAccount authorize(
            TenantId tenantId,
            AdvertiserId adv,
            String accountName,
            EncryptedToken access,
            EncryptedToken refresh,
            Instant accessExp,
            Instant refreshExp,
            List<Aweme> awemes) {
        return authorize(tenantId, adv, accountName, access, refresh, accessExp, refreshExp, awemes, null);
    }

    public static OauthAccount authorize(
            TenantId tenantId,
            AdvertiserId adv,
            String accountName,
            EncryptedToken access,
            EncryptedToken refresh,
            Instant accessExp,
            Instant refreshExp,
            List<Aweme> awemes,
            String authorizedByUserId) {
        return new OauthAccount(
                new AccountId(UUID.randomUUID().toString()),
                tenantId,
                Platform.QIANCHUAN,
                adv,
                accountName,
                access,
                refresh,
                accessExp,
                refreshExp,
                "ACTIVE",
                awemes,
                authorizedByUserId);
    }

    public void unbind() {
        this.status = "UNBOUND";
    }

    /** Soft removal keeps account IDs referenced by historical plans and audit records. */
    public void remove() {
        this.status = "DELETED";
    }

    public boolean canRemove(Instant now) {
        return !"ACTIVE".equals(status) || (refreshExpiresAt != null && !refreshExpiresAt.isAfter(now));
    }

    public OauthAccount withAuthorizedAt(Instant value) {
        this.authorizedAt = value;
        return this;
    }

    public Instant authorizedAt() {
        return authorizedAt;
    }

    public boolean needsRefresh(Instant now) {
        return "ACTIVE".equals(status) && tokenExpiresAt != null && !now.plus(REFRESH_LEAD).isBefore(tokenExpiresAt);
    }

    public void replaceTokens(EncryptedToken access, EncryptedToken refresh, Instant exp) {
        this.accessToken = access;
        this.refreshToken = refresh;
        this.tokenExpiresAt = exp;
        this.status = "ACTIVE";
    }

    public void markRefreshFailed() {
        this.status = "UNUSABLE";
    }

    public boolean hasUsableAweme() {
        return "ACTIVE".equals(status) && !awemes.isEmpty();
    }

    public Optional<Aweme> primaryAweme() {
        return awemes.stream().filter(Aweme::primary).findFirst();
    }

    public List<Aweme> awemes() {
        return List.copyOf(awemes);
    }

    public AccountId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Platform platform() {
        return platform;
    }

    public AdvertiserId advertiserId() {
        return advertiserId;
    }

    public String accountName() {
        return accountName;
    }

    public EncryptedToken accessToken() {
        return accessToken;
    }

    public EncryptedToken refreshToken() {
        return refreshToken;
    }

    public Instant tokenExpiresAt() {
        return tokenExpiresAt;
    }

    public Instant refreshExpiresAt() {
        return refreshExpiresAt;
    }

    public String status() {
        return status;
    }

    public Optional<String> authorizedByUserId() {
        return Optional.ofNullable(authorizedByUserId);
    }
}
