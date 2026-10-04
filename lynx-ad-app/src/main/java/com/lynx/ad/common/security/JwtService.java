package com.lynx.ad.common.security;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final LynxAdProperties properties;

    public JwtService(LynxAdProperties properties) {
        this.properties = properties;
    }

    public String issue(UserId userId, Optional<TenantId> tenantId, MemberRole memberRole, SysRole sysRole) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.value())
                .claim("srole", sysRole.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getJwt().getTtlSec())))
                .signWith(key());
        tenantId.ifPresent(id -> builder.claim("tid", id.value()));
        if (memberRole != null) {
            builder.claim("mrole", memberRole.name());
        }
        return builder.compact();
    }

    public TenantPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UserId userId = new UserId(claims.getSubject());
            Optional<TenantId> tenantId = Optional.ofNullable(claims.get("tid", String.class)).map(TenantId::new);
            Optional<MemberRole> memberRole = Optional.ofNullable(claims.get("mrole", String.class)).map(MemberRole::valueOf);
            SysRole sysRole = SysRole.valueOf(Optional.ofNullable(claims.get("srole", String.class)).orElse(SysRole.USER.name()));
            Optional<String> sessionId = Optional.ofNullable(claims.getId());
            return new TenantPrincipal(userId, tenantId, memberRole, sysRole, sessionId);
        } catch (Exception ex) {
            throw new BizException(401, "未登录");
        }
    }

    private SecretKey key() {
        byte[] bytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
