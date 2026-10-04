package com.lynx.ad.modules.identity.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class OauthAuthCodeAudit {

    public static final String RECEIVED = "RECEIVED";
    public static final String USED = "USED";
    public static final String EXCHANGE_FAILED = "EXCHANGE_FAILED";

    private final String id;
    private final TenantId tenantId;
    private final String userId;
    private final String authCodeHash;
    private String advertiserId;
    private String status;
    private final Instant createdAt;
    private Instant usedAt;

    public OauthAuthCodeAudit(
            String id,
            TenantId tenantId,
            String userId,
            String authCodeHash,
            String advertiserId,
            String status,
            Instant createdAt,
            Instant usedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.authCodeHash = authCodeHash;
        this.advertiserId = advertiserId;
        this.status = status;
        this.createdAt = createdAt;
        this.usedAt = usedAt;
    }

    public static OauthAuthCodeAudit received(TenantId tenantId, String userId, String authCodePlain) {
        return new OauthAuthCodeAudit(
                UUID.randomUUID().toString(),
                tenantId,
                userId,
                sha256Hex(authCodePlain),
                null,
                RECEIVED,
                Instant.now(),
                null);
    }

    public static String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    public void markUsed(String advertiserId) {
        this.status = USED;
        this.advertiserId = advertiserId;
        this.usedAt = Instant.now();
    }

    public void markFailed() {
        this.status = EXCHANGE_FAILED;
    }

    public boolean used() {
        return USED.equals(status);
    }

    public String id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public String authCodeHash() {
        return authCodeHash;
    }

    public String advertiserId() {
        return advertiserId;
    }

    public String status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant usedAt() {
        return usedAt;
    }
}
