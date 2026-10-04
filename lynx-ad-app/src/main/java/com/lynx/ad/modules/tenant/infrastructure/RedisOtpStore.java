package com.lynx.ad.modules.tenant.infrastructure;

import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.port.OtpStore;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class RedisOtpStore implements OtpStore {

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
        boolean ok = match(phone, code);
        if (ok) {
            redis.delete(key(phone));
        }
        return ok;
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
