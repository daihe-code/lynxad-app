package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Uses an explicitly supplied isolated database; never the development plan database. */
@EnabledIfEnvironmentVariable(named="LYNXAD_TRANSFER_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/lynxad_transfer_verify_[a-zA-Z0-9_]+.*")
class PlanTransferStoreIT {
    static JdbcTemplate jdbc;static PlanTransferStore store;
    final TenantId tenant=new TenantId("tenant-a");
    @BeforeAll static void setup() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("LYNXAD_TRANSFER_TEST_URL"),"root",System.getenv("LYNXAD_TRANSFER_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE IF NOT EXISTS ad_plan(id VARCHAR(64) NOT NULL,tenant_id VARCHAR(64) NOT NULL,PRIMARY KEY(tenant_id,id))");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='delivery_transfer_group'",Integer.class)==0)
            try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V22__plan_transfer.sql"));}
        store=new PlanTransferStore(jdbc,new DataSourceTransactionManager(ds));
    }
    @BeforeEach void reset(){jdbc.update("DELETE FROM delivery_transfer_attempt");jdbc.update("DELETE FROM delivery_transfer_group");jdbc.update("DELETE FROM ad_plan");}
    AdPlan plan(String tenant,String id){return new AdPlan(new PlanId(id),new TenantId(tenant),new AccountId("a"),new AwemeId("99"),new PlanName("计划"),PlanStatus.DELIVERING,PlanSource.LINGTOU,new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(100),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(),new QcPlanId("111"),"DELIVERY_OK",null,1,null,List.of());}
    Attempt attempt(String key,String root){return new Attempt(tenant.value(),key,root,root,"target","{}","{}",true,null,"RUNNING","PREPARED",null,Instant.now());}
    @Test void onlyOneConcurrentTransferCanClaimTheGroup() throws Exception {
        store.ensure(plan(tenant.value(),"root"));var threads=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<Boolean> a=()->{gate.await();try{store.claim(attempt(UUID.randomUUID().toString(),"root"));return true;}catch(BizException e){return false;}};
        try {var one=threads.submit(a);var two=threads.submit(a);gate.countDown();assertNotEquals(one.get(),two.get());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM delivery_transfer_attempt",Integer.class));}finally{threads.shutdownNow();}
    }
    @Test void duplicateKeyRollsBackTheSecondGroupsClaim(){
        store.ensure(plan(tenant.value(),"one"));store.ensure(plan(tenant.value(),"two"));store.claim(attempt("same-key","one"));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->store.claim(attempt("same-key","two")));
        assertNull(store.group(tenant,new PlanId("two")).requestKey());assertEquals("same-key",store.group(tenant,new PlanId("one")).requestKey());
    }
    @Test void receiptUncertaintyBlocksNewWritesAndSurvivesRecovery(){
        store.ensure(plan(tenant.value(),"root"));store.claim(attempt("first-key","root"));store.phase(tenant,"first-key","CREATED","222");store.finish(tenant,"first-key","UNCERTAIN","network");
        assertEquals("222",store.attempt(tenant,"first-key").orElseThrow().qcId());assertThrows(BizException.class,()->store.assertIdle(tenant,new PlanId("root")));
        store.member(tenant,"root",SmartBidType.CUSTOM,"target");store.finish(tenant,"first-key","SUCCEEDED",null);
        assertEquals("target",store.activeId(tenant,new PlanId("root")).value());assertEquals("target",store.activeId(tenant,new PlanId("target")).value());assertNull(store.group(tenant,new PlanId("root")).requestKey());
    }
    @Test void identifiersAndKeysAreTenantScoped(){
        store.ensure(plan("tenant-a","root"));store.ensure(plan("tenant-b","root"));store.claim(attempt("same-key","root"));
        assertNull(store.group(new TenantId("tenant-b"),new PlanId("root")).requestKey());assertTrue(store.attempt(new TenantId("tenant-b"),"same-key").isEmpty());assertTrue(store.groups(new TenantId("unknown")).isEmpty());
    }
    @Test void importedPairHasOneStableRootAndCannotJoinAnotherGroup(){
        var root=plan(tenant.value(),"root");var current=plan(tenant.value(),"current");var other=plan(tenant.value(),"other");
        current.updateSetting(new DeliverySetting(SmartBidType.CUSTOM,current.setting().dailyBudget(),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()));
        for(String id:List.of("root","current","other"))jdbc.update("INSERT INTO ad_plan(id,tenant_id) VALUES(?,?)",id,tenant.value());
        var g=store.linkImported(root,current);assertEquals("root",g.root());assertEquals("current",g.active());assertEquals("root",store.group(tenant,current.id()).root());
        assertEquals(g,store.linkImported(root,current));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM delivery_transfer_group",Integer.class));
        assertThrows(BizException.class,()->store.linkImported(other,current));
        assertTrue(store.groups(new TenantId("other-tenant")).isEmpty());
    }
    @Test void selectedNonActiveMemberCanClaimButOnlyWithCurrentGroupVersion(){
        var root=plan(tenant.value(),"root");var cost=plan(tenant.value(),"cost");
        cost.updateSetting(new DeliverySetting(SmartBidType.CUSTOM,cost.setting().dailyBudget(),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()));
        for(String id:List.of("root","cost"))jdbc.update("INSERT INTO ad_plan(id,tenant_id) VALUES(?,?)",id,tenant.value());
        store.linkImported(root,cost);
        var a=new Attempt(tenant.value(),"new-key","root","root","cost","{}","{}",false,"222","RUNNING","PREPARED",null,Instant.now());
        assertThrows(BizException.class,()->store.claim(a,"root"));assertNull(store.group(tenant,root.id()).requestKey());
        store.claim(a,"cost");assertEquals("new-key",store.group(tenant,cost.id()).requestKey());
        assertEquals("222",store.attempt(tenant,"new-key").orElseThrow().qcId());
    }
    @Test void deletingOriginalMemberRetainsSharedRootAndLeavesSurvivorAvailableForNewTransfer(){
        var root=plan(tenant.value(),"root");var cost=plan(tenant.value(),"cost");
        cost.updateSetting(new DeliverySetting(SmartBidType.CUSTOM,cost.setting().dailyBudget(),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()));
        for(String id:List.of("root","cost"))jdbc.update("INSERT INTO ad_plan(id,tenant_id) VALUES(?,?)",id,tenant.value());
        store.linkImported(root,cost);store.detachDeleted(tenant,root.id());
        var g=store.group(tenant,cost.id());assertEquals("root",g.root());assertNull(g.conservative());
        assertEquals("cost",g.custom());assertEquals("cost",g.active());
        var next=new Attempt(tenant.value(),"replacement","root","cost","replacement-plan","{}","{}",false,null,"RUNNING","PREPARED",null,Instant.now());
        store.claim(next,g.active());store.member(tenant,"root",SmartBidType.CONSERVATIVE,"replacement-plan");store.finish(tenant,"replacement","SUCCEEDED",null);
        assertEquals("replacement-plan",store.group(tenant,cost.id()).conservative());assertEquals("root",store.group(tenant,new PlanId("replacement-plan")).root());
    }
    @Test void pendingTransferCannotBeDetachedByDeletion(){
        var root=plan(tenant.value(),"root");store.ensure(root);store.claim(attempt("pending","root"));
        assertThrows(BizException.class,()->store.lockForDeletion(root));assertThrows(BizException.class,()->store.detachDeleted(tenant,root.id()));
        assertEquals("root",store.group(tenant,root.id()).conservative());assertEquals("pending",store.group(tenant,root.id()).requestKey());
    }
    @Test void deletionTransactionBlocksTransferClaimUntilMemberIsDetached() throws Exception {
        var root=plan(tenant.value(),"root");store.ensure(root);
        var manager=new DataSourceTransactionManager(jdbc.getDataSource());var transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var threads=Executors.newFixedThreadPool(2);
        try {
            var deletion=threads.submit(()->transaction.executeWithoutResult(s->{store.lockForDeletion(root);locked.countDown();
                try {assertTrue(release.await(10,TimeUnit.SECONDS));} catch(InterruptedException e){throw new RuntimeException(e);}
                store.detachDeleted(tenant,root.id());}));
            assertTrue(locked.await(10,TimeUnit.SECONDS));
            var transfer=threads.submit(()->{try {store.claim(attempt("competing","root"));return true;}catch(BizException e){return false;}});
            assertThrows(TimeoutException.class,()->transfer.get(150,TimeUnit.MILLISECONDS));
            release.countDown();deletion.get(10,TimeUnit.SECONDS);assertFalse(transfer.get(10,TimeUnit.SECONDS));
            assertNull(store.group(tenant,root.id()).conservative());assertNull(store.group(tenant,root.id()).requestKey());
        } finally {release.countDown();threads.shutdownNow();}
    }
}
