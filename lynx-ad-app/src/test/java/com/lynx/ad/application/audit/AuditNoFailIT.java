package com.lynx.ad.application.audit;

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
class AuditNoFailIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private com.lynx.ad.common.security.JwtService jwt;

    @Test
    void failedLoginDoesNotAddRowAndNoTenantGets1602() throws Exception {
        var session = TenantAuthSupport.onboard(mvc, mapper, "13900002014", "失败店");
        JsonNode before = TenantAuthSupport.json(
                mapper, TenantAuthSupport.get(mvc, "/api/v1/audit/records", session.token()));
        long total = before.path("data").path("total").asLong();
        TenantAuthSupport.post(
                mvc,
                "/api/v1/tenant/auth/password/login",
                "{\"phone\":\"13900002014\",\"password\":\"wrong-pass-1\"}",
                null);
        JsonNode after = TenantAuthSupport.json(
                mapper, TenantAuthSupport.get(mvc, "/api/v1/audit/records", session.token()));
        assertEquals(total, after.path("data").path("total").asLong());

        String lone = TenantAuthSupport.smsLogin(mvc, mapper, "13900002015");
        assertEquals(0, TenantAuthSupport.json(mapper, TenantAuthSupport.get(mvc, "/api/v1/audit/records", lone)).path("code").asInt());
        String tenantless = jwt.issue(jwt.parse(lone).userId(), java.util.Optional.empty(), null, com.lynx.ad.common.security.SysRole.USER);
        JsonNode isolated = TenantAuthSupport.json(mapper, TenantAuthSupport.get(mvc, "/api/v1/audit/records", tenantless));
        assertEquals(1602, isolated.path("code").asInt());
    }
}
