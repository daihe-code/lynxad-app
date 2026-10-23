package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.TenantAuthSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordChangeIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void firstSetWrongOldAndIllegalNew() throws Exception {
        String phone = "13900002010";
        String token = TenantAuthSupport.smsLogin(mvc, mapper, phone);
        JsonNode first = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.put(
                        mvc,
                        "/api/v1/tenant/users/me/password",
                        "{\"newPassword\":\"Passw0rd!\"}",
                        token));
        assertEquals(0, first.path("code").asInt());
        token = TenantAuthSupport.smsLogin(mvc, mapper, phone);
        JsonNode badOld = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.put(
                        mvc,
                        "/api/v1/tenant/users/me/password",
                        "{\"oldPassword\":\"wrong-old-1\",\"newPassword\":\"Passw0rd!\"}",
                        token));
        assertEquals(1107, badOld.path("code").asInt());
        JsonNode illegal = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.put(
                        mvc,
                        "/api/v1/tenant/users/me/password",
                        "{\"oldPassword\":\"Passw0rd!\",\"newPassword\":\"abcdefg\"}",
                        token));
        assertEquals(1002, illegal.path("code").asInt());
    }
}
