package com.lynx.ad.application.watcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatcherDedupIT extends AbstractContainerIT {

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

    @Test
    void secondHitDoesNotSpamAndKeepsSnapshotTs() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003201", "去重店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant t0 = Instant.parse("2026-07-01T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0, "1.00", "10");
        Instant firstHitTs = t0.plusSeconds(300);
        WatcherFixture.append(snapshots, tenantId, planId, null, firstHitTs, "0.69", "10");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode first = openRow(token, "ROI_DROP");
        String firstTs = first.path("snapshotTs").asText();
        WatcherFixture.append(snapshots, tenantId, planId, null, firstHitTs.plusSeconds(300), "1.00", "10");
        WatcherFixture.append(snapshots, tenantId, planId, null, firstHitTs.plusSeconds(600), "0.50", "10");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode actions = data(token, "/api/v1/agentops/actions");
        assertEquals(1, countOpen(actions, "ROI_DROP"));
        assertEquals(firstTs, openRow(token, "ROI_DROP").path("snapshotTs").asText());
        assertEquals(1, inboxWatchCount(token));
    }

    @Test
    void recoverThenRehitOpensNewRow() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003202", "恢复店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant t0 = Instant.parse("2026-07-02T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0, "1.00", "10");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0.plusSeconds(300), "0.69", "10");
        watcherJob.afterSnapshot(new PlanId(planId));
        assertEquals(1, inboxWatchCount(token));

        WatcherFixture.append(snapshots, tenantId, planId, null, t0.plusSeconds(600), "0.95", "10");
        watcherJob.afterSnapshot(new PlanId(planId));
        JsonNode recovered = rowOf(data(token, "/api/v1/agentops/actions"), "ROI_DROP", false);
        assertFalse(recovered.path("open").asBoolean());
        assertTrue(recovered.hasNonNull("recoveredAt"));
        assertEquals(1, inboxWatchCount(token));

        WatcherFixture.append(snapshots, tenantId, planId, null, t0.plusSeconds(900), "0.50", "10");
        watcherJob.afterSnapshot(new PlanId(planId));
        assertEquals(1, countOpen(data(token, "/api/v1/agentops/actions"), "ROI_DROP"));
        assertEquals(2, inboxWatchCount(token));
    }

    @Test
    void twoRulesCanBeOpenTogether() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003203", "双规则店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant t0 = Instant.parse("2026-07-03T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0, "1.00", "10");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0.plusSeconds(300), "0.69", "271");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode actions = data(token, "/api/v1/agentops/actions");
        assertEquals(1, countOpen(actions, "ROI_DROP"));
        assertEquals(1, countOpen(actions, "BUDGET_EXHAUST"));
        assertEquals(2, inboxWatchCount(token));
    }

    @Test
    void persistRereadWithoutMemoryMap() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003204", "落库店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant t0 = Instant.parse("2026-07-04T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0, "1.00", "10");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0.plusSeconds(300), "0.69", "10");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode again = data(token, "/api/v1/agentops/actions");
        assertEquals(1, countOpen(again, "ROI_DROP"));
    }

    private JsonNode data(String token, String url) throws Exception {
        return WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, url, token)).path("data");
    }

    private JsonNode openRow(String token, String type) throws Exception {
        return rowOf(data(token, "/api/v1/agentops/actions"), type, true);
    }

    private static JsonNode rowOf(JsonNode actions, String type, boolean open) {
        for (JsonNode row : actions) {
            if (type.equals(row.path("type").asText()) && row.path("open").asBoolean() == open) {
                return row;
            }
        }
        throw new AssertionError("missing " + type + " open=" + open);
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

    private int inboxWatchCount(String token) throws Exception {
        JsonNode inbox = data(token, "/api/v1/tenant/notifications");
        int n = 0;
        for (JsonNode row : inbox) {
            if ("盯盘告警".equals(row.path("title").asText())) {
                n++;
            }
        }
        return n;
    }
}
