package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.exception.BizException;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis keeps password-only restrictions consistent across API replicas. SMS remains available. */
@Component
public class PasswordLoginGuard {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> FAILURE = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[2]) == 1 then return 5 end
        local failures = redis.call('INCR', KEYS[1])
        redis.call('EXPIRE', KEYS[1], 900)
        if failures >= 5 then
          redis.call('SET', KEYS[2], '1', 'EX', 900)
          redis.call('DEL', KEYS[1])
        end
        return failures
        """, Long.class);
    public PasswordLoginGuard(StringRedisTemplate redis) { this.redis=redis; }
    private String count(String phone) { return "lynx:password:{"+phone+"}:failures"; }
    private String lock(String phone) { return "lynx:password:{"+phone+"}:locked"; }
    public void check(String phone) {
        if(Boolean.TRUE.equals(redis.hasKey(lock(phone)))) throw locked();
    }
    public void failed(String phone) {
        Long failures=redis.execute(FAILURE,List.of(count(phone),lock(phone)));
        if(failures==null)throw new BizException(1101,"密码登录暂不可用，请使用验证码登录");
        if(failures>=5)throw locked();
        if(failures>=3)throw new BizException(1101,"手机号或密码已错误"+failures+"次，连续错误达5次后账号将临时限制登录，建议您使用验证码方式登录");
        throw new BizException(1101,"手机号或密码错误");
    }
    public void clear(String phone) { redis.delete(List.of(count(phone),lock(phone))); }
    private BizException locked() { return new BizException(1101,"手机号或密码已错误5次，密码登录将临时锁定15分钟。锁定期间，您仍可使用验证码方式登录"); }
}
