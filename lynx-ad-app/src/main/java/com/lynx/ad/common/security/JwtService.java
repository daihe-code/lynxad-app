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
import javax.crypto.Mac;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import com.lynx.ad.modules.tenant.domain.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final LynxAdProperties properties;
    private final UserRepository users;

    public JwtService(LynxAdProperties properties) {
        this(properties, null);
    }

    @Autowired
    public JwtService(LynxAdProperties properties, UserRepository users) {
        this.properties = properties;
        this.users = users;
    }

    public String issue(UserId userId, Optional<TenantId> tenantId, MemberRole memberRole, SysRole sysRole) {
        return issue(userId, tenantId, memberRole, sysRole,
                sysRole == SysRole.USER ? credentialVersion(userId) : null);
    }

    public String issueForUser(User user, Optional<TenantId> tenantId, MemberRole memberRole) {
        // Bind login to the exact credential that was authenticated, even if a reset races with issuance.
        return issue(user.id(), tenantId, memberRole, SysRole.USER, credentialVersion(user));
    }

    private String issue(UserId userId, Optional<TenantId> tenantId, MemberRole memberRole, SysRole sysRole, String version) {
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
        if (sysRole == SysRole.USER) {
            builder.claim("cv", version);
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
            if (sysRole == SysRole.USER) {
                String version = claims.get("cv", String.class);
                if (version == null || !MessageDigest.isEqual(version.getBytes(StandardCharsets.UTF_8),
                        credentialVersion(userId).getBytes(StandardCharsets.UTF_8))) {
                    throw new BizException(401, "未登录");
                }
            }
            Optional<String> sessionId = Optional.ofNullable(claims.getId());
            return new TenantPrincipal(userId, tenantId, memberRole, sysRole, sessionId);
        } catch (Exception ex) {
            throw new BizException(401, "未登录");
        }
    }

    private String credentialVersion(UserId userId) {
        if (users == null) {
            throw new BizException(401, "未登录");
        }
        return credentialVersion(users.findById(userId).orElseThrow(() -> new BizException(401, "未登录")));
    }

    private String credentialVersion(User user) {
        try {
            // Bind sessions to the durable credential without exposing its password hash in the JWT.
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key().getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((user.id().value() + ":" +
                    Optional.ofNullable(user.passwordHash()).orElse("")).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to bind session credential", ex);
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
