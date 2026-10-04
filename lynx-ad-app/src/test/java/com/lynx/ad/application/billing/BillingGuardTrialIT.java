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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingGuardTrialIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void activeTrialCanCreatePlanWithoutPay() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000005");
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        JsonNode accounts = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/identity/oauth/accounts").header("Authorization", "Bearer " + token));
        String accountId = accounts.path("data").get(0).path("accountId").asText();
        String awemeId = accounts.path("data").get(0).path("awemes").get(0).path("awemeId").asText();
        JsonNode draft = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/delivery/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\""
                                + accountId
                                + "\",\"awemeId\":\""
                                + awemeId
                                + "\",\"name\":\"试用计划\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\"]}"));
        assertEquals(0, draft.path("code").asInt());
    }
}
