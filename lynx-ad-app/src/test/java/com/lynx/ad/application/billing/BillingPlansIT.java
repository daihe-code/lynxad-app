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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingPlansIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void threePlansAtLockedPrices() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000011");
        JsonNode plans = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/plans").header("Authorization", "Bearer " + token));
        JsonNode items = plans.path("data").path("items");
        assertEquals(3, items.size());
        assertEquals("BASIC", items.get(0).path("code").asText());
        assertEquals(199, items.get(0).path("listPriceYuan").asInt());
        assertEquals(0, items.get(0).path("childAccountLimit").asInt(-1));
        assertEquals(2000, items.get(0).path("periodPoints").asInt());
        assertEquals("PROFESSIONAL", items.get(1).path("code").asText());
        assertEquals(7099, items.get(1).path("listPriceYuan").asInt());
        assertEquals(3, items.get(1).path("childAccountLimit").asInt(-1));
        assertEquals(3000, items.get(1).path("periodPoints").asInt());
        assertEquals("FLAGSHIP", items.get(2).path("code").asText());
        assertEquals(22999, items.get(2).path("listPriceYuan").asInt());
        assertEquals(10, items.get(2).path("childAccountLimit").asInt(-1));
        assertEquals(5000, items.get(2).path("periodPoints").asInt());
    }
}
