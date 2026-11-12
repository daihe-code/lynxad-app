package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.port.OtpStore;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.TenantAuthSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmsVerifyIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired private OtpStore otpStore;
    @Autowired private PasswordEncoder passwords;

    @Test
    void administratorCodesHaveSeparateCooldownAttemptsAndSingleUse() {
        var phone = new PhoneNumber("13900002005");
        var ttl = Duration.ofMinutes(5);
        String adminHash = passwords.encode("333333");
        otpStore.save(phone, passwords.encode("111111"), ttl);
        otpStore.saveChildActivation("fixture-invitation", phone, passwords.encode("222222"), ttl);
        assertTrue(otpStore.reserveAdminSms(phone, ttl));
        assertFalse(otpStore.reserveAdminSms(phone, ttl));
        assertFalse(otpStore.inCooldown(phone));

        otpStore.saveAdminLogin(phone, adminHash, ttl);
        assertFalse(otpStore.peek(phone, "333333"));
        assertFalse(otpStore.verifyAndConsumeAdminLogin(phone, "111111"));
        assertFalse(otpStore.verifyAndConsumeAdminLogin(phone, "222222"));
        for (int i = 0; i < 3; i++) assertFalse(otpStore.verifyAndConsumeAdminLogin(phone, "000000"));
        assertFalse(otpStore.verifyAndConsumeAdminLogin(phone, "333333"));

        otpStore.saveAdminLogin(phone, adminHash, ttl);
        assertTrue(otpStore.verifyAndConsumeAdminLogin(phone, "333333"));
        assertFalse(otpStore.verifyAndConsumeAdminLogin(phone, "333333"));
        assertTrue(otpStore.verifyAndConsume(phone, "111111"));
        assertTrue(otpStore.verifyAndConsumeChildActivation("fixture-invitation", phone, "222222"));
    }

    @Test
    void peekDoesNotConsumeThenLoginSucceeds() throws Exception {
        String phone = "13900002004";
        TenantAuthSupport.post(mvc, "/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        assertNotNull(redis.opsForValue().get("otp:" + phone));
        JsonNode wrong = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.post(
                        mvc,
                        "/api/v1/tenant/auth/sms/verify",
                        "{\"phone\":\"" + phone + "\",\"code\":\"000000\"}",
                        null));
        assertEquals(0, wrong.path("code").asInt());
        assertFalse(wrong.path("data").path("matched").asBoolean());
        assertNotNull(redis.opsForValue().get("otp:" + phone));
        JsonNode right = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.post(
                        mvc,
                        "/api/v1/tenant/auth/sms/verify",
                        "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}",
                        null));
        assertTrue(right.path("data").path("matched").asBoolean());
        assertNotNull(redis.opsForValue().get("otp:" + phone));
        JsonNode login = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.post(
                        mvc, "/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null));
        assertEquals(0, login.path("code").asInt());
    }
}
