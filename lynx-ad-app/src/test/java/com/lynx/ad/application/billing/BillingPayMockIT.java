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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingPayMockIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void expiredFirstProfessional719ThenPublish() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000001");
        String tenantId = BillingItSupport.tenantId(mapper, mvc, token);
        BillingItSupport.expireTrial(jdbc, tenantId);

        JsonNode blocked = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/delivery/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"x\",\"awemeId\":\"a\",\"name\":\"不可发\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\"]}"));
        assertEquals(1102, blocked.path("code").asInt());
        assertTrue(blocked.path("message").asText().contains("无有效订阅"));

        JsonNode order = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"PROFESSIONAL\"}"));
        assertEquals(0, order.path("code").asInt());
        assertEquals(71900, order.path("data").path("payableFen").asInt());
        assertTrue(order.path("data").path("discountLabel").asText().contains("9折"));
        String orderId = order.path("data").path("orderId").asText();

        JsonNode pay = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\"}"));
        String paymentId = pay.path("data").path("paymentId").asText();
        String nonce = pay.path("data").path("mockNonce").asText();
        JsonNode done = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments/" + paymentId + "/mock-complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true,\"nonce\":\"" + nonce + "\"}"));
        assertEquals(0, done.path("code").asInt());

        JsonNode sub = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token));
        assertTrue(sub.path("data").path("hasPaidSubscription").asBoolean());
        assertEquals("PROFESSIONAL", sub.path("data").path("planCode").asText());
    }
}
