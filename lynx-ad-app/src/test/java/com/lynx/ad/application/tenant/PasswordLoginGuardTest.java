package com.lynx.ad.application.tenant;
import com.lynx.ad.modules.tenant.application.PasswordLoginGuard;
import com.lynx.ad.common.exception.BizException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordLoginGuardTest {
    @Test void failuresShowThirdWarningAndFifthPasswordOnlyLock(){
        var redis=mock(StringRedisTemplate.class);var guard=new PasswordLoginGuard(redis);
        when(redis.execute(any(RedisScript.class),anyList())).thenReturn(1L,3L,5L);
        assertEquals("手机号或密码错误",assertThrows(BizException.class,()->guard.failed("13800000000")).getMessage());
        assertTrue(assertThrows(BizException.class,()->guard.failed("13800000000")).getMessage().contains("已错误3次"));
        String message=assertThrows(BizException.class,()->guard.failed("13800000000")).getMessage();assertTrue(message.contains("15分钟"));assertTrue(message.contains("验证码"));
    }
    @Test void anotherReplicaUsesSameLockAndSuccessfulLoginClearsBothKeys(){
        var redis=mock(StringRedisTemplate.class);when(redis.hasKey("lynx:password:{13800000000}:locked")).thenReturn(true);
        var a=new PasswordLoginGuard(redis);var b=new PasswordLoginGuard(redis);
        assertThrows(BizException.class,()->a.check("13800000000"));assertThrows(BizException.class,()->b.check("13800000000"));
        b.clear("13800000000");verify(redis).delete(List.of("lynx:password:{13800000000}:failures","lynx:password:{13800000000}:locked"));
    }
}
