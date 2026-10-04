package com.lynx.ad.common.idempotency;

import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.TenantId;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisIdempotencyService implements IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public RedisIdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> findReplay(TenantId tenantId, IdempotencyKey key) {
        return Optional.ofNullable(redis.opsForValue().get(redisKey(tenantId, key)));
    }

    @Override
    public void saveReplay(TenantId tenantId, IdempotencyKey key, String responseJson) {
        redis.opsForValue().set(redisKey(tenantId, key), responseJson, TTL);
    }

    private static String redisKey(TenantId tenantId, IdempotencyKey key) {
        return "idem:" + tenantId.value() + ":" + key.value();
    }
}
