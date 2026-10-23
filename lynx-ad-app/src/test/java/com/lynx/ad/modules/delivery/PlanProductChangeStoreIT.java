package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named="LYNXAD_PRODUCTS_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:(?:3306|55406)/lynxad_products_verify_[a-zA-Z0-9_]+.*")
class PlanProductChangeStoreIT {
    static JdbcTemplate jdbc;static PlanTransferStore transfers;static PlanProductChangeStore changes;static TransactionTemplate tx;static CreationStore creations;
    final TenantId tenant=new TenantId("tenant-a");
    @BeforeAll static void setup() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("LYNXAD_PRODUCTS_TEST_URL"),"root",System.getenv("LYNXAD_PRODUCTS_TEST_PASSWORD"));jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        jdbc.execute("CREATE TABLE IF NOT EXISTS ad_plan(id VARCHAR(64) NOT NULL,tenant_id VARCHAR(64) NOT NULL,PRIMARY KEY(tenant_id,id))");
        for(var migration:Map.of("delivery_product_change","V26__plan_product_change.sql","delivery_transfer_group","V22__plan_transfer.sql","delivery_creation","V18__creation_workflow.sql").entrySet())
            if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,migration.getKey())==0)
                try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/"+migration.getValue()));}
        transfers=new PlanTransferStore(jdbc,manager);changes=new PlanProductChangeStore(jdbc);creations=new CreationStore(jdbc,new ObjectMapper(),manager);
    }
    @BeforeEach void reset() {for(var table:List.of("delivery_product_change","delivery_transfer_attempt","delivery_transfer_group","delivery_creation","delivery_publish_attempt","ad_plan"))jdbc.update("DELETE FROM "+table);}
    AdPlan plan() {return new AdPlan(new PlanId("root"),tenant,new AccountId("account"),new AwemeId("99"),new PlanName("计划"),PlanStatus.PAUSED,PlanSource.LINGTOU,new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(100),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(),new QcPlanId("111"),"DISABLE",null,1,null,List.of());}
    PlanProductChangeStore.Change change(String key) {return new PlanProductChangeStore.Change(tenant.value(),key,"root","root","3841021345144111165","[{\"id\":\"root\",\"qcId\":\"111\"}]","PENDING",null);}
    @Test void durablePendingLocksTransferAndDeletionUntilAtomicCompletion() {var p=plan();var c=change("key");tx.executeWithoutResult(s->{transfers.lockForProductChange(p,c.key());changes.insert(c);});assertThrows(BizException.class,()->transfers.assertIdle(tenant,p.id()));assertThrows(BizException.class,()->tx.executeWithoutResult(s->transfers.lockForDeletion(p)));assertEquals(c,changes.get(tenant,c.key()).orElseThrow());tx.executeWithoutResult(s->{changes.complete(c);transfers.finishProductChange(tenant,"root",c.key());});assertNull(transfers.group(tenant,p.id()).requestKey());assertEquals("DONE",changes.get(tenant,c.key()).orElseThrow().status());}
    @Test void failedPreflightTransactionLeavesNoPendingGroupOrOperation() {assertThrows(RuntimeException.class,()->tx.executeWithoutResult(s->{transfers.lockForProductChange(plan(),"key");changes.insert(change("key"));throw new RuntimeException("fixture");}));assertNull(transfers.group(tenant,plan().id()));assertTrue(changes.get(tenant,"key").isEmpty());}
    @Test void onlyOneConcurrentWorkerCanLeaseDurableOperation() throws Exception {var c=change("key");changes.insert(c);var threads=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);try {Callable<Boolean> worker=()->{gate.await();return changes.claim(c);};var one=threads.submit(worker);var two=threads.submit(worker);gate.countDown();assertNotEquals(one.get(),two.get());}finally{threads.shutdownNow();}changes.release(c,"待核查");assertTrue(changes.claim(c));changes.complete(c);assertFalse(changes.claim(c));}
    @Test void conflictingKeysAndOtherTenantCannotReleasePendingGroup() {var p=plan();tx.executeWithoutResult(s->transfers.lockForProductChange(p,"one"));assertThrows(BizException.class,()->tx.executeWithoutResult(s->transfers.lockForProductChange(p,"two")));assertThrows(BizException.class,()->transfers.finishProductChange(new TenantId("other"),"root","one"));assertEquals("one",transfers.group(tenant,p.id()).requestKey());}
    @Test void localCleanupAndOperationCompletionRollBackTogether() {var p=plan();var c=change("key");var state=new CreationState();state.planId="root";state.tenantId=tenant.value();state.products.add(new CreationState.Product("a",c.product(),"商品",null,null,null,null,null,List.of(),List.of()));creations.insert(state);tx.executeWithoutResult(s->{transfers.lockForProductChange(p,c.key());changes.insert(c);});assertThrows(RuntimeException.class,()->tx.executeWithoutResult(s->{creations.mutate(tenant.value(),"root",null,v->{v.products.clear();v.changed();});changes.complete(c);transfers.finishProductChange(tenant,"root",c.key());throw new RuntimeException("fixture");}));assertEquals(1,creations.get(tenant.value(),"root").products.size());assertEquals("PENDING",changes.get(tenant,c.key()).orElseThrow().status());assertEquals(c.key(),transfers.group(tenant,p.id()).requestKey());}
}
