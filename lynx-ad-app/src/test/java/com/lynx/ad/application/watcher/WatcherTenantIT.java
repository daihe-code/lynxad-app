package com.lynx.ad.application.watcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.agents.watcher.WatcherJob;
import com.lynx.ad.application.billing.BillingItSupport;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.modules.dataops.application.AuditPollJob;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.WatcherFixture;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatcherTenantIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AuditPollJob auditPollJob;

    @Autowired
    private WatcherJob watcherJob;

    @Autowired
    private MetricSnapshotRepository snapshots;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tenantBCannotSeeTenantA() throws Exception {
        String tokenA = WatcherItSupport.onboard(mvc, mapper, "13900003301", "甲店");
        String planA = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, tokenA);
        String tenantA = WatcherItSupport.tenantId(mvc, mapper, tokenA);
        Instant t0 = Instant.parse("2026-08-01T00:00:00Z");
        WatcherFixture.append(snapshots, tenantA, planA, null, t0, "1.00", "10");
        WatcherFixture.append(snapshots, tenantA, planA, null, t0.plusSeconds(300), "0.69", "10");
        watcherJob.afterSnapshot(new PlanId(planA));

        String tokenB = WatcherItSupport.onboard(mvc, mapper, "13900003302", "乙店");
        JsonNode b = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", tokenB))
                .path("data");
        for (JsonNode row : b) {
            assertTrue(!planA.equals(row.path("planId").asText()));
        }
        JsonNode a = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", tokenA))
                .path("data");
        assertEquals(1, countOpen(a, "ROI_DROP"));
    }

    @Test
    void expiredTrialWithoutSubscriptionStillListsOpen() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003303", "过期店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant t0 = Instant.parse("2026-08-02T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0, "1.00", "10");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0.plusSeconds(300), "0.69", "10");
        watcherJob.afterSnapshot(new PlanId(planId));
        BillingItSupport.expireTrial(jdbc, tenantId);

        JsonNode actions = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", token));
        assertEquals(0, actions.path("code").asInt());
        assertEquals(1, countOpen(actions.path("data"), "ROI_DROP"));
        JsonNode inbox = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/tenant/notifications", token))
                .path("data");
        boolean found = false;
        for (JsonNode row : inbox) {
            if ("盯盘告警".equals(row.path("title").asText())) {
                found = true;
            }
        }
        assertTrue(found);
    }

    private static int countOpen(JsonNode actions, String type) {
        int n = 0;
        for (JsonNode row : actions) {
            if (type.equals(row.path("type").asText()) && row.path("open").asBoolean()) {
                n++;
            }
        }
        return n;
    }
}
