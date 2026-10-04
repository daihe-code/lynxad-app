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
class BillingExpireLazyIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void lazyExpireAndAutoRenewPersist() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000010");
        String tenantId = BillingItSupport.tenantId(mapper, mvc, token);
        BillingItSupport.expireTrial(jdbc, tenantId);
        JsonNode noSub = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.patch("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoRenew\":false}"));
        assertEquals(1604, noSub.path("code").asInt());
        BillingFlowHelper.subscribeAndPay(mvc, mapper, token, "PROFESSIONAL");
        JsonNode off = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.patch("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoRenew\":false}"));
        assertEquals(false, off.path("data").path("autoRenew").asBoolean());
        JsonNode again = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token));
        assertEquals(false, again.path("data").path("autoRenew").asBoolean());
        BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.patch("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoRenew\":true}"));
        jdbc.update("update billing_subscription set period_end = now() - interval '1 day', auto_renew = true where tenant_id = ?", tenantId);
        JsonNode expired = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token));
        assertEquals(false, expired.path("data").path("hasPaidSubscription").asBoolean());
        JsonNode publish = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/delivery/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"x\",\"awemeId\":\"a\",\"name\":\"过期\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\"]}"));
        assertEquals(1102, publish.path("code").asInt());
        assertTrue(publish.path("message").asText().contains("无有效订阅"));
    }
}
