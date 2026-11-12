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
    private static final DefaultRedisScript<Long> SAVE_LIMITED = new DefaultRedisScript<>(
            "redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', 0); return redis.call('PEXPIRE', KEYS[1], ARGV[2])", Long.class);
    private static final DefaultRedisScript<String> TRY_LIMITED = new DefaultRedisScript<>(
            "local code=redis.call('HGET', KEYS[1], 'code'); if code and redis.call('HINCRBY', KEYS[1], 'attempts', 1)<=5 then return code end", String.class);
    private static final DefaultRedisScript<Long> CONSUME_LIMITED = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'code') == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

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
        return consume(key(phone), code);
    }

    @Override
    public void saveChildActivation(String invitationId, PhoneNumber phone, String codeHash, Duration ttl) {
        saveLimited(activationKey(invitationId, phone), codeHash, ttl);
    }

    @Override
    public boolean verifyAndConsumeChildActivation(String invitationId, PhoneNumber phone, String code) {
        return consumeLimited(activationKey(invitationId, phone), code);
    }

    @Override
    public boolean reserveAdminSms(PhoneNumber phone, Duration cooldown) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("otp:admin:cd:" + phone.value(), "1", cooldown));
    }

    @Override
    public void saveAdminLogin(PhoneNumber phone, String codeHash, Duration ttl) {
        saveLimited("otp:admin:login:" + phone.value(), codeHash, ttl);
    }

    @Override
    public boolean verifyAndConsumeAdminLogin(PhoneNumber phone, String code) {
        return consumeLimited("otp:admin:login:" + phone.value(), code);
    }

    private void saveLimited(String key, String codeHash, Duration ttl) {
        redis.execute(SAVE_LIMITED, List.of(key), codeHash, Long.toString(ttl.toMillis()));
    }

    private boolean consumeLimited(String key, String code) {
        var keys = List.of(key);
        String stored = redis.execute(TRY_LIMITED, keys);
        return matches(stored, code) && Long.valueOf(1).equals(redis.execute(CONSUME_LIMITED, keys, stored));
    }

    private boolean consume(String key, String code) {
        String stored = redis.opsForValue().get(key);
        if (!matches(stored, code)) {
            return false;
        }
        // Verification may take time (BCrypt); consume only the exact value we verified.
        return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key), stored));
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

    private static String activationKey(String invitationId, PhoneNumber phone) {
        return "otp:child:" + invitationId + ":" + phone.value();
    }
}
