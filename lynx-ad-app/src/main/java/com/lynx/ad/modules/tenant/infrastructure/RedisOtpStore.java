package com.lynx.ad.modules.tenant.infrastructure;

import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.port.OtpStore;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class RedisOtpStore implements OtpStore {

    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;
    private final PasswordEncoder encoder;

    public RedisOtpStore(StringRedisTemplate redis, PasswordEncoder encoder) {
        this.redis = redis;
        this.encoder = encoder;
    }

    @Override
    public void save(PhoneNumber phone, String codeHash, Duration ttl) {
        redis.opsForValue().set(key(phone), codeHash, ttl);
    }

    @Override
    public boolean verifyAndConsume(PhoneNumber phone, String code) {
        String stored = redis.opsForValue().get(key(phone));
        if (!matches(stored, code)) {
            return false;
        }
        // Verification may take time (BCrypt); consume only the exact value we verified.
        return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key(phone)), stored));
    }

    @Override
    public boolean peek(PhoneNumber phone, String code) {
        return match(phone, code);
    }

    @Override
    public boolean inCooldown(PhoneNumber phone) {
        Boolean has = redis.hasKey(cooldownKey(phone));
        return Boolean.TRUE.equals(has);
    }

    @Override
    public void markCooldown(PhoneNumber phone, Duration ttl) {
        redis.opsForValue().set(cooldownKey(phone), "1", ttl);
    }

    private boolean match(PhoneNumber phone, String code) {
        String stored = redis.opsForValue().get(key(phone));
        return matches(stored, code);
    }

    private boolean matches(String stored, String code) {
        if (stored == null || code == null) {
            return false;
        }
        return stored.equals(code) || encoder.matches(code, stored);
    }

    public static String key(PhoneNumber phone) {
        return "otp:" + phone.value();
    }

    public static String cooldownKey(PhoneNumber phone) {
        return "otp:cd:" + phone.value();
    }
}
