package com.lynx.ad.modules.delivery.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.tenant.infrastructure.persistence.InboxOutboxStore;
import com.lynx.ad.modules.tenant.application.InboxDeliveryJob;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Real MyBatis mapper and transactional repository lifecycle, with no platform or production database. */
class PlanReviewRepositoryTest {
    static class Time extends Clock {
        Instant now=Instant.parse("2026-10-17T10:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
    final TenantId tenant=new TenantId("tenant");AdPlanRepository plans;PlanReviewStore store;Time time;JdbcTemplate jdbc;InboxOutboxStore outbox;
    void setup(String mode)throws Exception {
        time=new Time();var ds=new DriverManagerDataSource("jdbc:h2:mem:review_repo_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);
        jdbc.execute("""
          CREATE TABLE ad_plan(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),oauth_account_id VARCHAR(64),aweme_id VARCHAR(64),name VARCHAR(255),
            status VARCHAR(32),qc_status VARCHAR(64),qc_plan_id VARCHAR(64),smart_bid_type VARCHAR(64),budget DECIMAL(18,2),roi2_goal DECIMAL(18,4),
            start_date DATE,end_date DATE,publish_version INT,idempotency_key VARCHAR(64),reject_reason VARCHAR(255),source VARCHAR(32),created_at TIMESTAMP NULL,delivery_platform VARCHAR(32))
          """);
        try(var c=ds.getConnection()){
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V37__plan_review_progress.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V38__audit_notification_outbox.sql"));
        }
        jdbc.execute("CREATE TABLE notification(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(64),channel VARCHAR(16),title VARCHAR(128),body TEXT,biz_type VARCHAR(32),biz_id VARCHAR(64),read_at TIMESTAMP NULL,sent_at TIMESTAMP NOT NULL)");
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(AdPlanMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        var manager=new DataSourceTransactionManager(ds);store=new PlanReviewStore(jdbc,manager,time);
        var target=new MpAdPlanRepository(new SqlSessionTemplate(factory.getObject()).getMapper(AdPlanMapper.class),mock(AdPlanCreativeBindingStore.class));target.reviewStore(store);
        outbox=new InboxOutboxStore(jdbc,manager,time);target.auditNoticeStore(new PlanAuditNoticeStore(jdbc,outbox,time));
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));plans=(AdPlanRepository)proxy.getProxy();
    }
    AdPlan plan(PlanStatus status){return new AdPlan(new PlanId("plan"),tenant,new AccountId("account"),new AwemeId("aweme"),new PlanName("审核周期测试"),status,PlanSource.LINGTOU,
        new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(),status==PlanStatus.DRAFT?null:new QcPlanId("456"),null,null,1,null,List.of());}
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void oldDraftCreationIsNotUsedAsSubmissionTimeAndUnrelatedSavesPreserveCycle(String mode)throws Exception {
        setup(mode);var draft=plan(PlanStatus.DRAFT);plans.save(draft);assertTrue(store.list(tenant).isEmpty());
        time.now=time.now.plusSeconds(86400);var pending=plan(PlanStatus.PENDING_AUDIT);plans.save(pending);assertEquals(time.instant(),store.list(tenant).get("plan").pendingSince());
        var claim=store.claim(pending,false).orElseThrow();time.now=time.now.plusSeconds(60);pending.rename(new PlanName("重新命名"));plans.save(pending);
        assertEquals(claim.since(),store.list(tenant).get("plan").pendingSince());assertTrue(store.finish(claim,"PENDING",()->{}));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void completedAndDeletedReviewsClearStateAndOldResponsesCannotAffectNewCycle(String mode)throws Exception {
        setup(mode);var p=plan(PlanStatus.PENDING_AUDIT);plans.save(p);var old=store.claim(p,false).orElseThrow();
        p.apply(PlanStatus.REJECTED);plans.save(p);assertTrue(store.list(tenant).isEmpty());time.now=time.now.plusSeconds(3600);
        p.apply(PlanStatus.PENDING_AUDIT);plans.save(p);assertEquals(time.instant(),store.list(tenant).get("plan").pendingSince());assertFalse(store.finish(old,"RESOLVED",()->fail("stale cycle response applied")));
        var stale=plans.findById(tenant,p.id()).orElseThrow();p.markDeleted();plans.save(p);stale.apply(PlanStatus.PENDING_AUDIT);plans.save(stale);
        assertTrue(store.list(tenant).isEmpty());assertEquals(PlanStatus.DELETED,plans.findById(tenant,p.id()).orElseThrow().status());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void auditOutcomeAndDurableNoticeCommitTogetherAndInboxFailureCannotUndoTheOutcome(String mode)throws Exception {
        setup(mode);var p=plan(PlanStatus.PENDING_AUDIT);plans.save(p);p.rename(new PlanName("折叠自行车审核"));p.apply(PlanStatus.DELIVERING);plans.save(p);
        assertEquals(PlanStatus.DELIVERING,plans.findById(tenant,p.id()).orElseThrow().status());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));assertTrue(jdbc.queryForObject("SELECT body FROM inbox_outbox",String.class).contains("折叠自行车审核"));
        jdbc.execute("ALTER TABLE notification ADD CONSTRAINT reject_notice CHECK(title<>'计划审核通过')");new InboxDeliveryJob(outbox,mock(AuditRecorder.class)).deliverDue();
        assertEquals(PlanStatus.DELIVERING,plans.findById(tenant,p.id()).orElseThrow().status());assertEquals("RETRY",jdbc.queryForObject("SELECT state FROM inbox_outbox",String.class));
        jdbc.execute("ALTER TABLE notification DROP CONSTRAINT reject_notice");time.now=time.now.plusSeconds(30);new InboxDeliveryJob(outbox,mock(AuditRecorder.class)).deliverDue();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void eventStorageFailureRollsBackTheLocalOutcomeForSafeReadbackRetry(String mode)throws Exception {
        setup(mode);var p=plan(PlanStatus.PENDING_AUDIT);plans.save(p);p.apply(PlanStatus.DELIVERING);
        jdbc.execute("ALTER TABLE inbox_outbox ADD CONSTRAINT reject_event CHECK(state<>'READY')");assertThrows(RuntimeException.class,()->plans.save(p));
        assertEquals(PlanStatus.PENDING_AUDIT,plans.findById(tenant,p.id()).orElseThrow().status());assertEquals("WAITING",jdbc.queryForObject("SELECT state FROM delivery_plan_audit_notice",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        jdbc.execute("ALTER TABLE inbox_outbox DROP CONSTRAINT reject_event");plans.save(p);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void sameCycleIsDeduplicatedAndARealNewReviewCanNotifyAgain(String mode)throws Exception {
        setup(mode);var p=plan(PlanStatus.PENDING_AUDIT);plans.save(p);p.apply(PlanStatus.DELIVERING);plans.save(p);plans.save(p);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        p.apply(PlanStatus.PENDING_AUDIT);plans.save(p);p.apply(PlanStatus.REJECTED);plans.save(p);plans.save(p);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox WHERE biz_type='AUDIT_PASS'",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox WHERE biz_type='AUDIT_REJECT'",Integer.class));
        p.apply(PlanStatus.PENDING_AUDIT);plans.save(p);p.apply(PlanStatus.DELIVERING);plans.save(p);
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox WHERE biz_type='AUDIT_PASS'",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void snapshotsCannotInventHistoricalResultsOrTreatPauseAsAnApproval(String mode)throws Exception {
        setup(mode);var p=plan(PlanStatus.PAUSED);plans.save(p);p.apply(PlanStatus.DELIVERING);plans.save(p);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        p.apply(PlanStatus.PENDING_AUDIT);plans.save(p);p.apply(PlanStatus.PAUSED);plans.save(p);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        p.apply(PlanStatus.DELIVERING);plans.save(p);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void firstResultForExistingPendingPlanIsCapturedAfterMigration(String mode)throws Exception {
        setup(mode);var p=plan(PlanStatus.PENDING_AUDIT);plans.save(p);jdbc.update("DELETE FROM delivery_plan_audit_notice");
        p.apply(PlanStatus.REJECTED);plans.save(p);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void concurrentOutcomeSavesCreateOnlyOneCycleEvent(String mode)throws Exception {
        setup(mode);var p=plan(PlanStatus.PENDING_AUDIT);plans.save(p);
        var a=plans.findById(tenant,p.id()).orElseThrow();var b=plans.findById(tenant,p.id()).orElseThrow();a.apply(PlanStatus.DELIVERING);b.apply(PlanStatus.DELIVERING);
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var first=pool.submit(()->{gate.await();plans.save(a);return true;});var second=pool.submit(()->{gate.await();plans.save(b);return true;});gate.countDown();
            assertTrue(first.get(5,TimeUnit.SECONDS));assertTrue(second.get(5,TimeUnit.SECONDS));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        }finally{pool.shutdownNow();}
    }
}
