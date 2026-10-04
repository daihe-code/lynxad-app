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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatcherConfirmIT extends AbstractContainerIT {

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
    void confirmStays501AndDoesNotRecover() throws Exception {
        String token = WatcherItSupport.onboard(mvc, mapper, "13900003401", "确认店");
        String planId = WatcherItSupport.publishDelivering(mvc, mapper, auditPollJob, token);
        String tenantId = WatcherItSupport.tenantId(mvc, mapper, token);
        Instant t0 = Instant.parse("2026-08-03T00:00:00Z");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0, "1.00", "10");
        WatcherFixture.append(snapshots, tenantId, planId, null, t0.plusSeconds(300), "0.69", "10");
        watcherJob.afterSnapshot(new PlanId(planId));

        JsonNode list = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", token))
                .path("data");
        String id = list.get(0).path("id").asText();
        JsonNode confirm = WatcherItSupport.json(
                mapper, WatcherItSupport.post(mvc, "/api/v1/agentops/actions/" + id + "/confirm", "{}", token));
        assertEquals(501, confirm.path("code").asInt());
        assertEquals("确认即将上线", confirm.path("message").asText());

        JsonNode after = WatcherItSupport.json(mapper, WatcherItSupport.get(mvc, "/api/v1/agentops/actions", token))
                .path("data");
        assertTrue(after.get(0).path("open").asBoolean());
        assertTrue(after.get(0).path("recoveredAt").isNull() || after.get(0).path("recoveredAt").isMissingNode());
    }
}
