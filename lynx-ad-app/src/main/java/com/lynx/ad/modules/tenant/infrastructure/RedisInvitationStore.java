package com.lynx.ad.modules.tenant.infrastructure;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.model.MemberInvitation;
import com.lynx.ad.modules.tenant.domain.port.InvitationStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisInvitationStore implements InvitationStore {

    private final StringRedisTemplate redis;

    public RedisInvitationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(MemberInvitation inv, Duration ttl) {
        String value = inv.tenantId().value() + "|" + inv.expiresAt().toString();
        redis.opsForValue().set(key(inv.phone()), value, ttl);
    }

    @Override
    public Optional<MemberInvitation> findByPhone(PhoneNumber phone) {
        String raw = redis.opsForValue().get(key(phone));
        if (raw == null) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\|", 2);
        Instant exp = parts.length > 1 ? Instant.parse(parts[1]) : Instant.now().plusSeconds(3600);
        return Optional.of(new MemberInvitation(phone, new TenantId(parts[0]), exp));
    }

    private static String key(PhoneNumber phone) {
        return "invite:" + phone.value();
    }
}
