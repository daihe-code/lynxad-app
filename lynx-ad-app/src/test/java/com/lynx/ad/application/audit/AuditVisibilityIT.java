package com.lynx.ad.application.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class AuditVisibilityIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void memberBSeesMemberA() throws Exception {
        var a = TenantAuthSupport.onboard(mvc, mapper, "13900002012", "可见店");
        TenantAuthSupport.post(mvc, "/api/v1/tenant/members/invite", "{\"phone\":\"13900002013\"}", a.token());
        String bToken = TenantAuthSupport.smsLogin(mvc, mapper, "13900002013");
        TenantAuthSupport.put(
                mvc,
                "/api/v1/tenant/users/me/profile",
                "{\"nickname\":\"甲改\",\"companyName\":\"可见店\"}",
                a.token());
        JsonNode page = TenantAuthSupport.json(mapper, TenantAuthSupport.get(mvc, "/api/v1/audit/records", bToken));
        assertEquals(0, page.path("code").asInt());
        boolean sawAccount = false;
        boolean sawLogin = false;
        for (JsonNode row : page.path("data").path("list")) {
            if ("ACCOUNT".equals(row.path("actionType").asText())) {
                sawAccount = true;
            }
            if ("LOGIN".equals(row.path("actionType").asText())) {
                sawLogin = true;
            }
        }
        assertTrue(sawAccount);
        assertTrue(sawLogin);
    }
}
