package com.lynx.ad.application.dataops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.agents.watcher.WatcherJob;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SnapshotJobIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MetricSnapshotJob job;

    @Autowired
    private AuditPollJob auditPollJob;

    @SpyBean
    private WatcherJob watcherJob;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void jobConstructorDependsOnWatcher() {
        boolean found = false;
        for (var ctor : MetricSnapshotJob.class.getDeclaredConstructors()) {
            for (var p : ctor.getParameterTypes()) {
                if (p == WatcherJob.class) {
                    found = true;
                }
            }
        }
        assertTrue(found);
    }

    @Test
    void deliveringPlanSnapshotWritesYuan() throws Exception {
        String token = onboard("13900002002", "快照店");
        String planId = publishDelivering(token);
        job.snapshot();
        Integer rows = jdbc.queryForObject(
                "select count(*) from plan_metric_snapshot where ad_plan_id = ?", Integer.class, planId);
        assertTrue(rows != null && rows >= 1);
        BigDecimal cost = jdbc.queryForObject(
                "select cost from plan_metric_snapshot where ad_plan_id = ? order by snapshot_ts desc, id desc limit 1",
                BigDecimal.class,
                planId);
        assertEquals(0, new BigDecimal("1.0000").compareTo(cost));

        JsonNode list = mapper.readTree(get("/api/v1/delivery/plans", token)).path("data").path("records");
        boolean found = false;
        for (JsonNode row : list) {
            if (planId.equals(row.path("planId").asText())) {
                found = true;
                assertEquals(0, new BigDecimal("1.0000").compareTo(row.path("costYuan").decimalValue()));
            }
        }
        assertTrue(found);
        verify(watcherJob, atLeastOnce()).afterSnapshot(any());
    }

    private String publishDelivering(String token) throws Exception {
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
                                + "\",\"name\":\"快照计划\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\",\"qp02\"]}",
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
        post("/api/v1/delivery/plans/" + planId + "/publish", "{\"idempotencyKey\":\"snap-1\"}", token);
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
