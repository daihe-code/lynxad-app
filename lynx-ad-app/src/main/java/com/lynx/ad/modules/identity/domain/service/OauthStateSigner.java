package com.lynx.ad.modules.identity.domain.service;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class OauthStateSigner {

    public static final Duration TTL = Duration.ofMinutes(15);
    private static final String HMAC = "HmacSHA256";

    public record VerifiedState(TenantId tenantId, UserId userId) {}

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public OauthStateSigner(String hmacKey) {
        this.key = hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    /** 仅用于构造 3 段旧 state，供拒绝测例使用。生产签发必须走 4 段。 */
    public String sign(TenantId tenantId) {
        return signLegacyThreePart(tenantId, Instant.now().plus(TTL));
    }

    public String sign(TenantId tenantId, Instant expiresAt) {
        return signLegacyThreePart(tenantId, expiresAt);
    }

    public String sign(TenantId tenantId, UserId userId) {
        return sign(tenantId, userId, Instant.now().plus(TTL));
    }

    public String sign(TenantId tenantId, UserId userId, Instant expiresAt) {
        if (userId == null || userId.value() == null || userId.value().isBlank()) {
            throw new BizException(1206, "授权状态无效");
        }
        String nonce = HexFormat.of().formatHex(random.generateSeed(16));
        String payload = tenantId.value() + "|" + userId.value() + "|" + nonce + "|" + expiresAt.getEpochSecond();
        return encode(payload) + "." + encode(mac(payload));
    }

    public VerifiedState verify(String state) {
        String payload = verifiedPayload(state);
        String[] parts = payload.split("\\|", 4);
        if (parts.length != 4) {
            throw new BizException(1206, "授权状态无效");
        }
        long exp = parseExp(parts[3]);
        if (Instant.now().getEpochSecond() > exp) {
            throw new BizException(1206, "授权已过期");
        }
        return new VerifiedState(new TenantId(parts[0]), new UserId(parts[1]));
    }

    private String signLegacyThreePart(TenantId tenantId, Instant expiresAt) {
        String nonce = HexFormat.of().formatHex(random.generateSeed(16));
        String payload = tenantId.value() + "|" + nonce + "|" + expiresAt.getEpochSecond();
        return encode(payload) + "." + encode(mac(payload));
    }

    private String verifiedPayload(String state) {
        if (state == null || state.isBlank()) {
            throw new BizException(1206, "授权状态无效");
        }
        int dot = state.indexOf('.');
        if (dot <= 0 || dot == state.length() - 1) {
            throw new BizException(1206, "授权状态无效");
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(state.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] expected = mac(payload);
            byte[] actual = Base64.getUrlDecoder().decode(state.substring(dot + 1));
            if (!constantTimeEquals(expected, actual)) {
                throw new BizException(1206, "授权状态无效");
            }
            return payload;
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(1206, "授权状态无效");
        }
    }

    private static long parseExp(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new BizException(1206, "授权状态无效");
        }
    }

    private byte[] mac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 不可用", ex);
        }
    }

    private static String encode(String raw) {
        return encode(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int acc = 0;
        for (int i = 0; i < a.length; i++) {
            acc |= a[i] ^ b[i];
        }
        return acc == 0;
    }
}
