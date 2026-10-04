package com.lynx.ad.application.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class BillingPayForbidIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forbidPaidFlagAndCrossTenant() throws Exception {
        String tokenA = BillingItSupport.onboard(mvc, mapper, "13904000003");
        String tenantA = BillingItSupport.tenantId(mapper, mvc, tokenA);
        BillingItSupport.expireTrial(jdbc, tenantA);
        JsonNode order = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/orders")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"PROFESSIONAL\"}"));
        String orderId = order.path("data").path("orderId").asText();
        JsonNode pay = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\"}"));
        String paymentId = pay.path("data").path("paymentId").asText();

        JsonNode noNonce = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments/" + paymentId + "/mock-complete")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true}"));
        assertTrue(noNonce.path("code").asInt() == 1604 || noNonce.path("code").asInt() == 1605);

        JsonNode paidFlag = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments/" + paymentId + "/mock-complete")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true,\"nonce\":\"x\",\"paid\":true}"));
        assertEquals(1604, paidFlag.path("code").asInt());

        String tokenB = BillingItSupport.onboard(mvc, mapper, "13904000013");
        JsonNode cross = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/orders/" + orderId + "/close")
                        .header("Authorization", "Bearer " + tokenB));
        assertEquals(1604, cross.path("code").asInt());
        JsonNode still = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/subscription").header("Authorization", "Bearer " + tokenA));
        assertFalse(still.path("data").path("hasPaidSubscription").asBoolean());
    }
}
