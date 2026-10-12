package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanRemoteStore;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Real persistence and migration checks only in an explicitly named isolated test database. */
@EnabledIfEnvironmentVariable(named="LYNXAD_TRANSFER_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/lynxad_transfer_verify_[a-zA-Z0-9_]+.*")
class PlanRemoteStoreIT {
    static JdbcTemplate jdbc;static PlanRemoteStore store;static ObjectMapper json=new ObjectMapper();
    final TenantId tenant=new TenantId("tenant-a");final AccountId account=new AccountId("account");
    @BeforeAll static void setup() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("LYNXAD_TRANSFER_TEST_URL"),"root",System.getenv("LYNXAD_TRANSFER_TEST_PASSWORD"));jdbc=new JdbcTemplate(ds);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='qc_plan_metadata'",Integer.class)==0)
            try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V9__qianchuan_management.sql"));}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='qc_plan_metadata' AND column_name='last_report'",Integer.class)==0) {
            jdbc.update("INSERT INTO qc_plan_metadata(plan_id,tenant_id,oauth_account_id,qc_plan_id,payload,synced_at,yesterday_report,report_date,report_ts) VALUES('migration-seed','test','a','1','{}',CURRENT_TIMESTAMP,?,'2026-10-11',CURRENT_TIMESTAMP)","{\"stat_cost\":{\"Value\":3.56}}");
            try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V23__preserve_plan_statistics.sql"));}
            assertEquals("2026-10-10",jdbc.queryForObject("SELECT CAST(last_report_date AS CHAR) FROM qc_plan_metadata WHERE plan_id='migration-seed'",String.class));
            assertEquals("{\"stat_cost\":{\"Value\":3.56}}",jdbc.queryForObject("SELECT last_report FROM qc_plan_metadata WHERE plan_id='migration-seed'",String.class));
        }
        store=new PlanRemoteStore(jdbc,json);
    }
    @BeforeEach void reset(){jdbc.update("DELETE FROM qc_plan_metadata");store.save(tenant,account,"111","plan-a",json.createObjectNode());}
    JsonNode metrics(String cost) throws Exception {return json.readTree("{\"stat_cost\":{\"Value\":"+cost+"}}");}
    @Test void emptyDailyReportsAndPlanSyncDoNotEraseTheLastStatistics() throws Exception {
        var day=LocalDate.of(2026,10,10);var ts=Instant.parse("2026-10-10T15:00:00Z");
        store.report(tenant,"plan-a",metrics("3.56"),null,day,ts);
        store.report(tenant,"plan-a",null,null,day.plusDays(3),ts.plusSeconds(3*86400));
        store.save(tenant,account,"111","plan-a",json.createObjectNode());
        var row=store.list(tenant).get("plan-a");assertNull(row.todayReport());assertNull(row.yesterdayReport());
        assertEquals(metrics("3.56"),row.lastReport());assertEquals(day,row.lastReportDate());assertEquals(ts,row.lastReportTs());
    }
    @Test void yesterdaySnapshotIsRetainedWhenTodayHasNoRow() throws Exception {
        var day=LocalDate.of(2026,10,11);store.report(tenant,"plan-a",null,metrics("3.56"),day,Instant.now());
        assertEquals(day.minusDays(1),store.list(tenant).get("plan-a").lastReportDate());
        assertEquals(metrics("3.56"),store.list(tenant).get("plan-a").lastReport());
    }
    @Test void olderResponsesCannotReplaceNewerRetainedReports() throws Exception {
        var day=LocalDate.of(2026,10,11);var ts=Instant.now();store.report(tenant,"plan-a",metrics("5"),null,day,ts);
        store.report(tenant,"plan-a",metrics("3"),null,day.minusDays(1),ts.plusSeconds(1));
        assertEquals(metrics("5"),store.list(tenant).get("plan-a").lastReport());
        store.report(tenant,"plan-a",metrics("2"),null,day,ts.minusSeconds(1));
        assertEquals(metrics("5"),store.list(tenant).get("plan-a").lastReport());
    }
    @Test void zeroIsValidAndUnknownMetricsCannotEraseIt() throws Exception {
        var day=LocalDate.of(2026,10,11);var ts=Instant.now();store.report(tenant,"plan-a",metrics("0"),null,day,ts);
        store.report(tenant,"plan-a",json.createObjectNode(),null,day.plusDays(1),ts.plusSeconds(86400));
        assertEquals(metrics("0"),store.list(tenant).get("plan-a").lastReport());assertEquals(day,store.list(tenant).get("plan-a").lastReportDate());
    }
    @Test void reportUpdatesAndReadsAreTenantScoped() throws Exception {
        store.report(new TenantId("tenant-b"),"plan-a",metrics("9"),null,LocalDate.now(),Instant.now());
        assertNull(store.list(tenant).get("plan-a").lastReport());assertTrue(store.list(new TenantId("tenant-b")).isEmpty());
    }
}
