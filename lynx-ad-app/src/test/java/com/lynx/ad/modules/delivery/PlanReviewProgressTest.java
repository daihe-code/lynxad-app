package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.domain.service.PlanReviewProgress;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanReviewStore;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.dataops.application.AuditPollJob;
import com.lynx.ad.modules.dataops.domain.model.AuditVerdict;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;

class PlanReviewProgressTest {
    static class Time extends Clock {
        Instant now=Instant.parse("2026-10-17T10:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
        void advance(long seconds){now=now.plusSeconds(seconds);}
    }
    final TenantId tenant=new TenantId("tenant");final PlanId id=new PlanId("plan");
    Time time;JdbcTemplate jdbc;PlanReviewStore store,second;AdPlan plan;
    void setup(String mode)throws Exception {
        time=new Time();var ds=new DriverManagerDataSource("jdbc:h2:mem:review_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000","sa","");
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE ad_plan(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),status VARCHAR(24),qc_plan_id VARCHAR(64))");
        jdbc.update("INSERT INTO ad_plan VALUES('plan','tenant','PENDING_AUDIT','456')");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V37__plan_review_progress.sql"));}
        store=new PlanReviewStore(jdbc,manager,time);second=new PlanReviewStore(new JdbcTemplate(ds),manager,time);
        plan=mock(AdPlan.class);when(plan.tenantId()).thenReturn(tenant);when(plan.id()).thenReturn(id);when(plan.qcPlanId()).thenReturn(Optional.of(new QcPlanId("456")));
        when(plan.status()).thenReturn(PlanStatus.PENDING_AUDIT);when(plan.source()).thenReturn(PlanSource.LINGTOU);when(plan.name()).thenReturn(new PlanName("待审核计划"));when(plan.accountId()).thenReturn(new AccountId("account"));
    }
    PlanReviewProgress.View view(){return store.list(tenant).get(id.value());}
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void delayBoundaryAndReadFailuresNeverTurnIntoRejection(String mode)throws Exception {
        setup(mode);assertNull(view().pendingSince());assertEquals("WAITING",view().state());
        store.saved(plan,true);var since=view().pendingSince();time.advance(899);assertEquals("WAITING",view().state());
        time.advance(1);assertEquals("DELAYED",view().state());
        var claim=store.claim(plan,false).orElseThrow();assertTrue(store.finish(claim,"FAILED",()->{}));
        assertEquals("CHECK_UNAVAILABLE",view().state());assertEquals(since,view().pendingSince());assertEquals(time.instant().plusSeconds(180),view().nextCheckAt());
        assertEquals("PENDING_AUDIT",jdbc.queryForObject("SELECT status FROM ad_plan",String.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void twoInstancesClaimOneReadAndManualRefreshHasSharedCooldown(String mode)throws Exception {
        setup(mode);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();return store.claim(plan,true);});var b=pool.submit(()->{gate.await();return second.claim(plan,true);});gate.countDown();
            var first=a.get(4,TimeUnit.SECONDS);var other=b.get(4,TimeUnit.SECONDS);assertNotEquals(first.isPresent(),other.isPresent());
            store.finish(first.or(()->other).orElseThrow(),"PENDING",()->{});
            assertTrue(second.claim(plan,true).isEmpty());time.advance(30);assertTrue(second.claim(plan,true).isPresent());
        }finally{pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void repeatedObservationKeepsCycleAndLaterReviewStartsFresh(String mode)throws Exception {
        setup(mode);store.saved(plan,true);var initial=view().pendingSince();time.advance(1000);store.saved(plan,false);assertEquals(initial,view().pendingSince());
        var stale=store.claim(plan,false).orElseThrow();
        jdbc.update("UPDATE ad_plan SET status='DELIVERING'");when(plan.status()).thenReturn(PlanStatus.DELIVERING);store.saved(plan,false);assertNull(view());
        jdbc.update("UPDATE ad_plan SET status='PENDING_AUDIT'");when(plan.status()).thenReturn(PlanStatus.PENDING_AUDIT);store.saved(plan,true);
        assertEquals("WAITING",view().state());assertEquals(time.instant(),view().pendingSince());
        assertFalse(store.finish(stale,"RESOLVED",()->fail("previous review must not update the new cycle")));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void expiryAndTenantIsolationRejectStaleReaders(String mode)throws Exception {
        setup(mode);var old=store.claim(plan,false).orElseThrow();time.advance(181);var next=second.claim(plan,false).orElseThrow();
        assertFalse(store.finish(old,"RESOLVED",()->fail("stale read applied")));assertTrue(store.list(new TenantId("other")).isEmpty());
        assertTrue(second.finish(next,"NOT_FOUND",()->{}));assertEquals("CHECK_UNAVAILABLE",view().state());
        when(plan.tenantId()).thenReturn(new TenantId("other"));assertTrue(store.claim(plan,true).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void localFailureRollsBackStateAndLeaseCanBeRecovered(String mode)throws Exception {
        setup(mode);var claim=store.claim(plan,false).orElseThrow();
        assertThrows(IllegalStateException.class,()->store.finish(claim,"RESOLVED",()->{jdbc.update("UPDATE ad_plan SET status='DELIVERING'");throw new IllegalStateException();}));
        assertEquals("PENDING_AUDIT",jdbc.queryForObject("SELECT status FROM ad_plan",String.class));assertTrue(view().checking());
        assertTrue(store.finish(claim,"FAILED",()->{}));assertFalse(view().checking());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void delayedPollingWaitsThreeMinutesAndManualReadCanCheckSooner(String mode)throws Exception {
        setup(mode);store.saved(plan,true);time.advance(900);var claim=store.claim(plan,false).orElseThrow();store.finish(claim,"PENDING",()->{});
        time.advance(30);assertTrue(store.claim(plan,false).isEmpty());var manual=second.claim(plan,true).orElseThrow();store.finish(manual,"PENDING",()->{});
        assertEquals(time.instant().plusSeconds(180),view().nextCheckAt());time.advance(179);assertTrue(store.claim(plan,false).isEmpty());time.advance(1);assertTrue(store.claim(plan,false).isPresent());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void failedOrMissingPlatformReadsPreserveStatusAndNeverPublish(String mode)throws Exception {
        setup(mode);var repository=mock(AdPlanRepository.class);when(repository.findPendingAudit()).thenReturn(List.of(plan));
        var commands=mock(PlanCommandService.class);var inbox=mock(InboxCommandService.class);var port=mock(AdListPort.class);var props=new LynxAdProperties();props.getMock().setQianchuan(false);
        var job=new AuditPollJob(repository,commands,props,port,mock(QianchuanCredentialFacade.class));job.reviewStore(store);
        when(port.list(any(),isNull())).thenThrow(new IllegalStateException("network"));job.pollPendingAudits();assertEquals("FAILED",view().lastResult());
        time.advance(30);doReturn(List.of()).when(port).list(any(),isNull());job.refresh(plan);assertEquals("NOT_FOUND",view().lastResult());
        verifyNoInteractions(commands,inbox);assertEquals("PENDING_AUDIT",jdbc.queryForObject("SELECT status FROM ad_plan",String.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void finalResultIsAppliedOnceAcrossInstances(String mode)throws Exception {
        setup(mode);var repository=mock(AdPlanRepository.class);when(repository.findPendingAudit()).thenReturn(List.of(plan));
        var commands=mock(PlanCommandService.class);doAnswer(i->{jdbc.update("UPDATE ad_plan SET status='DELIVERING'");return null;}).when(commands).markAudit(tenant,id,AuditVerdict.PASS,null);
        var inbox=mock(InboxCommandService.class);var port=mock(AdListPort.class);var props=new LynxAdProperties();props.getMock().setQianchuan(false);
        when(port.list(any(),isNull())).thenReturn(List.of(new OceanPlanRow("456","待审核计划","DELIVERY_OK",null,null,null,null)));
        var first=new AuditPollJob(repository,commands,props,port,mock(QianchuanCredentialFacade.class));first.reviewStore(store);
        var other=new AuditPollJob(repository,commands,props,port,mock(QianchuanCredentialFacade.class));other.reviewStore(second);
        first.pollPendingAudits();other.pollPendingAudits();verify(commands,times(1)).markAudit(tenant,id,AuditVerdict.PASS,null);verify(port,times(1)).list(any(),isNull());assertNull(view());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void batchedAccountResponseCannotCompleteAReviewRestartedWhileReading(String mode)throws Exception {
        setup(mode);jdbc.update("INSERT INTO ad_plan(id,tenant_id,status,qc_plan_id) VALUES('second','tenant','PENDING_AUDIT','789')");
        var other=mock(AdPlan.class);when(other.tenantId()).thenReturn(tenant);when(other.id()).thenReturn(new PlanId("second"));when(other.qcPlanId()).thenReturn(Optional.of(new QcPlanId("789")));
        when(other.status()).thenReturn(PlanStatus.PENDING_AUDIT);when(other.accountId()).thenReturn(new AccountId("account"));
        var repository=mock(AdPlanRepository.class);when(repository.findPendingAudit()).thenReturn(List.of(plan,other));
        var commands=mock(PlanCommandService.class);var port=mock(AdListPort.class);var props=new LynxAdProperties();props.getMock().setQianchuan(false);
        when(port.list(any(),isNull())).thenAnswer(i->{store.saved(other,true);return List.of(new OceanPlanRow("456","first","AUDIT",null,null,null,null),new OceanPlanRow("789","second","DELIVERY_OK",null,null,null,null));});
        var job=new AuditPollJob(repository,commands,props,port,mock(QianchuanCredentialFacade.class));job.reviewStore(store);job.pollPendingAudits();
        verifyNoInteractions(commands);assertEquals("PENDING_AUDIT",jdbc.queryForObject("SELECT status FROM ad_plan WHERE id='second'",String.class));
        verify(port,times(1)).list(any(),isNull());
    }
}
