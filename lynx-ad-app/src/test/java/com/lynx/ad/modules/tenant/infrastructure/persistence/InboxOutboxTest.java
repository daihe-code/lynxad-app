package com.lynx.ad.modules.tenant.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.tenant.application.*;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** Independent delivery instances, real SQL transactions, and the actual inbox repository/API service. */
class InboxOutboxTest {
    static class Time extends Clock {
        Instant now=Instant.parse("2026-10-17T10:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}void advance(long s){now=now.plusSeconds(s);}
    }
    final TenantId tenant=new TenantId("tenant");Time time;JdbcTemplate jdbc;TransactionTemplate tx;
    InboxOutboxStore first,second;InboxDeliveryJob job;InboxCommandService inbox;
    void database(String mode)throws Exception {
        time=new Time();var ds=new DriverManagerDataSource("jdbc:h2:mem:outbox_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000","sa","");
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        jdbc.execute("CREATE TABLE notification(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(64),channel VARCHAR(16),title VARCHAR(128),body TEXT,biz_type VARCHAR(32),biz_id VARCHAR(64),read_at TIMESTAMP NULL,sent_at TIMESTAMP NOT NULL)");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V38__audit_notification_outbox.sql"));}
        first=new InboxOutboxStore(jdbc,manager,time);second=new InboxOutboxStore(new JdbcTemplate(ds),manager,time);job=new InboxDeliveryJob(first,mock(AuditRecorder.class));
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(NotificationMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        inbox=new InboxCommandService(new MpInboxMessageRepository(new SqlSessionTemplate(factory.getObject()).getMapper(NotificationMapper.class)),mock(AuditRecorder.class));inbox.deliveryJob(job);
    }
    void enqueue(String event,TenantId owner){tx.executeWithoutResult(s->first.enqueue(owner,event,"计划审核通过","该轮审核结果已确认，请查看当前状态。",new BizRef("AUDIT_PASS","plan"),time.instant().minusSeconds(3600)));}
    String eventId(){return jdbc.queryForObject("SELECT id FROM inbox_outbox WHERE tenant_id=?",String.class,tenant.value());}
    String state(){return jdbc.queryForObject("SELECT state FROM inbox_outbox WHERE tenant_id=?",String.class,tenant.value());}
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void sourceRollbackLeavesNoEventAndEnqueueRequiresSourceTransaction(String mode)throws Exception {
        database(mode);assertThrows(IllegalStateException.class,()->first.enqueue(tenant,"e","t","b",new BizRef("AUDIT_PASS","p"),time.instant()));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{first.enqueue(tenant,"e","t","b",new BizRef("AUDIT_PASS","p"),time.instant());throw new IllegalStateException();}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void nextInboxReadDeliversDueEventsWithStableIdAndTenantIsolation(String mode)throws Exception {
        database(mode);enqueue("one",tenant);enqueue("foreign",new TenantId("other"));
        var rows=inbox.list(tenant);assertEquals(1,rows.size());assertEquals("plan",rows.getFirst().bizId());assertTrue(rows.getFirst().unread());
        assertEquals(time.instant(),rows.getFirst().sentAt());assertEquals(1,inbox.list(tenant).size());assertEquals(rows.getFirst().id(),inbox.list(tenant).getFirst().id());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE tenant_id='other'",Integer.class));
        assertTrue(second.claim(jdbc.queryForObject("SELECT id FROM inbox_outbox WHERE tenant_id='other'",String.class),tenant).isEmpty());
        assertEquals(1,inbox.list(new TenantId("other")).size());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void failedDeliveryRetriesAfterRestartAndDoesNotChangeAnyBusinessData(String mode)throws Exception {
        database(mode);enqueue("one",tenant);
        jdbc.execute("ALTER TABLE notification ADD CONSTRAINT simulated_inbox_failure CHECK(title<>'计划审核通过')");
        job.deliverDue();assertEquals("RETRY",state());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT attempts FROM inbox_outbox",Integer.class));assertTrue(second.due(tenant,10).isEmpty());
        jdbc.execute("ALTER TABLE notification DROP CONSTRAINT simulated_inbox_failure");time.advance(29);new InboxDeliveryJob(second,mock(AuditRecorder.class)).deliverDue();assertEquals("RETRY",state());
        time.advance(1);new InboxDeliveryJob(second,mock(AuditRecorder.class)).deliverDue();assertEquals("SENT",state());assertEquals(1,inbox.list(tenant).size());
        assertEquals(2,jdbc.queryForObject("SELECT attempts FROM inbox_outbox",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void crashLeaseAndLateWorkerCannotDuplicateOrOverwriteReadState(String mode)throws Exception {
        database(mode);enqueue("one",tenant);String id=eventId();var old=first.claim(id,tenant).orElseThrow();
        assertTrue(second.claim(id,tenant).isEmpty());time.advance(121);var current=second.claim(id,tenant).orElseThrow();
        assertTrue(first.deliver(old).isEmpty());first.retry(old);assertEquals("SENDING",state());second.deliver(current);
        var message=inbox.list(tenant).getFirst();inbox.markRead(tenant,new NotificationId(message.id()),new UserId("reader"));
        var before=jdbc.queryForMap("SELECT read_at,sent_at,title FROM notification");
        // Even a repair/replay of a previously delivered event never rewrites the existing inbox row.
        jdbc.update("UPDATE inbox_outbox SET state='RETRY',next_attempt_at=?,lease_key=NULL,lease_until=NULL",Timestamp.from(time.instant()));
        job.deliverDue();assertEquals(before,jdbc.queryForMap("SELECT read_at,sent_at,title FROM notification"));assertFalse(inbox.list(tenant).getFirst().unread());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void twoInstancesDeliverExactlyOneMessage(String mode)throws Exception {
        database(mode);enqueue("one",tenant);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();job.deliverDue();return true;});var b=pool.submit(()->{gate.await();new InboxDeliveryJob(second,mock(AuditRecorder.class)).deliverDue();return true;});gate.countDown();
            assertTrue(a.get(5,TimeUnit.SECONDS));assertTrue(b.get(5,TimeUnit.SECONDS));assertEquals(1,inbox.list(tenant).size());assertEquals("SENT",state());
        }finally{pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void insertionAndDeliveryAcknowledgementRollbackTogether(String mode)throws Exception {
        database(mode);enqueue("one",tenant);jdbc.execute("ALTER TABLE inbox_outbox ADD CONSTRAINT simulated_ack_failure CHECK(state<>'SENT')");
        var claim=first.claim(eventId(),tenant).orElseThrow();assertThrows(RuntimeException.class,()->first.deliver(claim));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));assertEquals("SENDING",state());
        first.retry(claim);jdbc.execute("ALTER TABLE inbox_outbox DROP CONSTRAINT simulated_ack_failure");time.advance(30);job.deliverDue();
        assertEquals(1,inbox.list(tenant).size());assertEquals("SENT",state());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void boundedRecoveryAndBackoffDoNotHammerFailedEvents(String mode)throws Exception {
        database(mode);for(int i=0;i<12;i++)enqueue("event-"+i,tenant);
        assertEquals(10,inbox.list(tenant).size());assertEquals(12,inbox.list(tenant).size());
        assertEquals(30,InboxOutboxStore.retryDelaySeconds(1));assertEquals(120,InboxOutboxStore.retryDelaySeconds(2));assertEquals(300,InboxOutboxStore.retryDelaySeconds(3));assertEquals(900,InboxOutboxStore.retryDelaySeconds(4));assertEquals(3600,InboxOutboxStore.retryDelaySeconds(100));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void recoveryFailurePreservesExistingInboxAndPostDeliveryAuditFailureCannotReplayTheMessage(String mode)throws Exception {
        database(mode);enqueue("one",tenant);var audit=mock(AuditRecorder.class);doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any());
        new InboxDeliveryJob(first,audit).deliverDue();assertEquals("SENT",state());assertEquals(1,inbox.list(tenant).size());
        var unavailable=mock(InboxOutboxStore.class);when(unavailable.due(tenant,10)).thenThrow(new IllegalStateException("queue unavailable"));
        inbox.deliveryJob(new InboxDeliveryJob(unavailable,mock(AuditRecorder.class)));assertEquals(1,inbox.list(tenant).size());
        verify(unavailable,never()).claim(any(),any());assertEquals("SENT",state());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void inboxReadInsideSourceTransactionCannotDeliverAnUncommittedEvent(String mode)throws Exception {
        database(mode);tx.executeWithoutResult(s->{first.enqueue(tenant,"uncommitted","t","b",new BizRef("AUDIT_PASS","p"),time.instant());assertTrue(inbox.list(tenant).isEmpty());});
        assertEquals(1,inbox.list(tenant).size());assertEquals("SENT",state());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void successfulLoginMayAdvanceBackoffButRepeatedLoginsRespectSharedCooldown(String mode)throws Exception {
        database(mode);enqueue("one",tenant);jdbc.execute("ALTER TABLE notification ADD CONSTRAINT reject_on_login CHECK(title<>'计划审核通过')");
        job.deliverDue();assertEquals(1,jdbc.queryForObject("SELECT attempts FROM inbox_outbox",Integer.class));
        time.advance(30);job.deliverDue();assertEquals(2,jdbc.queryForObject("SELECT attempts FROM inbox_outbox",Integer.class));
        assertEquals(Timestamp.from(time.instant().plusSeconds(120)),jdbc.queryForObject("SELECT next_attempt_at FROM inbox_outbox",Timestamp.class));
        job.recoverAfterLogin(tenant);assertEquals(2,jdbc.queryForObject("SELECT attempts FROM inbox_outbox",Integer.class));
        jdbc.execute("ALTER TABLE notification DROP CONSTRAINT reject_on_login");time.advance(30);
        assertTrue(inbox.list(tenant).isEmpty()); // Ordinary polling does not bypass the two-minute backoff.
        new InboxDeliveryJob(second,mock(AuditRecorder.class)).recoverAfterLogin(tenant);
        assertEquals(1,inbox.list(tenant).size());assertEquals("SENT",state());assertEquals(3,jdbc.queryForObject("SELECT attempts FROM inbox_outbox",Integer.class));
    }
}
