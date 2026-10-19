package com.lynx.ad.application.dataops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.modules.dataops.application.MetricSnapshotJob;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.LiveFixtureServer;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = LynxAdApplication.class)
@ActiveProfiles("test")
class DemoLiveReportIT extends AbstractContainerIT {

    static final LiveFixtureServer FIXTURE;

    static {
        try {
            FIXTURE = new LiveFixtureServer();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void live(DynamicPropertyRegistry registry) {
        FIXTURE.register(registry);
    }

    @AfterAll
    static void stop() {
        FIXTURE.stop();
    }

    @Autowired
    private MetricSnapshotJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Encryptor encryptor;

    @Test
    void demoPlansDoNotCallReport() {
        jdbc.update("update ad_plan set status = 'FINISHED' where status = 'DELIVERING'");
        String tenantId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();
        jdbc.update("insert into sys_tenant (id, name, status) values (?, '演示店', 'ACTIVE')", tenantId);
        jdbc.update(
                "insert into oauth_account (id, tenant_id, platform, advertiser_id, account_name, access_token_enc, refresh_token_enc, status) values (?, ?, 'QIANCHUAN', 'adv_demo', '演示千川账户', ?, ?, 'ACTIVE')",
                accountId,
                tenantId,
                encryptor.encrypt("mock-access"),
                encryptor.encrypt("mock-refresh"));
        jdbc.update(
                "insert into ad_plan (id, tenant_id, oauth_account_id, aweme_id, name, status, qc_plan_id) values (?, ?, ?, 'aweme_demo', '烘焙放量计划', 'DELIVERING', 'mock_seed1')",
                UUID.randomUUID().toString(),
                tenantId,
                accountId);
        jdbc.update(
                "insert into ad_plan (id, tenant_id, oauth_account_id, aweme_id, name, status, qc_plan_id) values (?, ?, ?, 'aweme_demo', '外部对照计划', 'DELIVERING', null)",
                UUID.randomUUID().toString(),
                tenantId,
                accountId);
        FIXTURE.reportHits.set(0);
        job.snapshot();
        assertEquals(0, FIXTURE.reportHits.get());
    }
}
