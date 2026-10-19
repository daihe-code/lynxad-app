package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanInsightsStore;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named="LYNXAD_INSIGHTS_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:(?:3306|55406)/lynxad_plan_insights_verify_[a-zA-Z0-9_]+.*")
class PlanInsightsStoreIT {
    static JdbcTemplate jdbc;static PlanInsightsStore store;static TransactionTemplate transaction;
    final TenantId tenant=new TenantId("tenant-a");final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));final PlanStatisticsRange range=new PlanStatisticsRange(today,today);
    @BeforeAll static void setup() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("LYNXAD_INSIGHTS_TEST_URL"),"root",System.getenv("LYNXAD_INSIGHTS_TEST_PASSWORD"));jdbc=new JdbcTemplate(ds);transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));store=new PlanInsightsStore(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS ad_plan(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS audit_record(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),object_type VARCHAR(32),object_id VARCHAR(64),created_at TIMESTAMP(6),actor_name VARCHAR(64),action_type VARCHAR(32),result VARCHAR(32),object_label VARCHAR(256))");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='delivery_guarantee_history'",Integer.class)==0)
            try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V25__plan_guarantee_history.sql"));}
    }
    @BeforeEach void clear() { jdbc.update("DELETE FROM delivery_guarantee_history");jdbc.update("DELETE FROM audit_record");jdbc.update("DELETE FROM ad_plan");jdbc.update("INSERT INTO ad_plan(id,tenant_id) VALUES('plan-a','tenant-a'),('plan-b','tenant-a'),('plan-other','tenant-b')"); }
    void observe(String status) { transaction.executeWithoutResult(s->store.observeGuarantee(tenant,"plan-a",status,null,null)); }
    @Test void repeatedStatusRecordsOnceAndChangingStatusRecordsAgain() { observe("IN_EFFECT");observe("IN_EFFECT");observe("INVALID");observe("INVALID");observe("IN_EFFECT");var history=store.guarantees(tenant,"plan-a",range,1);assertEquals(3,history.total());assertEquals("IN_EFFECT",history.records().getFirst().status()); }
    @Test void concurrentSameStatusCannotDuplicateHistory() throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(6);try { var start=new CountDownLatch(1);List<Future<?>> tasks=new ArrayList<>();for(int i=0;i<6;i++)tasks.add(executor.submit(()->{try{start.await();observe("IN_EFFECT");}catch(Exception ex){throw new RuntimeException(ex);}}));start.countDown();for(var task:tasks)task.get(10,TimeUnit.SECONDS); } finally { executor.shutdownNow(); }
        assertEquals(1,store.guarantees(tenant,"plan-a",range,1).total());
    }
    @Test void missingOrUnownedPlanCannotCreateHistory() { observe(null);transaction.executeWithoutResult(s->store.observeGuarantee(tenant,"plan-other","IN_EFFECT",null,null));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM delivery_guarantee_history",Integer.class)); }
    @Test void transactionFailureRollsBackObservation() { assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(s->{store.observeGuarantee(tenant,"plan-a","IN_EFFECT",null,null);throw new IllegalStateException("rollback");}));assertEquals(0,store.guarantees(tenant,"plan-a",range,1).total()); }
    void audit(String tenant,String object,String type,Instant time,String label) { jdbc.update("INSERT INTO audit_record(id,tenant_id,object_type,object_id,created_at,actor_name,action_type,result,object_label) VALUES(?,?,?,?,?,'店主','PLAN','SUCCESS',?)",UUID.randomUUID().toString(),tenant,type,object,java.sql.Timestamp.from(time),label); }
    @Test void logsFilterExactPhysicalMemberTenantAndDayWithExclusiveEnd() {
        Instant begin=today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),end=today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        audit("tenant-a","plan-a","PLAN",begin,"相同计划名称");audit("tenant-a","plan-b","PLAN",begin,"相同计划名称");audit("tenant-b","plan-a","PLAN",begin,"相同计划名称");audit("tenant-a","plan-a","CREATIVE",begin,"相同计划名称");audit("tenant-a","plan-a","PLAN",end,"下一天");audit("tenant-a","plan-a","PLAN",begin.minusMillis(1),"前一天");
        var logs=store.logs(tenant,"plan-a",range,1);assertEquals(1,logs.total());assertEquals("相同计划名称",logs.records().getFirst().detail());
    }
    @Test void localLogsHaveStablePagingAndNeverCombineCounterpart() { for(int i=0;i<25;i++)audit("tenant-a","plan-a","PLAN",Instant.now(),"调整预算");assertEquals(25,store.logs(tenant,"plan-a",range,1).total());assertEquals(20,store.logs(tenant,"plan-a",range,1).records().size());assertEquals(5,store.logs(tenant,"plan-a",range,2).records().size());assertEquals(0,store.logs(tenant,"plan-b",range,1).total()); }
}
