package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.RegenerationPolicy;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Actual draft transactions and two independent API services; no Docker or external generation. */
class CreationRegenerationTest {
    final ObjectMapper json=new ObjectMapper();final TenantId tenant=new TenantId("tenant");
    JdbcTemplate jdbc;CreationStore store,otherStore;CreationWorkflowService first,second;CreationEngineClient engine;CreationAssetImporter importer;AssetUploadService assets;
    void login(){TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));}
    @AfterEach void clear(){TenantContext.clear();}
    void database(String mode)throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:regeneration_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000","sa","");
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V18__creation_workflow.sql"));}
        store=new CreationStore(jdbc,json,manager);otherStore=new CreationStore(new JdbcTemplate(ds),json,manager);
        engine=mock(CreationEngineClient.class);importer=mock(CreationAssetImporter.class);assets=mock(AssetUploadService.class);
        var s=new CreationState();s.tenantId="tenant";s.userId="user";s.planId="plan";s.accountId="account";s.enginePlanId="apt";s.engineJobId="job";s.generationStatus="SUCCEEDED";
        s.products.add(new CreationState.Product("123","123","商品",null,null,"https://assets.test/source.png","",null,List.of(),List.of()));
        var m=new CreationState.Material();m.id="source";m.productId="123";m.type="IMAGE";m.engineMaterialId="parent";m.engineJobId="job";m.groupId="group-1";m.status="READY";m.title="商品展示标题";m.titles=List.of(m.title);s.materials.add(m);store.insert(s);
        first=workflow(store,manager);second=workflow(otherStore,manager);login();
        when(engine.post(contains("/revisions"),any())).thenReturn(accepted());
    }
    CreationWorkflowService workflow(CreationStore db,DataSourceTransactionManager manager){return new CreationWorkflowService(db,null,null,null,engine,json,importer,assets,null,mock(CreationPayloadBuilder.class),mock(BillingTrialService.class),manager);}
    CreationState state(){return store.get("tenant","plan");}
    CreationWorkflowService.Feedback feedback(int revision){return new CreationWorkflowService.Feedback(revision,List.of("商品展示不清晰"),"保留商品款式");}
    JsonNode row(String id,String status,String material) {
        var r=json.createObjectNode().put("id",id).put("parent_material_id","parent").put("generation_job_id","job").put("execution_job_id","execution").put("ai_plan_task_id","apt").put("store_id","tenant").put("group_id","group-1").put("operation","regenerate").put("target","product_card").put("instruction","商品展示不清晰；保留商品款式").put("status",status);if(material!=null)r.put("material_id",material);return r;
    }
    JsonNode accepted(){var r=json.createObjectNode().put("job_id","job").put("ai_plan_task_id","apt").put("group_id","group-1").put("parent_material_id","parent").put("revision_id","revision").put("execution_job_id","execution").put("status","queued");r.set("revision",row("revision","queued",null));return r;}
    JsonNode list(JsonNode... rows){var r=json.createObjectNode().put("material_id","parent");r.putArray("items").addAll(List.of(rows));return r;}
    @ParameterizedTest @CsvSource({"PostgreSQL,VIDEO,video","PostgreSQL,IMAGE,product_card","PostgreSQL,CAROUSEL,image_text","MySQL,VIDEO,video","MySQL,IMAGE,product_card","MySQL,CAROUSEL,image_text"})
    void everyMaterialTypeRegeneratesOnlyItsOwnEngineSource(String mode,String type,String target)throws Exception {
        database(mode);
        var prepared=store.mutate("tenant","plan",null,s->{
            s.materials.getFirst().type=type;
            var other=new CreationState.Material();other.id="untouched";other.productId="123";other.type=type;other.status="READY";s.materials.add(other);s.changed();
        });
        var receipt=(com.fasterxml.jackson.databind.node.ObjectNode)accepted();
        ((com.fasterxml.jackson.databind.node.ObjectNode)receipt.path("revision")).put("target",target);
        when(engine.post(any(),any())).thenReturn(receipt);
        first.regenerate(tenant,"plan","source",feedback(prepared.revision));
        verify(engine,times(1)).post(eq("/api/v1/capabilities/generation/materials/parent/revisions"),argThat(body->body instanceof Map<?,?> values&&target.equals(values.get("target"))&&"job".equals(values.get("job_id"))&&"group-1".equals(values.get("group_id"))));
        var restored=otherStore.get("tenant","plan");
        assertEquals("CONFIRMED",restored.regeneration.status);assertEquals(target,restored.regeneration.target);
        assertEquals(List.of("source","untouched"),restored.materials.stream().map(m->m.id).toList());
        assertTrue(restored.materials.stream().allMatch(m->"READY".equals(m.status)));
    }
    @ParameterizedTest @CsvSource({"PostgreSQL,VIDEO","PostgreSQL,IMAGE","PostgreSQL,CAROUSEL","MySQL,VIDEO","MySQL,IMAGE","MySQL,CAROUSEL"})
    void deletingOneMaterialPersistsWithoutDeletingItsNeighborOrCallingTheModel(String mode,String type)throws Exception {
        database(mode);
        var prepared=store.mutate("tenant","plan",null,s->{
            s.materials.getFirst().type=type;
            var other=new CreationState.Material();other.id="untouched";other.productId="123";other.type=type;other.status="READY";s.materials.add(other);s.changed();
        });
        first.remove(tenant,"plan","source",feedback(prepared.revision));
        var restored=otherStore.get("tenant","plan");
        assertEquals(List.of("untouched"),restored.materials.stream().map(m->m.id).toList());
        assertEquals("READY",restored.materials.getFirst().status);assertTrue(restored.removedMaterialIds.contains("source"));
        assertEquals(prepared.packageVersion+1,restored.packageVersion);assertEquals(1,restored.feedback.size());
        assertEquals(1404,assertThrows(BizException.class,()->second.remove(tenant,"plan","untouched",feedback(prepared.revision))).code());
        assertEquals(1,otherStore.get("tenant","plan").materials.size());verifyNoInteractions(engine,importer);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void reasonAndSourceValidationHappenBeforeAnyModelCallOrDraftChange(String mode)throws Exception {
        database(mode);
        for(var reasons:Arrays.<List<String>>asList(null,List.of(),List.of(" ")))assertEquals(1503,assertThrows(BizException.class,()->first.regenerate(tenant,"plan","source",new CreationWorkflowService.Feedback(1,reasons,""))).code());
        assertThrows(BizException.class,()->first.regenerate(tenant,"plan","source",new CreationWorkflowService.Feedback(1,List.of("字".repeat(100),"句".repeat(100)),"说明")));
        assertEquals(1,state().revision);assertTrue(state().feedback.isEmpty());verifyNoInteractions(engine);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void independentApisReserveOneDispatchAndPreserveOriginal(String mode)throws Exception {
        database(mode);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var work=(java.util.function.Function<CreationWorkflowService,Callable<Boolean>>)(api->()->{gate.await();login();try{api.regenerate(tenant,"plan","source",feedback(1));return true;}catch(BizException conflict){assertEquals(1404,conflict.code());return false;}finally{TenantContext.clear();}});
            var a=pool.submit(work.apply(first));var b=pool.submit(work.apply(second));gate.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        verify(engine,times(1)).post(contains("/parent/revisions"),any());assertEquals("REVISING",state().generationStatus);assertEquals("CONFIRMED",state().regeneration.status);assertEquals("READY",state().materials.getFirst().status);assertEquals(1,state().feedback.size());
        var wire=json.valueToTree(CreationView.of(state()));assertTrue(wire.path("regenerationRetryAt").asLong()>0);assertFalse(wire.has("regeneration"));assertFalse(wire.toString().contains("execution"));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void backgroundCannotTreatTheOriginalCompletedJobAsAnAcceptedRevision(String mode)throws Exception {
        database(mode);first.regenerate(tenant,"plan","source",feedback(1));
        when(engine.get(contains("/revisions?"))).thenReturn(list(row("revision","queued",null)));
        second.process(otherStore.get("tenant","plan"));assertEquals("REVISING",state().generationStatus);assertEquals(0,state().generationCounts.getFirst().completed());verify(engine,never()).get(contains("/generation/jobs/"));verifyNoInteractions(importer);
        when(engine.get(contains("/revisions?"))).thenReturn(list(row("revision","failed",null)));second.process(state());
        assertEquals("FAILED",state().generationStatus);assertEquals("FAILED",CreationView.of(state()).generationResultStatus());assertEquals("READY",state().materials.getFirst().status);assertEquals("source",state().materials.getFirst().id);verify(engine,times(1)).post(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void timeoutOrMalformedReceiptNeverResendsAndNeverAdoptsAnotherRevision(String mode)throws Exception {
        database(mode);when(engine.post(any(),any())).thenThrow(new BizException(8003,"reply lost"));first.regenerate(tenant,"plan","source",feedback(1));
        for(int i=0;i<3;i++)second.process(state());assertEquals("REVISING",state().generationStatus);assertEquals("UNCERTAIN",state().regeneration.status);verify(engine,times(1)).post(any(),any());verify(engine,never()).get(any());
        assertEquals(0,jdbc.queryForObject("SELECT work_pending FROM delivery_creation WHERE plan_id='plan'",Integer.class));
        assertEquals("UNCERTAIN",CreationView.of(state()).generationResultStatus());
        assertEquals("READY",state().materials.getFirst().status);assertTrue(state().generationMessage.contains("缺少本次修订"));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void missingOrForeignRevisionMetadataCannotFinishTheOperation(String mode)throws Exception {
        database(mode);first.regenerate(tenant,"plan","source",feedback(1));
        var wrong=(com.fasterxml.jackson.databind.node.ObjectNode)row("revision","succeeded","new");wrong.put("store_id","foreign");
        for(JsonNode response:List.of(list(),list(row("another","succeeded","new")),list(wrong),list(row("revision","succeeded","parent")))) {
            when(engine.get(any())).thenReturn(response);second.process(state());assertEquals("REVISING",state().generationStatus);assertEquals(1,state().materials.size());
        }
        verify(engine,times(1)).post(any(),any());verifyNoInteractions(importer);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void aStaleWholeJobResponseCannotImportOrDeclareSuccess(String mode)throws Exception {
        database(mode);first.regenerate(tenant,"plan","source",feedback(1));when(engine.get(contains("/revisions?"))).thenReturn(list(row("revision","succeeded","new")));
        when(engine.get(contains("/generation/jobs/"))).thenReturn(json.readTree("{\"job_id\":\"job\",\"store_id\":\"tenant\",\"ai_plan_task_id\":\"apt\",\"status\":\"succeeded\",\"groups\":[]}"));
        second.process(state());assertEquals("RUNNING",state().generationStatus);assertEquals(1,state().materials.size());assertTrue(state().generationMessage.contains("不采用旧任务"));verifyNoInteractions(importer);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void verifiedNewMaterialWaitsForAnExplicitDecisionAfterImportAndSync(String mode)throws Exception {
        database(mode);first.regenerate(tenant,"plan","source",feedback(1));when(engine.get(contains("/revisions?"))).thenReturn(list(row("revision","succeeded","new")));
        when(engine.get(contains("/generation/jobs/"))).thenReturn(json.readTree("""
            {"job_id":"job","store_id":"tenant","ai_plan_task_id":"apt","status":"succeeded","groups":[{"group_id":"group-1","expected":{"product_cards":1},"materials":[{"material_id":"new","material_type":"product_card","version":"v1","status":"succeeded","content":{"title":"新的商品展示标题"},"urls":{"image_urls":["https://assets.test/new.png"]},"detail":{"parent_material_id":"parent"}}]}]}
            """));
        when(importer.reference(any(),any(),any(),any(),any(),any(),any(),any(),anyBoolean())).thenAnswer(c->c.getArgument(2));
        when(assets.detail(any(),any())).thenReturn(CreationPayloadTest.asset("image","IMAGE","NOT_REQUIRED",null,null,null));
        second.process(state());assertEquals("SUCCEEDED",state().generationStatus);assertEquals(2,state().materials.size());assertEquals("source",state().activeMaterials().getFirst().id);assertEquals("source",state().materials.getLast().reviewSourceId);assertTrue(state().materials.getLast().pendingVersion());assertFalse(state().removedMaterialIds.contains("source"));
        var view=CreationView.of(state());assertEquals(1,view.materials().size());assertEquals("new",view.materialVersions().getFirst().material().id());
        second.process(state());assertEquals(2,state().materials.size());
        second.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(state().revision,"REPLACE"));
        assertEquals(List.of("new"),state().activeMaterials().stream().map(m->m.id).toList());assertTrue(state().removedMaterialIds.contains("source"));
        second.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(1,"REPLACE"));assertEquals(1,state().materials.size());verify(engine,times(1)).post(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void keepBothUsesNoNewGenerationAndSurvivesReload(String mode)throws Exception {
        database(mode);var s=store.mutate("tenant","plan",null,v->{var next=json.convertValue(v.materials.getFirst(),CreationState.Material.class);next.id="new";next.engineMaterialId="new-engine";next.reviewSourceId="source";next.versionDecision="PENDING";v.materials.add(next);v.changed();});
        assertThrows(BizException.class,()->second.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(s.revision-1,"KEEP_BOTH")));
        assertThrows(BizException.class,()->second.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(s.revision,"invalid")));
        var kept=second.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(s.revision,"KEEP_BOTH"));
        assertEquals(2,kept.activeMaterials().size());assertTrue(CreationView.of(otherStore.get("tenant","plan")).materialVersions().isEmpty());assertFalse(kept.removedMaterialIds.contains("source"));verifyNoInteractions(engine);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void failedAlternativeCanBeAbandonedWithoutDeletingTheOriginalOrCallingAModel(String mode)throws Exception {
        database(mode);var snapshot=store.mutate("tenant","plan",null,s->{var m=json.convertValue(s.materials.getFirst(),CreationState.Material.class);m.id="failed";m.reviewSourceId="source";m.versionDecision="PENDING";m.status="FAILED";s.materials.add(m);s.changed();});
        var resolved=second.resolveMaterialVersion(tenant,"plan","failed",new CreationWorkflowService.VersionDecision(snapshot.revision,"DISCARD"));
        assertEquals(List.of("source"),resolved.activeMaterials().stream().map(m->m.id).toList());assertTrue(resolved.removedMaterialIds.contains("failed"));verifyNoInteractions(engine);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void unreadyAndFullPackageCannotKeepBothAndPendingVersionCannotBeRegenerated(String mode)throws Exception {
        database(mode);var s=store.mutate("tenant","plan",null,v->{var next=json.convertValue(v.materials.getFirst(),CreationState.Material.class);next.id="new";next.reviewSourceId="source";next.versionDecision="PENDING";next.status="PROCESSING";v.materials.add(next);v.changed();});
        assertThrows(BizException.class,()->second.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(s.revision,"REPLACE")));
        assertThrows(BizException.class,()->second.regenerate(tenant,"plan","source",feedback(s.revision)));
        var full=store.mutate("tenant","plan",null,v->{v.materials.getLast().status="READY";for(int i=1;i<100;i++){var m=json.convertValue(v.materials.getFirst(),CreationState.Material.class);m.id="keep-"+i;v.materials.add(m);}v.changed();});
        assertThrows(BizException.class,()->second.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(full.revision,"KEEP_BOTH")));assertEquals(100,state().activeMaterials().size());verifyNoInteractions(engine);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void knownRejectionRetainsCooldownAcrossRestartWithoutBlockingOtherPlans(String mode)throws Exception {
        database(mode);when(engine.post(any(),any())).thenThrow(new BizException(8002,"HTTP 422"));assertThrows(BizException.class,()->first.regenerate(tenant,"plan","source",feedback(1)));
        assertEquals("FAILED",state().generationStatus);assertEquals(429,assertThrows(BizException.class,()->second.regenerate(tenant,"plan","source",feedback(state().revision))).code());verify(engine,times(1)).post(any(),any());
        var other=state();other.planId="other";other.regenerationRetryAt=0;RegenerationPolicy.reserve(other,System.currentTimeMillis());assertTrue(other.regenerationRetryAt>0);
        assertThrows(BizException.class,()->otherStore.get("foreign","plan"));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void legacyRevisionWithoutReceiptIsNotRestoredFromTheOldJobId(String mode)throws Exception {
        database(mode);store.mutate("tenant","plan",null,s->{s.generationStatus="REVISING";s.pendingRegenerationId="source";});second.process(state());second.process(state());
        assertEquals("UNCERTAIN",state().generationStatus);assertTrue(state().generationMessage.contains("原素材已保留"));verifyNoInteractions(engine);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void aReservedRequestCanDispatchAfterRestartButARepeatedWorkerSnapshotCannotSendAgain(String mode)throws Exception {
        database(mode);first.regenerate(tenant,"plan","source",feedback(1));
        store.mutate("tenant","plan",null,s->{s.regeneration.status="RESERVED";s.regeneration.revisionId=null;s.regeneration.executionJobId=null;});clearInvocations(engine);
        var reserved=state();first.process(reserved);second.process(reserved);verify(engine,times(1)).post(any(),any());assertEquals("CONFIRMED",state().regeneration.status);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void lateAcceptanceCannotOverwriteAChangedRequestAndMalformedAcceptanceRemainsUncertain(String mode)throws Exception {
        database(mode);when(engine.post(any(),any())).thenAnswer(call->{otherStore.mutate("tenant","plan",null,s->{s.regeneration.requestId="new-owner";s.generationMessage="new request";});return accepted();});
        first.regenerate(tenant,"plan","source",feedback(1));assertNull(state().regeneration.revisionId);assertEquals("new request",state().generationMessage);
        store.mutate("tenant","plan",null,s->{s.generationStatus="FAILED";s.regenerationRetryAt=0;});
        var wrong=(com.fasterxml.jackson.databind.node.ObjectNode)accepted();((com.fasterxml.jackson.databind.node.ObjectNode)wrong.path("revision")).put("store_id","foreign");when(engine.post(any(),any())).thenReturn(wrong);
        first.regenerate(tenant,"plan","source",feedback(state().revision));assertEquals("UNCERTAIN",state().regeneration.status);assertNull(state().regeneration.revisionId);assertEquals("READY",state().materials.getFirst().status);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void anInFlightPostIsNotConfusedWithALostReceiptAndExpiredUnprovableRequestsStopPolling(String mode)throws Exception {
        database(mode);when(engine.post(any(),any())).thenAnswer(call->{second.process(state());assertEquals("SENDING",state().regeneration.status);assertEquals(1,jdbc.queryForObject("SELECT work_pending FROM delivery_creation WHERE plan_id='plan'",Integer.class));return accepted();});
        first.regenerate(tenant,"plan","source",feedback(1));assertEquals("CONFIRMED",state().regeneration.status);
        store.mutate("tenant","plan",null,s->{s.regeneration.status="SENDING";s.regeneration.revisionId=null;s.regeneration.executionJobId=null;s.regeneration.sentAt=System.currentTimeMillis()-301_000;});
        second.process(state());assertEquals("UNCERTAIN",state().regeneration.status);assertEquals(0,jdbc.queryForObject("SELECT work_pending FROM delivery_creation WHERE plan_id='plan'",Integer.class));verify(engine,times(1)).post(any(),any());verify(engine,never()).get(any());
    }
}
