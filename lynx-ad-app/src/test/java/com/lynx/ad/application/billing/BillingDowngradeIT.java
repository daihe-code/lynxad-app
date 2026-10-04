package com.lynx.ad.application.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingDowngradeIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void downgradeSchedulesWithoutPayment() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000009");
        BillingItSupport.expireTrial(jdbc, BillingItSupport.tenantId(mapper, mvc, token));
        BillingFlowHelper.subscribeAndPay(mvc, mapper, token, "PROFESSIONAL");
        JsonNode down = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"BASIC\"}"));
        assertEquals(0, down.path("code").asInt());
        assertEquals("DOWNGRADE", down.path("data").path("type").asText());
        assertEquals(0, down.path("data").path("payableFen").asInt());
        JsonNode sub = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token));
        assertEquals("PROFESSIONAL", sub.path("data").path("planCode").asText());
        assertEquals("PENDING_DOWNGRADE", sub.path("data").path("status").asText());
    }
}
