package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.TenantAuthSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "lynx.ad.sms.mode=off")
class SmsOffIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void offSendIs1112AndFixedCodeCannotOpenSession() throws Exception {
        String phone = "13900002002";
        JsonNode send = TenantAuthSupport.json(
                mapper, TenantAuthSupport.post(mvc, "/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null));
        assertEquals(1112, send.path("code").asInt());
        assertNull(redis.opsForValue().get("otp:" + phone));
        JsonNode login = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.post(
                        mvc, "/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null));
        assertEquals(1101, login.path("code").asInt());
    }
}
