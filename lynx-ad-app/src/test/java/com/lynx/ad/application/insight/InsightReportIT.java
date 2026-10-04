package com.lynx.ad.application.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.dataops.application.AuditPollJob;
import com.lynx.ad.modules.dataops.application.MetricSnapshotJob;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsightReportIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MetricSnapshotJob job;

    @Autowired
    private AuditPollJob auditPollJob;

    @Test
    void planAndAccountDimsExportAndIsolation() throws Exception {
        String token = onboard("13900002003", "报表店");
        String planId = publishDelivering(token, "报表计划");
        job.snapshot();

        JsonNode planRows = mapper.readTree(get("/api/v1/insight/reports?dim=PLAN", token)).path("data");
        assertTrue(planRows.size() >= 1);
        JsonNode first = planRows.get(0);
        assertTrue(first.path("name").asText().length() > 0);
        assertTrue(first.hasNonNull("snapshotTs"));
        assertEquals(0, new BigDecimal("1.0000").compareTo(first.path("cost").decimalValue()));
        assertEquals(0, new BigDecimal("2.0000").compareTo(first.path("gmv").decimalValue()));

        JsonNode accountRows = mapper.readTree(get("/api/v1/insight/reports?dim=account", token)).path("data");
        assertEquals(1, accountRows.size());
        assertEquals(0, new BigDecimal("1.0000").compareTo(accountRows.get(0).path("cost").decimalValue()));

        JsonNode filtered = mapper.readTree(get("/api/v1/insight/reports?dim=plan&adIds=" + planId, token)).path("data");
        assertEquals(1, filtered.size());

        JsonNode bad = mapper.readTree(get("/api/v1/insight/reports?dim=product", token));
        assertEquals(400, bad.path("code").asInt());

        JsonNode export = mapper.readTree(post("/api/v1/insight/reports/export", "{}", token));
        assertEquals(501, export.path("code").asInt());

        MvcResult getExport = mvc.perform(MockMvcRequestBuilders.get("/api/v1/insight/reports/export")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertEquals(405, getExport.getResponse().getStatus());

        String other = onboard("13900002004", "隔离店");
        JsonNode isolated = mapper.readTree(get("/api/v1/insight/reports?dim=plan", other)).path("data");
        assertTrue(isolated.isArray());
        assertEquals(0, isolated.size());

        JsonNode cards = mapper.readTree(get("/api/v1/insight/reports?dim=account", token)).path("data");
        BigDecimal cardCost = BigDecimal.ZERO;
        BigDecimal cardGmv = BigDecimal.ZERO;
        for (JsonNode row : cards) {
            cardCost = cardCost.add(row.path("cost").decimalValue());
            cardGmv = cardGmv.add(row.path("gmv").decimalValue());
        }
        assertEquals(0, cardCost.compareTo(accountRows.get(0).path("cost").decimalValue()));
        assertFalse(first.path("cost").asText().contains("100000"));
    }

    private String publishDelivering(String token, String name) throws Exception {
        JsonNode bind = json(post("/api/v1/identity/oauth/accounts/mock-bind", "{}", token)).path("data");
        String accountId = bind.path("accountId").asText();
        String awemeId = bind.path("awemes").get(0).path("awemeId").asText();
        post("/api/v1/catalog/products/select", "{\"awemeId\":\"" + awemeId + "\",\"productIds\":[\"qp01\",\"qp02\"]}", token);
        String planId = json(post(
                        "/api/v1/delivery/plans",
                        "{\"accountId\":\""
                                + accountId
                                + "\",\"awemeId\":\""
                                + awemeId
                                + "\",\"name\":\""
                                + name
                                + "\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\",\"qp02\"]}",
                        token))
                .path("data")
                .path("plan")
                .path("planId")
                .asText();
        String taskId = json(post(
                        "/api/v1/creative/creatives/generate",
                        "{\"planId\":\"" + planId + "\",\"productIds\":[\"qp01\",\"qp02\"],\"mode\":\"STANDARD\"}",
                        token))
                .path("data")
                .path("taskId")
                .asText();
        String packageId = json(get("/api/v1/creative/creatives/tasks/" + taskId, token))
                .path("data")
                .path("packageId")
                .asText();
        post("/api/v1/creative/packages/" + packageId + "/confirm", "{}", token);
        post("/api/v1/delivery/plans/" + planId + "/publish", "{\"idempotencyKey\":\"insight-1\"}", token);
        auditPollJob.pollPendingAudits();
        return planId;
    }

    private String onboard(String phone, String company) throws Exception {
        post("/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        String t = json(post("/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"测\",\"companyName\":\""
                                + company
                                + "\",\"password\":\"Passw0rd!\",\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andReturn();
        post("/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        return json(post("/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
    }

    private String post(String url, String body, String auth) throws Exception {
        var req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            req.header("Authorization", "Bearer " + auth);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    private String get(String url, String auth) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + auth))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }
}
