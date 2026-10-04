package com.lynx.ad.modules.creative.domain.service;

import com.lynx.ad.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class MediaTokenSigner {

    private MediaTokenSigner() {}

    public static String sign(String secret, String tenantId, String objectKey, Instant exp) {
        String payload = tenantId + "|" + objectKey + "|" + exp.getEpochSecond();
        return payload + "." + hmac(secret, payload);
    }

    public static TokenPayload verify(String secret, String token) {
        if (token == null || !token.contains(".")) {
            throw new BizException(1508, "媒体令牌无效或过期");
        }
        int dot = token.lastIndexOf('.');
        String payload = token.substring(0, dot);
        String mac = token.substring(dot + 1);
        if (!hmac(secret, payload).equals(mac)) {
            throw new BizException(1508, "媒体令牌无效或过期");
        }
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            throw new BizException(1508, "媒体令牌无效或过期");
        }
        long exp;
        try {
            exp = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            throw new BizException(1508, "媒体令牌无效或过期");
        }
        if (Instant.now().getEpochSecond() > exp) {
            throw new BizException(1508, "媒体令牌无效或过期");
        }
        return new TokenPayload(parts[0], parts[1], Instant.ofEpochSecond(exp));
    }

    private static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("媒体令牌签名失败", ex);
        }
    }

    public record TokenPayload(String tenantId, String objectKey, Instant exp) {}
}
