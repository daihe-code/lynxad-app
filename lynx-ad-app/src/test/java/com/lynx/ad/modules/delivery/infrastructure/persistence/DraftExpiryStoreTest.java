package com.lynx.ad.modules.delivery.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.DraftExpiryPolicy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class DraftExpiryStoreTest {
    final Instant now=Instant.parse("2026-10-28T08:00:00Z");
    final ObjectMapper json=new ObjectMapper();
    JdbcTemplate jdbc; DraftExpiryStore expiry; DataSourceTransactionManager manager;
    @BeforeEach void database() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=1000","sa","");
        jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);expiry=new DraftExpiryStore(jdbc,json,manager);
        jdbc.execute("CREATE TABLE ad_plan(id varchar PRIMARY KEY,tenant_id varchar,status varchar,qc_plan_id varchar,source varchar,created_at timestamp)");
        jdbc.execute("CREATE TABLE ad_plan_creative(ad_plan_id varchar,product_id varchar)");
        jdbc.execute("CREATE TABLE delivery_creation(plan_id varchar PRIMARY KEY,tenant_id varchar,revision int,state_json text,work_pending int,lease_until timestamp,updated_at timestamp)");
        jdbc.execute("CREATE TABLE delivery_publish_attempt(tenant_id varchar,plan_id varchar,status varchar)");
    }
    void plan(String id,String tenant,String status,String qc,Instant created) {
        jdbc.update("INSERT INTO ad_plan VALUES(?,?,?,?, 'LINGTOU',?)",id,tenant,status,qc,Timestamp.from(created));
        jdbc.update("INSERT INTO ad_plan_creative VALUES(?,'product')",id);
    }
    void creation(String id,String tenant,String publish,int pending,Instant lease) {
        String body="{\"tenantId\":\""+tenant+"\",\"planId\":\""+id+"\",\"revision\":7,\"publishStatus\":\""+publish+"\",\"generationStatus\":\"RUNNING\",\"analysisStatus\":\"SUCCEEDED\",\"engineJobId\":\"saved-engine-job\",\"materials\":[]}";
        jdbc.update("INSERT INTO delivery_creation VALUES(?,?,7,?,?,?,?)",id,tenant,body,pending,lease==null?null:Timestamp.from(lease),Timestamp.from(now));
    }
    String status(String id){return jdbc.queryForObject("SELECT status FROM ad_plan WHERE id=?",String.class,id);}
    @Test void sevenDaysUsesCreationTimeAndIncludesExactDeadline() {
        assertFalse(DraftExpiryPolicy.expired(now.minusSeconds(604799),now));
        assertTrue(DraftExpiryPolicy.expired(now.minusSeconds(604800),now));
        assertFalse(DraftExpiryPolicy.expired(null,now));
        plan("old","tenant","DRAFT",null,now.minusSeconds(604800));creation("old","tenant","NONE",1,null);
        plan("fresh","tenant","DRAFT",null,now.minusSeconds(604799));
        assertEquals(1,expiry.expireDue("tenant",now,200));assertEquals("DELETED",status("old"));assertEquals("DRAFT",status("fresh"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ad_plan_creative WHERE ad_plan_id='old'",Integer.class));
        var body=jdbc.queryForObject("SELECT state_json FROM delivery_creation WHERE plan_id='old'",String.class);
        assertTrue(body.contains("saved-engine-job"));assertTrue(body.contains("CANCELLED"));
        assertEquals(0,jdbc.queryForObject("SELECT work_pending FROM delivery_creation WHERE plan_id='old'",Integer.class));
        assertEquals(0,expiry.expireDue("tenant",now,200));
    }
    @Test void tenantBoundaryAndPublishedHistoryArePreserved() {
        for(String status:new String[]{"DELIVERING","PAUSED","FINISHED","REJECTED","SUBMITTING"})plan(status,"tenant",status,"native-id",now.minusSeconds(900000));
        plan("bound-draft","tenant","DRAFT","native-id",now.minusSeconds(900000));
        plan("external","tenant","DRAFT",null,now.minusSeconds(900000));jdbc.update("UPDATE ad_plan SET source='EXTERNAL' WHERE id='external'");
        plan("other","foreign","DRAFT",null,now.minusSeconds(900000));
        assertEquals(0,expiry.expireDue("tenant",now,200));assertEquals("DRAFT",status("other"));
        assertEquals(1,expiry.expireDue(null,now,200));assertEquals("DELETED",status("other"));
        assertEquals("DRAFT",status("bound-draft"));assertEquals("DRAFT",status("external"));
    }
    @Test void pendingNativePublishAndActiveWorkerLeaseAreNotRemoved() {
        for(String publish:new String[]{"QUEUED","SENDING","UNCERTAIN","CREATED"}) {
            plan(publish,"tenant","DRAFT",null,now.minusSeconds(900000));creation(publish,"tenant",publish,1,null);
            assertDoesNotThrow(()->expiry.assertAvailable("tenant",publish,now));
        }
        plan("receipt","tenant","DRAFT",null,now.minusSeconds(900000));creation("receipt","tenant","FAILED",0,null);
        jdbc.update("INSERT INTO delivery_publish_attempt VALUES('tenant','receipt','CREATED')");
        plan("leased","tenant","DRAFT",null,now.minusSeconds(900000));creation("leased","tenant","NONE",1,now.plusSeconds(60));
        assertEquals(0,expiry.expireDue("tenant",now,200));assertEquals("DRAFT",status("leased"));
        assertEquals(1,expiry.expireDue("tenant",now.plusSeconds(61),200));assertEquals("DELETED",status("leased"));
    }
    @Test void directDraftReadsWritesAndStaleWorkerClaimsAreBlockedAtExpiry() {
        plan("old","tenant","DRAFT",null,Instant.now().minusSeconds(604801));creation("old","tenant","NONE",1,null);
        var store=new CreationStore(jdbc,json,manager);store.draftExpiry(expiry);
        assertEquals(1405,assertThrows(BizException.class,()->store.get("tenant","old")).code());
        assertThrows(BizException.class,()->store.mutate("tenant","old",7,s->fail("Expired draft must not mutate")));
        var snapshot=new CreationState();snapshot.tenantId="tenant";snapshot.planId="old";
        assertFalse(store.claim(snapshot));assertEquals(1,expiry.expireDue("tenant",Instant.now(),200));
        assertTrue(store.due().isEmpty());assertThrows(BizException.class,()->store.get("tenant","old"));
        assertDoesNotThrow(()->expiry.assertAvailable("foreign","old",Instant.now()));
    }
    @Test void aLockedConcurrentEditIsSkippedUntilItsTransactionFinishes() throws Exception {
        plan("locked","tenant","DRAFT",null,now.minusSeconds(900000));creation("locked","tenant","NONE",0,null);
        var ready=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var thread=new Thread(()->new TransactionTemplate(manager).executeWithoutResult(s->{
            jdbc.queryForObject("SELECT plan_id FROM delivery_creation WHERE plan_id='locked' FOR UPDATE",String.class);ready.countDown();
            try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        }));thread.start();assertTrue(ready.await(3,java.util.concurrent.TimeUnit.SECONDS));
        try{assertEquals(0,expiry.expireDue("tenant",now,200));assertEquals("DRAFT",status("locked"));}
        finally{release.countDown();thread.join(3000);}
        assertEquals(1,expiry.expireDue("tenant",now,200));
    }
}
