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
class PhoneChangeIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void rebindKeepsCurrentJwtAndOldPhoneCannotLogin() throws Exception {
        String oldPhone = "13900002008";
        String newPhone = "13900002009";
        var session = TenantAuthSupport.onboard(mvc, mapper, oldPhone, "换绑店");
        TenantAuthSupport.post(mvc, "/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + newPhone + "\"}", null);
        JsonNode changed = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.put(
                        mvc,
                        "/api/v1/tenant/users/me/phone",
                        "{\"phone\":\"" + newPhone + "\",\"code\":\"123456\"}",
                        session.token()));
        assertEquals(0, changed.path("code").asInt());
        JsonNode me = TenantAuthSupport.json(mapper, TenantAuthSupport.get(mvc, "/api/v1/tenant/users/me", session.token()));
        assertEquals(0, me.path("code").asInt());
        assertEquals(newPhone, me.path("data").path("phone").asText());
        JsonNode oldPwd = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.post(
                        mvc,
                        "/api/v1/tenant/auth/password/login",
                        "{\"phone\":\"" + oldPhone + "\",\"password\":\"Passw0rd!\"}",
                        null));
        assertEquals(1101, oldPwd.path("code").asInt());
    }
}
