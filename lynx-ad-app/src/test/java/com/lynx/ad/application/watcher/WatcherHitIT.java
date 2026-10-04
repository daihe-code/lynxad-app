package com.lynx.ad.application.watcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.agents.watcher.WatcherJob;
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
class WatcherHitIT extends AbstractContainerIT {

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
    void roiDropCreatesOpenAndInbox() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003101", "ROI店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant t0 = Instant.parse("2026-06-01T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0, "1.00", "10");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0.plusSeconds(300), "0.69", "10");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode actions = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", token))
                .path("data");
        assertEquals(1, countOpen(actions, "ROI_DROP"));
        assertEquals(1, inboxWatchCount(token));
    }

    @Test
    void budgetExhaustCreatesOpenAndInbox() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003102", "预算店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant ts = Instant.parse("2026-06-02T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, ts, "2.00", "271");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode actions = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", token))
                .path("data");
        assertEquals(1, countOpen(actions, "BUDGET_EXHAUST"));
        assertEquals(1, inboxWatchCount(token));
    }

    @Test
    void abnormalPauseCreatesOpenAndInbox() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003103", "暂停店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant ts = Instant.parse("2026-06-03T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, ts, "2.00", "10");
        WatcherFixture.setQcStatus(jdbc, planId, "ABNORMAL_PAUSE");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode actions = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", token))
                .path("data");
        assertEquals(1, countOpen(actions, "QC_ABNORMAL_PAUSE"));
        assertEquals(1, inboxWatchCount(token));
    }

    @Test
    void noSnapshotProducesNothing() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003104", "空快照店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        WatcherFixture.setQcStatus(jdbc, planId, "ABNORMAL_PAUSE");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode actions = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", token))
                .path("data");
        assertEquals(0, actions.size());
        assertEquals(0, inboxWatchCount(token));
    }

    private int countOpen(JsonNode actions, String type) {
        int n = 0;
        for (JsonNode row : actions) {
            if (type.equals(row.path("type").asText()) && row.path("open").asBoolean()) {
                n++;
            }
        }
        return n;
    }

    private int inboxWatchCount(String token) throws Exception {
        JsonNode inbox = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/tenant/notifications", token))
                .path("data");
        int n = 0;
        for (JsonNode row : inbox) {
            if ("盯盘告警".equals(row.path("title").asText())) {
                n++;
            }
        }
        assertTrue(n >= 0);
        return n;
    }
}
