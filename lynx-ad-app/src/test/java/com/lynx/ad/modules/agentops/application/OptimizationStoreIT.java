package com.lynx.ad.modules.agentops.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationStore;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationAutomationStore;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Creates and drops only its freshly named verification database, never the application DB. */
@EnabledIfEnvironmentVariable(named="LYNXAD_OPT_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/lynxad_optimization_verify_[a-zA-Z0-9_]+\\?.*")
class OptimizationStoreIT {
    static JdbcTemplate jdbc;static OptimizationStore store;static String database,server,user,password;static boolean created;
    static final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final TenantId a=new TenantId("tenant-a"),b=new TenantId("tenant-b");final PlanId p=new PlanId("plan-a");
    @BeforeAll static void setup() throws Exception {
        String url=System.getenv("LYNXAD_OPT_TEST_URL");database=url.substring(url.indexOf("/lynxad_")+1,url.indexOf('?'));
        if(!database.matches("lynxad_optimization_verify_[a-zA-Z0-9_]+"))throw new IllegalArgumentException("unsafe verification schema");
        server=url.replace("/"+database+"?","/?");user=System.getenv("LYNXAD_OPT_TEST_USER");password=System.getenv("LYNXAD_OPT_TEST_PASSWORD");
        try(var c=DriverManager.getConnection(server,user,password);var s=c.createStatement()) {s.execute("CREATE DATABASE "+database);created=true;}
        var ds=new DriverManagerDataSource(url,user,password);jdbc=new JdbcTemplate(ds);store=new OptimizationStore(jdbc,json,new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE sys_tenant(id VARCHAR(64) PRIMARY KEY)");jdbc.update("INSERT INTO sys_tenant VALUES('tenant-a'),('tenant-b')");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V28__optimization_monitor.sql"));ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V29__optimization_execution.sql"));ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V30__optimization_creative.sql"));}
        jdbc.execute("CREATE TABLE notification(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(64),channel VARCHAR(20),title VARCHAR(128),body TEXT,biz_type VARCHAR(32),biz_id VARCHAR(64),read_at TIMESTAMP NULL,sent_at TIMESTAMP NULL)");
    }
    @AfterAll static void cleanup() throws Exception {if(created && database.matches("lynxad_optimization_verify_[a-zA-Z0-9_]+"))try(var c=DriverManager.getConnection(server,user,password);var s=c.createStatement()){s.execute("DROP DATABASE "+database);}}
    @BeforeEach void clear() {jdbc.update("DELETE FROM optimization_creative_job");jdbc.update("DELETE FROM optimization_outbox");jdbc.update("DELETE FROM optimization_policy");jdbc.update("DELETE FROM product_economics_profile");jdbc.update("DELETE FROM optimization_event");jdbc.update("DELETE FROM optimization_account_guard");jdbc.update("DELETE FROM notification");}
    AdPlan plan(TenantId tenant,String id) {var p=mock(AdPlan.class);when(p.tenantId()).thenReturn(tenant);when(p.id()).thenReturn(new PlanId(id));when(p.accountId()).thenReturn(new AccountId("account"));when(p.awemeId()).thenReturn(new AwemeId("aweme"));return p;}
    OptimizationPolicy policy(boolean enabled) {return new OptimizationPolicy(enabled,"OBSERVE",BigDecimal.valueOf(500),BigDecimal.valueOf(200),BigDecimal.valueOf(100),3,3,60,List.of(new OptimizationPolicy.Economics("item",new BigDecimal(".4"),new BigDecimal(".1"),true)));}
    @Test void tenantScopedConfigurationPersistsAcrossServiceRestart() {
        store.save(plan(a,p.value()),policy(true));assertTrue(store.get(a,p).orElseThrow().enabled());assertTrue(store.get(b,p).isEmpty());
        var restarted=new OptimizationStore(jdbc,json,new DataSourceTransactionManager(jdbc.getDataSource()));assertEquals("OBSERVE",restarted.get(a,p).orElseThrow().policy().mode());assertEquals(new BigDecimal("0.40000000"),restarted.economics(plan(a,"other")).getFirst().contributionMarginRate());assertTrue(restarted.economics(plan(b,"other")).isEmpty());
    }
    @Test void exactlyOneReplicaCanClaimTheSamePolicy() throws Exception {
        store.save(plan(a,p.value()),policy(true));var state=store.get(a,p).orElseThrow();var executor=Executors.newFixedThreadPool(5);
        try {var start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();for(int i=0;i<5;i++){String owner="worker-"+i;futures.add(executor.submit(()->{start.await();return store.claim(state,owner,Instant.now());}));}start.countDown();int wins=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS))wins++;assertEquals(1,wins);}finally{executor.shutdownNow();}
    }
    @Test void outboxSurvivesFailedDeliveryAndCompletionIsAtomic() throws Exception {
        store.save(plan(a,p.value()),policy(true));var state=store.get(a,p).orElseThrow();assertTrue(store.claim(state,"worker",Instant.now()));
        var pending=store.enqueue(state,"worker",Map.of("request_id","request-1","store_id",a.value()));store.release(state,"worker","network timeout");
        assertEquals(pending.id(),store.pending(state).orElseThrow().id());assertEquals("network timeout",store.get(a,p).orElseThrow().error());
        assertTrue(store.claim(state,"worker-2",Instant.now()));store.complete(state,"worker-2",pending,json.readTree("{\"state\":\"WAITING_DATA\"}"));store.release(state,"worker-2",null);
        assertTrue(store.pending(state).isEmpty());var saved=store.get(a,p).orElseThrow();assertNotNull(saved.lastSuccess());assertNull(saved.error());assertEquals("WAITING_DATA",saved.result().path("state").asText());
    }
    @Test void expiredLeaseCannotOverwriteAnotherWorkersResult() {
        store.save(plan(a,p.value()),policy(true));var s=store.get(a,p).orElseThrow();store.claim(s,"expired",Instant.now().minusSeconds(181));assertTrue(store.claim(s,"current",Instant.now()));
        assertThrows(BizException.class,()->store.enqueue(s,"expired",Map.of("request_id","wrong")));assertTrue(store.pending(s).isEmpty());
    }
    @Test void sharedProductEditsInvalidateBothModeVariantsButNotOtherTenant() {
        store.save(plan(a,"cost"),policy(true));store.save(plan(a,"volume"),policy(true));store.save(plan(b,"other"),policy(true));
        var cost=store.get(a,new PlanId("cost")).orElseThrow();var volume=store.get(a,new PlanId("volume")).orElseThrow();var other=store.get(b,new PlanId("other")).orElseThrow();
        store.claim(cost,"worker",Instant.now());store.enqueue(cost,"worker",Map.of("request_id","pending"));store.release(cost,"worker",null);
        store.save(plan(a,"volume"),policy(false));assertTrue(store.get(a,new PlanId("cost")).orElseThrow().version()>cost.version());assertTrue(store.get(a,new PlanId("volume")).orElseThrow().version()>volume.version());assertEquals(other.version(),store.get(b,new PlanId("other")).orElseThrow().version());assertTrue(store.pending(cost).isEmpty());
    }
    @Test void editsFenceActiveSamplingAndDisabledPoliciesAreNotClaimed() {
        store.save(plan(a,p.value()),policy(true));var s=store.get(a,p).orElseThrow();store.claim(s,"worker",Instant.now());store.save(plan(a,p.value()),policy(false));
        assertThrows(BizException.class,()->store.enqueue(s,"worker",Map.of("request_id","old")));store.release(s,"worker","old failure");
        assertNull(store.get(a,p).orElseThrow().error());assertFalse(store.claim(store.get(a,p).orElseThrow(),"off",Instant.now()));
    }
    @Test void staleSettingsPageCannotOverwriteNewerProfileOrPolicy() {
        store.save(plan(a,p.value()),policy(true),0);var current=store.get(a,p).orElseThrow();
        assertThrows(BizException.class,()->store.save(plan(a,p.value()),policy(false),0));assertEquals(current.version(),store.get(a,p).orElseThrow().version());assertTrue(store.get(a,p).orElseThrow().enabled());
    }
    @Test void deletedPlanStopsSamplingAndCancelsItsPendingDelivery() {
        store.save(plan(a,p.value()),policy(true));var state=store.get(a,p).orElseThrow();
        assertTrue(store.claim(state,"worker",Instant.now()));store.enqueue(state,"worker",Map.of("request_id","deleted-plan"));
        store.disableMissing(state,"another-worker");assertTrue(store.get(a,p).orElseThrow().enabled());assertTrue(store.pending(state).isPresent());
        store.disableMissing(state,"worker");store.release(state,"worker",null);
        assertFalse(store.get(a,p).orElseThrow().enabled());assertTrue(store.pending(state).isEmpty());assertTrue(store.due(Instant.now().plusSeconds(3600)).isEmpty());
    }
    OptimizationAutomationStore journal(){var repo=mock(AdPlanRepository.class);var plan=plan(a,p.value());when(plan.name()).thenReturn(new PlanName("测试计划"));when(repo.findById(a,p)).thenReturn(Optional.of(plan));return new OptimizationAutomationStore(jdbc,json,new DataSourceTransactionManager(jdbc.getDataSource()),repo);}
    com.fasterxml.jackson.databind.JsonNode advice()throws Exception{return json.readTree("{\"observed_at\":\"2026-10-12T12:30:00+08:00\",\"window_end\":\"2026-10-12T11:00:00+08:00\",\"rule_version\":\"profit-control-v2\",\"recommendations\":[{\"type\":\"BUDGET_INCREASE_REVIEW\",\"title\":\"扩量建议\",\"evidence\":\"连续盈利\",\"execution_allowed\":true,\"action\":{\"kind\":\"BUDGET\",\"expected\":\"400\",\"target\":\"440\",\"cause\":\"PERSISTENT_PROFIT\"}}]}");}
    OptimizationStore.State automatic(){var observe=policy(true);var config=new OptimizationPolicy(true,"AUTO_LIMITED",observe.dailySpendLimit(),observe.maxLoss(),observe.minSpend(),3,3,60,observe.economics());store.save(plan(a,p.value()),config);return store.get(a,p).orElseThrow();}
    @Test void repeatedAdviceCreatesOneHistoryAndOneNotification()throws Exception{var j=journal();var s=automatic();j.recordAdvice(s,advice());j.recordAdvice(s,advice());assertEquals(1,j.history(a.value(),null,null,null,null,null,1,20).total());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));assertEquals("READY",j.pending().getFirst().status());assertEquals(0,j.history(b.value(),null,null,null,null,null,1,20).total());}
    @Test void journalDispatchIsAtMostOnceAndTenantScoped()throws Exception{var j=journal();j.recordAdvice(automatic(),advice());var e=j.pending().getFirst();assertTrue(j.event(b.value(),e.id()).isEmpty());assertFalse(j.dispatch(e,"wrong-owner"));assertTrue(j.claimAccount(a.value(),"account","owner"));assertTrue(j.dispatch(e,"owner"));assertFalse(j.dispatch(e,"owner"));assertTrue(j.unresolved(a.value(),"account"));assertFalse(j.unresolved(b.value(),"account"));assertEquals("EXECUTING",j.event(a.value(),e.id()).orElseThrow().status());}
    @Test void accountLeaseAndLimitPersistAcrossRestart(){var j=journal();assertTrue(j.claimAccount(a.value(),"account","first"));assertNull(j.limit(a.value(),"account"));assertFalse(j.claimAccount(a.value(),"account","second"));assertThrows(BizException.class,()->j.setLimit(a.value(),"account",new BigDecimal("1000")));j.releaseAccount(a.value(),"account","wrong");assertFalse(j.claimAccount(a.value(),"account","second"));j.releaseAccount(a.value(),"account","first");j.setLimit(a.value(),"account",new BigDecimal("1000"));assertEquals(new BigDecimal("1000.00"),journal().limit(a.value(),"account"));assertNull(j.limit(b.value(),"account"));}
    @Test void manualTakeoverCancelsReadyButPreservesDispatchedReceipts()throws Exception{var j=journal();var s=automatic();j.recordAdvice(s,advice());j.hold(a.value(),List.of(p.value()),"manual");assertTrue(j.pending().isEmpty());assertEquals("SKIPPED",j.history(a.value(),null,null,null,null,null,1,20).records().getFirst().status());assertTrue(store.get(a,p).orElseThrow().version()>s.version());assertEquals("manual",store.get(a,p).orElseThrow().holdReason());}
    @Test void uncertainWriteRequiresAcknowledgmentAndDoesNotReplay()throws Exception{var j=journal();j.recordAdvice(automatic(),advice());var e=j.pending().getFirst();j.claimAccount(a.value(),"account","owner");assertTrue(j.dispatch(e,"owner"));j.finish(e,"VERIFY_PENDING","network",null);assertTrue(j.unresolved(a.value(),"account"));j.finish(e,"UNKNOWN","uncertain",null);assertTrue(j.pending().isEmpty());assertTrue(j.unresolved(a.value(),"account"));j.finish(e,"ACKNOWLEDGED","user accepted",json.readTree("{\"budget\":\"400\"}"));assertFalse(j.unresolved(a.value(),"account"));assertFalse(j.dispatch(e,"owner"));}
    @Test void confirmedSuccessSetsBaselineAndNotifiesOnce()throws Exception{var j=journal();var s=automatic();j.recordAdvice(s,advice());var e=j.pending().getFirst();j.claimAccount(a.value(),"account","owner");j.dispatch(e,"owner");var facts=json.readTree("{\"budget\":\"440\"}");j.succeeded(e,facts);j.succeeded(e,facts);assertEquals(facts,store.get(a,p).orElseThrow().baseline());assertNotNull(store.get(a,p).orElseThrow().lastAction());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));assertEquals(1,j.actionsToday(a.value(),p.value()));}
    @Test void taskIntentAndReservedExtraBudgetSurviveRestart()throws Exception{var j=journal();var report=advice();var action=(com.fasterxml.jackson.databind.node.ObjectNode)report.path("recommendations").get(0).path("action");action.remove(List.of("expected","target"));action.put("kind","BOOST").put("budget","100").put("duration","1");j.recordAdvice(automatic(),report);var e=j.pending().getFirst();assertEquals(BigDecimal.ZERO,j.extraBudgetToday(a.value(),p.value()));j.claimAccount(a.value(),"account","owner");assertTrue(j.dispatch(e,"owner"));j.intent(e,json.createObjectNode().put("taskName","stable-task"));var saved=journal().event(a.value(),e.id()).orElseThrow();assertEquals("stable-task",saved.actual().path("operation").path("taskName").asText());assertEquals(new BigDecimal("100"),journal().extraBudgetToday(a.value(),p.value()));j.finish(e,"UNKNOWN","network",saved.actual());assertEquals(new BigDecimal("100"),journal().extraBudgetToday(a.value(),p.value()));assertEquals(BigDecimal.ZERO,j.extraBudgetToday(b.value(),p.value()));assertThrows(BizException.class,()->j.intent(e,json.createObjectNode()));}
    @Test void operationReceiptStaysInHistoryButNotAuthorizationBaseline()throws Exception{var j=journal();j.recordAdvice(automatic(),advice());var e=j.pending().getFirst();j.claimAccount(a.value(),"account","owner");j.dispatch(e,"owner");var facts=json.createObjectNode().put("budget","440").put("actionConfirmed",true);facts.putObject("operation").put("taskName","stable-task");j.succeeded(e,facts);assertTrue(j.event(a.value(),e.id()).orElseThrow().actual().has("operation"));assertFalse(store.get(a,p).orElseThrow().baseline().has("operation"));assertFalse(store.get(a,p).orElseThrow().baseline().has("actionConfirmed"));}
    com.lynx.ad.modules.agentops.infrastructure.OptimizationCreativeStore creatives(){return new com.lynx.ad.modules.agentops.infrastructure.OptimizationCreativeStore(jdbc,json,new DataSourceTransactionManager(jdbc.getDataSource()));}
    OptimizationStore.State creativePolicy(){var base=policy(true);var g=OptimizationPolicy.CreativeGeneration.defaults();var enabled=new OptimizationPolicy.CreativeGeneration(true,false,g.formats(),2,2,g.dailyCostLimit(),g.perAssetCostLimit(),g.trialLossLimit(),1000,50,"","","user");var config=new OptimizationPolicy(true,"AUTO_LIMITED",base.dailySpendLimit(),base.maxLoss(),base.minSpend(),3,3,60,base.economics(),base.automation(),base.tactics(),enabled);store.save(plan(a,p.value()),config);return store.get(a,p).orElseThrow();}
    com.fasterxml.jackson.databind.JsonNode generationAdvice()throws Exception{var raw=advice();var action=(com.fasterxml.jackson.databind.node.ObjectNode)raw.path("recommendations").get(0).path("action");action.removeAll();action.put("kind","MATERIAL_GENERATE").put("cause","CREATIVE_DIAGNOSIS").put("product_id","item");action.putObject("strategy").put("reserved_cost","30").put("hypothesis","验证真实使用需求");return raw;}
    @Test void creativeReservationSurvivesRestartAndCannotDuplicateOrCrossTenant()throws Exception{
        var j=journal();var s=creativePolicy();j.recordAdvice(s,generationAdvice());var e=j.pending().getFirst();assertTrue(j.claimAccount(a.value(),"account","owner"));
        var body=json.createObjectNode().set("strategy",e.proposal().path("strategy"));var job=creatives().reserve(e,"logical-root",s.policy(),body,"owner");
        assertEquals(job.id(),creatives().reserve(e,"logical-root",s.policy(),body,"owner").id());assertEquals("GENERATING",j.event(a.value(),e.id()).orElseThrow().status());assertTrue(creatives().get(b.value(),job.id()).isEmpty());
        assertEquals("30.00",creatives().context(a.value(),"logical-root").path("reserved_today").asText());assertEquals(1,creatives().context(a.value(),"logical-root").path("pending").path("item").asInt());
        assertEquals(1,creatives().history(a.value(),p.value()).size());assertFalse(creatives().claim(job,"first")&&creatives().claim(job,"second"));
    }
    @Test void creativePublicationAndReceiptTransitionCommitTogether()throws Exception{
        var j=journal();var s=creativePolicy();j.recordAdvice(s,generationAdvice());var e=j.pending().getFirst();j.claimAccount(a.value(),"account","owner");var c=creatives();var job=c.reserve(e,"logical-root",s.policy(),json.createObjectNode().set("strategy",e.proposal().path("strategy")),"owner");
        c.update(job,"SYNCING",job.body());job=c.get(a.value(),job.id()).orElseThrow();var action=json.createObjectNode().put("kind","MATERIAL_ADD").put("cause","NEW_MATERIAL_TRIAL").put("generation_id",job.id()).put("product_id","item");String child=c.enqueue(job,action,advice());
        var published=creatives().get(a.value(),job.id()).orElseThrow();assertEquals("PUBLISHING",published.status());assertEquals(child,published.body().path("publication_event_id").asText());assertEquals("READY",journal().event(a.value(),child).orElseThrow().status());assertEquals(child,c.enqueue(published,action,advice()));
        c.update(job,"FAILED",job.body());assertEquals("PUBLISHING",c.get(a.value(),job.id()).orElseThrow().status());assertEquals("PUBLISHING",j.event(a.value(),job.id()).orElseThrow().status());
    }
    @Test void versionRevocationBlocksPublicationAnd24HourCapBlocksAnotherReservation()throws Exception{
        var j=journal();var s=creativePolicy();var raw=generationAdvice();j.recordAdvice(s,raw);var e=j.pending().getFirst();j.claimAccount(a.value(),"account","owner");var c=creatives();var body=json.createObjectNode().set("strategy",e.proposal().path("strategy"));var job=c.reserve(e,"logical-root",s.policy(),body,"owner");
        ((com.fasterxml.jackson.databind.node.ObjectNode)raw).put("window_end","2026-10-12T12:00:00+08:00");j.recordAdvice(s,raw);var second=j.pending().getFirst();assertThrows(BizException.class,()->c.reserve(second,"logical-root",s.policy(),body,"owner"));
        store.save(plan(a,p.value()),policy(false));var action=json.createObjectNode().put("kind","MATERIAL_ADD");assertThrows(BizException.class,()->c.enqueue(job,action,json.createObjectNode()));assertFalse(j.unresolved(a.value(),"account"));
    }
    @Test void sagaUsesOneEngineRequestWaitsForReviewAndTracksPublicationAndMatureTrial()throws Exception{
        var j=journal();var s=creativePolicy();var raw=generationAdvice();var strategy=(com.fasterxml.jackson.databind.node.ObjectNode)raw.path("recommendations").get(0).path("action").path("strategy");strategy.put("product_id","item").put("target","video").put("count",1);j.recordAdvice(s,raw);var e=j.pending().getFirst();j.claimAccount(a.value(),"account","owner");
        var body=json.createObjectNode();body.set("strategy",strategy);body.set("request",json.createObjectNode().put("request_id",e.id()));var c=creatives();var job=c.reserve(e,"logical-root",s.policy(),body,"owner");j.releaseAccount(a.value(),"account","owner");
        var gateway=mock(com.lynx.ad.modules.agentops.infrastructure.OptimizationExecutionGateway.class);var tactics=mock(com.lynx.ad.modules.agentops.infrastructure.OptimizationTacticsGateway.class);var monitoring=mock(com.lynx.ad.modules.agentops.infrastructure.MonitoringEngineClient.class);var engine=mock(com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient.class);var workflow=mock(com.lynx.ad.modules.delivery.application.CreationWorkflowService.class);var publishedPlan=plan(a,p.value());
        when(gateway.plan(a.value(),p.value())).thenReturn(publishedPlan);when(gateway.tactics()).thenReturn(tactics);when(tactics.logicalRoot(publishedPlan)).thenReturn("logical-root");when(tactics.generationState(eq(publishedPlan),any(),eq("item"))).thenReturn(new com.lynx.ad.modules.delivery.domain.model.CreationState());
        when(publishedPlan.name()).thenReturn(new PlanName("测试计划"));when(gateway.facts(publishedPlan)).thenReturn(json.createObjectNode().put("qcId","777").put("status","DELIVERING").put("bidType","CUSTOM").put("budget","400"));
        var receipt=json.createObjectNode().put("request_id",job.id()).put("job_id","engine-job");when(monitoring.creative(a.value(),"",body.path("request"))).thenReturn(receipt);
        when(monitoring.creative(a.value(),"/"+job.id(),null)).thenReturn(null);
        when(engine.get(anyString())).thenReturn(json.readTree("{\"job_id\":\"engine-job\",\"status\":\"succeeded\",\"groups\":[{\"group_id\":\"group-1\",\"materials\":[{\"version\":\"v1\",\"status\":\"succeeded\",\"material_id\":\"generated\",\"material_type\":\"video\",\"content\":{\"title\":\"真实商品的使用细节\"},\"urls\":{\"video_url\":\"https://example.invalid/v.mp4\",\"cover_url\":\"https://example.invalid/c.png\"}}]}]}"));
        var service=new OptimizationCreativeService(store,j,c,gateway,monitoring,engine,workflow,json);service.scan();assertEquals("REVIEW",c.get(a.value(),job.id()).orElseThrow().status());verify(workflow,never()).importOptimizationMaterial(any(),any());verify(monitoring,times(1)).creative(eq(a.value()),eq(""),any());
        service.approve(a.value(),p.value(),job.id(),true);when(workflow.importOptimizationMaterial(any(),any())).thenAnswer(call->{var m=call.getArgument(1,com.lynx.ad.modules.delivery.domain.model.CreationState.Material.class);m.status="READY";return m;});
        var observed=ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));var report=json.createObjectNode().put("state","READY").put("observed_at",observed.toOffsetDateTime().toString()).put("window_end",observed.minusMinutes(60).withMinute(0).withSecond(0).withNano(0).toOffsetDateTime().toString()).put("window_count",3).put("store_id",a.value()).put("plan_id","777").put("account_id","account").put("estimated_contribution_profit","132").put("spend","300").put("orders","15");
        jdbc.update("UPDATE optimization_policy SET result_json=? WHERE tenant_id=? AND plan_id=?",json.writeValueAsString(report),a.value(),p.value());jdbc.update("UPDATE optimization_creative_job SET lease_until=NULL WHERE id=?",job.id());service.scan();var submitting=c.get(a.value(),job.id()).orElseThrow();assertEquals("PUBLISHING",submitting.status());var child=j.event(a.value(),submitting.body().path("publication_event_id").asText()).orElseThrow();assertEquals("NEW_MATERIAL_TRIAL",child.proposal().path("cause").asText());
        j.claimAccount(a.value(),"account","execution");assertTrue(j.dispatch(child,"execution"));var actual=json.createObjectNode().put("budget","400");actual.putObject("operation").put("objectKey","VIDEO:new-video");j.succeeded(child,actual);j.releaseAccount(a.value(),"account","execution");jdbc.update("UPDATE optimization_creative_job SET lease_until=NULL WHERE id=?",job.id());service.scan();assertEquals("PLATFORM_REVIEW",c.get(a.value(),job.id()).orElseThrow().status());assertNotNull(c.get(a.value(),job.id()).orElseThrow().publishedAt());
        var trial=json.createObjectNode().put("bound",true).put("audit","PASS").put("complete",true);var days=trial.putArray("days");for(int i=0;i<2;i++)days.addObject().putObject("metrics").put("spend","100").put("net_revenue","1000").put("orders","5").put("impressions","2000").put("clicks","100");when(tactics.trial(eq(publishedPlan),any(),any())).thenReturn(trial);
        jdbc.update("UPDATE optimization_creative_job SET lease_until=NULL WHERE id=?",job.id());service.scan();assertEquals("QUALIFIED",c.get(a.value(),job.id()).orElseThrow().status());assertEquals(0,new BigDecimal(c.get(a.value(),job.id()).orElseThrow().body().path("trial_summary").path("profit").asText()).compareTo(new BigDecimal("490")));
        verify(monitoring,times(1)).creative(eq(a.value()),eq(""),any());verify(gateway,never()).write(any(),any());
    }
}
