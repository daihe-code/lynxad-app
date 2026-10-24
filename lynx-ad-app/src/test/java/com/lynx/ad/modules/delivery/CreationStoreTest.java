package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker=true)
class CreationStoreTest {
    @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.0");
    static CreationStore store;static JdbcTemplate jdbc;
    @BeforeAll static void create() throws Exception {
        var ds=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V18__creation_workflow.sql"));}
        jdbc=new JdbcTemplate(ds);store=new CreationStore(jdbc,new ObjectMapper(),new DataSourceTransactionManager(ds));
    }
    @BeforeEach void reset(){jdbc.update("DELETE FROM delivery_publish_attempt");jdbc.update("DELETE FROM delivery_creation");}
    @Test void productMediaUsesOnlyExplicitBindingsWithinTenantAndAccount() {
        var s=new CreationState();s.planId="linked";s.tenantId="t";s.accountId="account";
        var m=new CreationState.Material();m.productId="product";m.videoAssetId="video";m.carouselId="carousel";m.imageAssetIds=List.of("image");s.materials.add(m);store.insert(s);
        assertEquals(Set.of("video","carousel","image"),store.productMedia("t","account","product"));
        assertTrue(store.productMedia("t","other","product").isEmpty());assertTrue(store.productMedia("other","account","product").isEmpty());assertTrue(store.productMedia("t","account","unlinked").isEmpty());
    }
    CreationState state(String id){var s=new CreationState();s.planId=id;s.tenantId="t";store.insert(s);return s;}
    @Test void rowLockAllowsOneEditorAndRollsBackInvalidMutation() throws Exception {
        state("one");var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<Boolean> write=()->{gate.await();try{store.mutate("t","one",1,s->s.changed());return true;}catch(BizException e){assertEquals(1404,e.code());return false;}};
        var first=executor.submit(write);var second=executor.submit(write);gate.countDown();assertNotEquals(first.get(),second.get());executor.shutdownNow();assertEquals(2,store.get("t","one").revision);
        assertThrows(BizException.class,()->store.get("other","one"));
        assertThrows(BizException.class,()->store.mutate("t","one",2,s->{s.name="must rollback";throw new BizException(1402,"invalid");}));assertNull(store.get("t","one").name);
    }
    @Test void idleDraftsCannotStarveActiveTasksAndLeasePreventsTwoWorkers(){
        for(int i=0;i<110;i++)state("idle"+i);var s=state("active");store.mutate("t",s.planId,null,x->x.analysisStatus="QUEUED");
        assertEquals(List.of("active"),store.due().stream().map(x->x.planId).toList());assertTrue(store.claim(s));assertFalse(store.claim(s));assertTrue(store.due().isEmpty());
    }
    @Test void aCreationReceiptSurvivesLateFailureAndRepairsOnlyOnce(){
        state("p");var attempt=new CreationStore.Attempt("id","t","p",1,"request","hash","{}","QUEUED",null,null);store.attempt(attempt);
        assertTrue(store.claimPublish("t","request"));assertFalse(store.claimPublish("t","request"));
        store.finishPublish("t","request","CREATED","9007199254740993",null);store.finishPublish("t","request","UNCERTAIN",null,"late local error");
        assertEquals("9007199254740993",store.attempt("t","request").orElseThrow().qcId());assertEquals(1,store.receipts().size());store.applied(attempt);assertTrue(store.receipts().isEmpty());assertTrue(store.queued().isEmpty());
    }
    @Test void newKeyCannotCreateAnotherAttemptForTheSamePlanRevision(){
        state("p");store.attempt(new CreationStore.Attempt("id","t","p",1,"request","hash","{}","QUEUED",null,null));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->store.attempt(new CreationStore.Attempt("other","t","p",1,"new-key","hash","{}","QUEUED",null,null)));
    }
    @Test void productSnapshotsAreScopedToTenantAndRequestedPlans(){
        state("p");state("other-plan");
        store.mutate("t","p",1,s->s.products.add(new CreationState.Product("123","123","真实商品",null,null,"https://example.test/image.jpg","",10L,List.of(),List.of())));
        assertEquals("真实商品",store.productsByPlan("t",List.of("p")).get("p").getFirst().name());
        assertFalse(store.productsByPlan("t",List.of("p")).containsKey("other-plan"));
        assertTrue(store.productsByPlan("other-tenant",List.of("p")).isEmpty());
        store.mutate("t","p",null,s->s.recommendations=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("marker","tenant-t"));
        assertEquals("tenant-t",store.summariesByPlan("t",List.of("p")).get("p").recommendations().path("marker").asText());
        assertFalse(store.summariesByPlan("t",List.of("p")).containsKey("other-plan"));
        assertTrue(store.summariesByPlan("other-tenant",List.of("p")).isEmpty());
    }
    @Test void failedSummaryStaysScheduledForBoundedReadOnlyReconciliation(){
        state("reconcile");
        store.mutate("t","reconcile",null,s->{s.engineJobId="job";s.engineTerminalStatus="partial_failed";s.generationStatus="FAILED";});
        assertEquals(List.of("reconcile"),store.due().stream().map(s->s.planId).toList());
        var failed=store.get("t","reconcile");assertTrue(store.claim(failed));store.release(failed);
        assertTrue(store.due().isEmpty());
        assertTrue(jdbc.queryForObject("SELECT lease_until FROM delivery_creation WHERE plan_id='reconcile'",java.sql.Timestamp.class)
                .toInstant().isAfter(java.time.Instant.now().plusSeconds(20)));
        store.mutate("t","reconcile",null,s->s.generationReconciliationAttempts=10);
        assertEquals(0,jdbc.queryForObject("SELECT work_pending FROM delivery_creation WHERE plan_id='reconcile'",Integer.class));
    }
}
