package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

class DraftProductStoreTest {
    JdbcTemplate jdbc;DataSourceTransactionManager manager;DraftProductStore store;TransactionTemplate tx;
    final TenantId tenant=new TenantId("tenant");
    @AfterEach void clear(){TenantContext.clear();}
    void database(String mode)throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:draft_products_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=4000","sa","");
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);store=new DraftProductStore(jdbc);tx=new TransactionTemplate(manager);
        // Exercise current-read behavior after an earlier snapshot, as on MySQL's default isolation.
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        jdbc.execute("CREATE TABLE oauth_account(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE ad_plan(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),oauth_account_id VARCHAR(64),aweme_id VARCHAR(64),name VARCHAR(255),status VARCHAR(24),qc_plan_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE ad_plan_creative(ad_plan_id VARCHAR(64),product_id VARCHAR(64),PRIMARY KEY(ad_plan_id,product_id))");
        jdbc.update("INSERT INTO oauth_account VALUES('account','tenant'),('other-account','tenant'),('foreign-account','foreign')");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V18__creation_workflow.sql"));}
    }
    void draft(String id,String tenantId,String account,String aweme,String product) {
        jdbc.update("INSERT INTO ad_plan VALUES(?,?,?,?,?,'DRAFT',NULL)",id,tenantId,account,aweme,"草稿-"+id);
        jdbc.update("INSERT INTO ad_plan_creative VALUES(?,?)",id,product);
    }
    void check(String account,List<String> ids,String except){tx.executeWithoutResult(s->store.assertAvailable("tenant",account,ids,except));}
    CreationState.Product product(String id){return new CreationState.Product(id,id,"商品-"+id,null,null,null,null,10L,List.of(),List.of());}

    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void sameAccountBlocksOtherDraftsAcrossAwemesButAllowsItsOwn(String mode)throws Exception {
        database(mode);draft("existing","tenant","account","aweme-one","123");
        var conflict=assertThrows(BizException.class,()->check("account",List.of("123"),"new"));
        assertEquals(1404,conflict.code());assertTrue(conflict.getMessage().contains("草稿-existing"));assertTrue(conflict.getMessage().contains("existing"));
        assertDoesNotThrow(()->check("account",List.of("123"),"existing"));
        assertDoesNotThrow(()->check("account",List.of("456"),null));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void accountAndTenantIsolationDoNotLeakOtherDraftNames(String mode)throws Exception {
        database(mode);draft("other","tenant","other-account","aweme","123");draft("private","foreign","foreign-account","aweme","456");
        assertDoesNotThrow(()->check("account",List.of("123","456"),null));
        var visible=store.decorate("tenant","account",null,List.of(product("123"),product("456")));
        assertTrue(visible.stream().allMatch(p->p.draft()==null));
        assertEquals(1201,assertThrows(BizException.class,()->check("foreign-account",List.of("456"),null)).code());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void decorationKeepsPaginationAndAvailabilityButIdentifiesExistingDraft(String mode)throws Exception {
        database(mode);draft("existing","tenant","account","aweme","123");
        var products=store.decorate("tenant","account",null,List.of(product("123"),product("456")));
        assertEquals(2,products.size());assertEquals("existing",products.getFirst().draft().planId());
        assertEquals("草稿-existing",products.getFirst().draft().planName());assertTrue(products.getFirst().unavailableReasons().isEmpty());
        assertNull(products.getLast().draft());assertNull(store.decorate("tenant","account","existing",List.of(product("123"))).getFirst().draft());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void removalDeletionAndSuccessfulPublicationFreeMembership(String mode)throws Exception {
        database(mode);draft("existing","tenant","account","aweme","123");
        jdbc.update("DELETE FROM ad_plan_creative WHERE ad_plan_id='existing'");assertDoesNotThrow(()->check("account",List.of("123"),null));
        jdbc.update("INSERT INTO ad_plan_creative VALUES('existing','123')");jdbc.update("UPDATE ad_plan SET qc_plan_id='9001' WHERE id='existing'");
        assertDoesNotThrow(()->check("account",List.of("123"),null));
        jdbc.update("UPDATE ad_plan SET qc_plan_id=NULL,status='SUBMITTING' WHERE id='existing'");assertDoesNotThrow(()->check("account",List.of("123"),null));
        jdbc.update("DELETE FROM ad_plan WHERE id='existing'");assertDoesNotThrow(()->check("account",List.of("123"),null));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void legacyDuplicatesRemainVisibleAndAreNotSilentlyDeleted(String mode)throws Exception {
        database(mode);draft("a","tenant","account","aweme","123");draft("b","tenant","account","aweme","123");
        assertThrows(BizException.class,()->check("account",List.of("123"),"a"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ad_plan",Integer.class));
        assertEquals("b",store.decorate("tenant","account","a",List.of(product("123"))).getFirst().draft().planId());
        tx.executeWithoutResult(s->{store.assertAvailable("tenant","account",List.of("456"),"a");jdbc.update("UPDATE ad_plan_creative SET product_id='456' WHERE ad_plan_id='a'");});
        assertDoesNotThrow(()->check("account",List.of("123"),"b"));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void rollbackAndConflictsPreserveOriginalDraftMembership(String mode)throws Exception {
        database(mode);draft("a","tenant","account","aweme","123");draft("b","tenant","account","aweme","456");
        assertThrows(BizException.class,()->tx.executeWithoutResult(s->{store.assertAvailable("tenant","account",List.of("456"),"a");jdbc.update("DELETE FROM ad_plan_creative WHERE ad_plan_id='a'");}));
        assertEquals("123",jdbc.queryForObject("SELECT product_id FROM ad_plan_creative WHERE ad_plan_id='a'",String.class));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{store.assertAvailable("tenant","account",List.of("789"),"a");jdbc.update("UPDATE ad_plan_creative SET product_id='789' WHERE ad_plan_id='a'");throw new IllegalStateException("rollback");}));
        assertEquals("123",jdbc.queryForObject("SELECT product_id FROM ad_plan_creative WHERE ad_plan_id='a'",String.class));
        assertThrows(IllegalStateException.class,()->store.assertAvailable("tenant","account",List.of("123"),null));
    }

    CreationWorkflowService workflow(CreationStore creation) {
        var plans=mock(AdPlanRepository.class);var bindings=mock(AdPlanCreativeBindingStore.class);var qc=mock(CreationQianchuanGateway.class);
        when(qc.selected(eq(tenant),eq("account"),eq("aweme"),anyList())).thenAnswer(call->((List<String>)call.getArgument(3)).stream().map(this::product).toList());
        doAnswer(call->{AdPlan p=call.getArgument(0);jdbc.update("INSERT INTO ad_plan VALUES(?,?,?,?,?,'DRAFT',NULL)",p.id().value(),p.tenantId().value(),p.accountId().value(),p.awemeId().value(),p.name().text());return null;}).when(plans).save(any());
        doAnswer(call->{PlanId id=call.getArgument(0);for(var binding:(List<PlanCreativeBinding>)call.getArgument(1))jdbc.update("INSERT INTO ad_plan_creative VALUES(?,?)",id.value(),binding.productId().value());return null;}).when(bindings).upsert(any(),anyList());
        var workflow=new CreationWorkflowService(creation,plans,bindings,qc,null,new ObjectMapper(),null,null,null,null,mock(BillingTrialService.class),manager);
        workflow.draftProducts(new DraftProductStore(jdbc));return workflow;
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void twoIndependentApisCannotCreateTwoDraftsForTheSameProduct(String mode)throws Exception {
        database(mode);var json=new ObjectMapper();var a=workflow(new CreationStore(jdbc,json,manager));var b=workflow(new CreationStore(jdbc,json,manager));
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            java.util.function.Function<CreationWorkflowService,Callable<Boolean>> request=api->()->{
                TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));gate.await();
                try{api.select(tenant,new CreationWorkflowService.Selection(null,null,"account","aweme",List.of("123")));return true;}
                catch(BizException conflict){assertEquals(1404,conflict.code());return false;}finally{TenantContext.clear();}
            };
            var first=pool.submit(request.apply(a));var second=pool.submit(request.apply(b));gate.countDown();assertNotEquals(first.get(12,TimeUnit.SECONDS),second.get(12,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ad_plan",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ad_plan_creative WHERE product_id='123'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM delivery_creation",Integer.class));
    }
}
