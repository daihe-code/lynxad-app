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
class BillingBillsIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void paidRowAndPreviewDebit() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000014");
        BillingItSupport.expireTrial(jdbc, BillingItSupport.tenantId(mapper, mvc, token));
        BillingFlowHelper.subscribeAndPay(mvc, mapper, token, "PROFESSIONAL");
        JsonNode bills = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.get("/api/v1/billing/bills").header("Authorization", "Bearer " + token));
        JsonNode items = bills.path("data").path("items");
        assertTrue(items.size() >= 2);
        boolean paid = false;
        boolean preview = false;
        for (JsonNode item : items) {
            if ("PAID".equals(item.path("status").asText())) {
                paid = true;
            }
            if ("preview-next".equals(item.path("id").asText()) && "PENDING_DEBIT".equals(item.path("status").asText())) {
                preview = true;
            }
        }
        assertEquals(true, paid);
        assertEquals(true, preview);
    }
}
