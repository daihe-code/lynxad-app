package com.lynx.ad.e2e;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.dataops.application.AuditPollJob;
import com.lynx.ad.modules.dataops.application.MetricSnapshotJob;
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
class ClosedLoopE2ETest extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MetricSnapshotJob snapshotJob;

    @Autowired
    private AuditPollJob auditPollJob;

    @Test
    void otpOnboardBindSelectDraftGeneratePublish() throws Exception {
        post("/api/v1/tenant/auth/sms/send", "{\"phone\":\"13900000003\"}", null);
        String token = json(post("/api/v1/tenant/auth/sms/login", "{\"phone\":\"13900000003\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"闭环\",\"companyName\":\"闭环店\",\"password\":\"Passw0rd!\",\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andReturn();
        post("/api/v1/tenant/auth/sms/send", "{\"phone\":\"13900000003\"}", null);
        token = json(post("/api/v1/tenant/auth/sms/login", "{\"phone\":\"13900000003\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
        JsonNode bind = json(post("/api/v1/identity/oauth/accounts/mock-bind", "{}", token)).path("data");
        String accountId = bind.path("accountId").asText();
        String awemeId = bind.path("awemes").get(0).path("awemeId").asText();
        post("/api/v1/catalog/products/select", "{\"awemeId\":\"" + awemeId + "\",\"productIds\":[\"qp01\",\"qp02\"]}", token);
        JsonNode draft = json(post(
                        "/api/v1/delivery/plans",
                        "{\"accountId\":\""
                                + accountId
                                + "\",\"awemeId\":\""
                                + awemeId
                                + "\",\"name\":\"闭环计划A\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\",\"qp02\"]}",
                        token))
                .path("data")
                .path("plan");
        String planId = draft.path("planId").asText();
        JsonNode gen = json(post(
                        "/api/v1/creative/creatives/generate",
                        "{\"planId\":\"" + planId + "\",\"productIds\":[\"qp01\",\"qp02\"],\"mode\":\"STANDARD\"}",
                        token))
                .path("data");
        String taskId = gen.path("taskId").asText();
        JsonNode task = com.lynx.ad.support.CreativeTaskSupport.awaitSucceeded(mvc, mapper, taskId, token);
        String packageId = task.path("packageId").asText();
        if (packageId != null && !packageId.isBlank()) {
            post("/api/v1/creative/packages/" + packageId + "/confirm", "{}", token);
        }
        JsonNode pub = json(post("/api/v1/delivery/plans/" + planId + "/publish", "{\"idempotencyKey\":\"e2e-1\"}", token))
                .path("data");
        assertNotNull(pub.path("qcPlanId").asText(null));
        auditPollJob.pollPendingAudits();
        String list = get("/api/v1/delivery/plans", token);
        assertTrue(list.contains(planId));
        snapshotJob.snapshot();
        JsonNode insight = json(get("/api/v1/insight/reports?dim=plan", token)).path("data");
        assertTrue(insight.isArray() && insight.size() > 0);
    }

    private String post(String url, String body, String token) throws Exception {
        var req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    private String get(String url, String token) throws Exception {
        var req = MockMvcRequestBuilders.get(url);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }
}
