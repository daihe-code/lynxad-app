package com.lynx.ad.application.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurrentUserCompatIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void meKeepsTrialFieldsAndAddsSubscription() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000006");
        JsonNode me = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/tenant/users/me").header("Authorization", "Bearer " + token));
        JsonNode data = me.path("data");
        assertTrue(data.has("remainingTrialDays"));
        assertTrue(data.has("displayPlanCode"));
        assertTrue(data.path("subscriptionPlanCode").isNull() || data.path("subscriptionPlanCode").asText().isBlank());
        BillingItSupport.expireTrial(jdbc, data.path("tenantId").asText());
        BillingFlowHelper.subscribeAndPay(mvc, mapper, token, "PROFESSIONAL");
        JsonNode paid = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/tenant/users/me").header("Authorization", "Bearer " + token));
        assertEquals("PROFESSIONAL", paid.path("data").path("subscriptionPlanCode").asText());
        assertEquals("PROFESSIONAL", paid.path("data").path("displayPlanCode").asText());
        assertTrue(paid.path("data").has("remainingTrialDays"));
    }
}
