package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.application.GenerationResultNotice;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import com.lynx.ad.modules.tenant.infrastructure.persistence.InboxOutboxStore;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Read-side reconciliation must work with production-sized identities and real SQL constraints. */
class CreationReadNoticeTest {
    final ObjectMapper json=new ObjectMapper();
    final TenantId tenant=new TenantId(UUID.randomUUID().toString());
    final String plan=UUID.randomUUID().toString(), job=UUID.randomUUID().toString();
    JdbcTemplate jdbc;
    CreationStore store;
    CreationEngineClient engine;
    CreationWorkflowService service;
    GenerationResultNotice notices;

    void database(String mode) throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:creation_notice_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1","sa","");
        try(var c=ds.getConnection()) {
            String root="db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/";
            ScriptUtils.executeSqlScript(c,new ClassPathResource(root+"V18__creation_workflow.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource(root+"V38__audit_notification_outbox.sql"));
        }
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
        store=new CreationStore(jdbc,json,manager);engine=mock(CreationEngineClient.class);
        notices=new GenerationResultNotice(new InboxOutboxStore(jdbc,manager));
        service=new CreationWorkflowService(store,null,null,null,engine,json,null,null,null,null,null,manager);
        service.generationNotices(notices);
        var state=new CreationState();state.tenantId=tenant.value();state.planId=plan;state.name="历史草稿";
        state.engineJobId=job;state.generationStatus="FAILED";state.engineTerminalStatus="partial_failed";
        state.generationReconciliationAttempts=10;state.analysisStatus="SUCCEEDED";
        state.generationProductIds.add("product");
        state.products.add(new CreationState.Product("product","product","商品",null,null,null,null,null,List.of(),List.of()));
        for(String type:List.of("VIDEO","IMAGE","CAROUSEL"))for(int i=1;i<=2;i++) {
            var m=new CreationState.Material();m.id=type+i;m.type=type;m.productId="product";m.status="READY";state.materials.add(m);
        }
        store.insert(state);
        var response=json.createObjectNode().put("job_id",job).put("status","succeeded");
        var group=response.putArray("groups").addObject().put("group_id","group-1");
        group.putObject("expected").put("videos",2).put("image_texts",2).put("product_cards",2);
        var materials=group.putArray("materials");
        for(var m:state.materials)materials.addObject().put("material_id",m.id).put("version","v1").put("status","succeeded")
            .put("material_type",switch(m.type){case "VIDEO"->"video";case "IMAGE"->"product_card";default->"image_text";});
        when(engine.get(anyString())).thenReturn(response);
    }

    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void openingLegacyDraftCommitsCompletedResultAndExactlyOneNotice(String mode) throws Exception {
        database(mode);
        var result=service.get(tenant,plan);
        assertEquals("SUCCEEDED",result.generationStatus);assertEquals(100,result.progress);
        assertEquals("SUCCEEDED",store.get(tenant.value(),plan).generationStatus);
        assertEquals(6,result.materials.size());assertTrue(result.materials.stream().allMatch(m->"READY".equals(m.status)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        assertEquals(64,jdbc.queryForObject("SELECT LENGTH(event_key) FROM inbox_outbox",Integer.class));
        service.get(tenant,plan);
        store.mutate(tenant.value(),plan,null,notices::generated);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        verify(engine,times(1)).get(anyString());verify(engine,never()).post(any(),any());
    }

    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void failedNoticeWriteRollsBackReconciliationAndKeepsSavedPlanReadable(String mode) throws Exception {
        database(mode);
        jdbc.execute("ALTER TABLE inbox_outbox ADD CONSTRAINT reject_notice CHECK(title<>'素材生成阶段已完成')");
        var result=assertDoesNotThrow(()->service.get(tenant,plan));
        assertEquals("FAILED",result.generationStatus);assertEquals("partial_failed",result.engineTerminalStatus);
        assertEquals(6,result.materials.size());assertTrue(result.materials.stream().allMatch(m->"READY".equals(m.status)));
        assertEquals("FAILED",store.get(tenant.value(),plan).generationStatus);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT lease_until FROM delivery_creation",java.sql.Timestamp.class));
        // Retry after the separate read-side transaction has rolled back, with no regeneration or upload.
        jdbc.execute("ALTER TABLE inbox_outbox DROP CONSTRAINT reject_notice");
        store.mutate(tenant.value(),plan,null,s->s.generationReconciledAt=0);
        jdbc.update("UPDATE delivery_creation SET lease_until=NULL");
        assertEquals("SUCCEEDED",service.get(tenant,plan).generationStatus);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_outbox",Integer.class));
        verify(engine,times(2)).get(anyString());verify(engine,never()).post(any(),any());
    }
}
