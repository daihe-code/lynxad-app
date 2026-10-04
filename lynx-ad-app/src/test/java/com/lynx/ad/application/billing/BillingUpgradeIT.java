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
class BillingUpgradeIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void basicThenUpgradeProfessional420() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000007");
        String tenantId = BillingItSupport.tenantId(mapper, mvc, token);
        BillingItSupport.expireTrial(jdbc, tenantId);
        jdbc.update(
                "insert into billing_order (id, tenant_id, plan_code, type, status, list_price_fen, payable_fen) values (?,?,?,?,?,?,?)",
                "hist-basic",
                tenantId,
                "BASIC",
                "FIRST_SUBSCRIBE",
                "PAID",
                29900,
                29900);
        JsonNode basic = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"BASIC\"}"));
        assertEquals(29900, basic.path("data").path("payableFen").asInt());
        JsonNode payBasic = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + basic.path("data").path("orderId").asText() + "\"}"));
        BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments/"
                                + payBasic.path("data").path("paymentId").asText()
                                + "/mock-complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true,\"nonce\":\""
                                + payBasic.path("data").path("mockNonce").asText()
                                + "\"}"));
        JsonNode before = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token));
        String start = before.path("data").path("periodStart").asText();
        String end = before.path("data").path("periodEnd").asText();
        JsonNode upgrade = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"PROFESSIONAL\"}"));
        assertEquals(42000, upgrade.path("data").path("payableFen").asInt());
        JsonNode payUpgrade = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + upgrade.path("data").path("orderId").asText() + "\"}"));
        BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/billing/payments/"
                                + payUpgrade.path("data").path("paymentId").asText()
                                + "/mock-complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true,\"nonce\":\""
                                + payUpgrade.path("data").path("mockNonce").asText()
                                + "\"}"));
        JsonNode after = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token));
        assertEquals("PROFESSIONAL", after.path("data").path("planCode").asText());
        assertEquals(start, after.path("data").path("periodStart").asText());
        assertEquals(end, after.path("data").path("periodEnd").asText());
    }
}
