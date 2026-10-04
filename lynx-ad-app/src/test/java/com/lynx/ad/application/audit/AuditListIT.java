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
class AuditListIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void onboardedLoginProducesMaskedLoginRow() throws Exception {
        var session = TenantAuthSupport.onboard(mvc, mapper, "13900002011", "日志店");
        JsonNode page = TenantAuthSupport.json(
                mapper, TenantAuthSupport.get(mvc, "/api/v1/audit/records", session.token()));
        assertEquals(0, page.path("code").asInt());
        assertTrue(page.path("data").path("total").asLong() >= 1);
        boolean login = false;
        for (JsonNode row : page.path("data").path("list")) {
            if ("LOGIN".equals(row.path("actionType").asText())) {
                login = true;
                String masked = row.path("ipMasked").asText();
                assertTrue(!masked.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"));
            }
        }
        assertTrue(login);
    }
}
