package com.lynx.ad.modules.tenant.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.model.PasswordHasher;
import com.lynx.ad.modules.tenant.infrastructure.RedisOtpStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

class CredentialConcurrencyTest {
    @org.junit.jupiter.api.BeforeAll static void initializeMapperMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), UserPo.class);
    }
    @Test void consumedOrReplacedOtpCannotBeAcceptedByConcurrentVerifier() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("hash");
        var encoder = mock(PasswordEncoder.class);
        when(encoder.matches("123456", "hash")).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        var otp = new RedisOtpStore(redis, encoder);
        assertFalse(otp.verifyAndConsume(new PhoneNumber("13900009801"), "123456"));
    }

    @Test void staleCredentialWriteFailsInsteadOfReportingPasswordChangeSuccess() {
        var mapper = mock(UserMapper.class);
        var profile = mock(UserProfileMapper.class);
        UserPo current = new UserPo();
        current.setId("user"); current.setPhone("13900009801"); current.setPasswordHash("newer-hash");
        when(mapper.selectById("user")).thenReturn(current);
        var stale = new User(new UserId("user"), new PhoneNumber("13900009801"), "old-hash", null);
        var hasher = mock(PasswordHasher.class);
        when(hasher.hash("Password123")).thenReturn("new-hash");
        stale.setPassword("Password123", hasher);
        assertEquals(409, assertThrows(BizException.class, () -> new MpUserRepository(mapper, profile).save(stale)).code());
    }
}
