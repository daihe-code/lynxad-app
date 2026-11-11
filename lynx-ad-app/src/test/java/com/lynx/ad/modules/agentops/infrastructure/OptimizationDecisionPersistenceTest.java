package com.lynx.ad.modules.agentops.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.application.*;
import com.lynx.ad.modules.delivery.application.PlanInsightsService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class OptimizationDecisionPersistenceTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    JdbcTemplate jdbc;OptimizationStore store;OptimizationAutomationStore journal;OptimizationConfirmationStore confirmations;OptimizationReviewStore reviews;OptimizationStore.State state;AdPlan plan;
    @BeforeEach void setup()throws Exception{
        var ds=new DriverManagerDataSource("jdbc:h2:mem:monitor_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa","");jdbc=new JdbcTemplate(ds);var tx=new DataSourceTransactionManager(ds);
        try(var c=ds.getConnection()){for(String migration:List.of("V28__optimization_monitor.sql","V29__optimization_execution.sql","V50__optimization_rule_details.sql","V51__optimization_decision_reviews.sql","V55__optimization_rule_lifecycle.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/"+migration));}
        jdbc.execute("CREATE TABLE sys_tenant(id VARCHAR(64) PRIMARY KEY)");jdbc.update("INSERT INTO sys_tenant VALUES('tenant')");jdbc.execute("CREATE TABLE notification(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(64),channel VARCHAR(20),title VARCHAR(255),body TEXT,biz_type VARCHAR(64),biz_id VARCHAR(64),sent_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE agent_action(tenant_id VARCHAR(64),ad_plan_id VARCHAR(64),action_type VARCHAR(32),result VARCHAR(16),snapshot_ts TIMESTAMP(3))");
        var p=mock(AdPlan.class);plan=p;when(p.tenantId()).thenReturn(new TenantId("tenant"));when(p.id()).thenReturn(new PlanId("plan"));when(p.accountId()).thenReturn(new AccountId("account"));when(p.awemeId()).thenReturn(new AwemeId("aweme"));when(p.name()).thenReturn(new PlanName("测试计划"));when(p.qcPlanId()).thenReturn(Optional.of(new QcPlanId("123")));when(p.status()).thenReturn(PlanStatus.DELIVERING);
        var repo=mock(AdPlanRepository.class);when(repo.findById(new TenantId("tenant"),new PlanId("plan"))).thenReturn(Optional.of(p));store=new OptimizationStore(jdbc,json,tx);journal=new OptimizationAutomationStore(jdbc,json,tx,repo);confirmations=new OptimizationConfirmationStore(jdbc,json,tx);reviews=new OptimizationReviewStore(jdbc,json);
        var d=OptimizationPolicy.defaults();var rules=new OptimizationRules(BigDecimal.ONE,new BigDecimal("400"),new BigDecimal("480"),"CONFIRM",5,new BigDecimal("100"),new BigDecimal("100"),new BigDecimal("200"));
        store.save(p,new OptimizationPolicy(true,"OBSERVE",d.dailySpendLimit(),d.maxLoss(),d.minSpend(),3,3,60,List.of(),null,null,null,rules),0);state=store.get(new TenantId("tenant"),new PlanId("plan")).orElseThrow();
    }
    ObjectNode report(String object){var n=json.createObjectNode().put("rule_version","merchant-rules-v2").put("complete",true).put("business_ready",true).put("diagnostics_ready",true);var advice=n.putArray("recommendations").addObject().put("type","GROWTH_BUDGET").put("object_id",object).put("title","预算方案").put("evidence","平台预算限制已核实").put("requires_confirmation",true).put("execution_allowed",false);advice.putObject("action").put("kind","BUDGET").put("expected","300").put("target","400").put("cause","MERCHANT_GROWTH");return n;}
    OptimizationAutomationStore.Event create(String object){journal.recordAdvice(state,report(object));return journal.ruleActions("tenant","plan",state.version()).stream().filter(e->e.report().path("recommendations").get(0).path("object_id").asText().equals(object)).findFirst().orElseThrow();}
    void approve(OptimizationAutomationStore.Event e,BigDecimal amount){confirmations.approve(e,"user",json.createObjectNode(),e.proposal(),e.report(),amount,new BigDecimal("200"),"owner");}
    OptimizationPolicy policy(boolean enabled){var p=state.policy();return new OptimizationPolicy(enabled,p.mode(),p.dailySpendLimit(),p.maxLoss(),p.minSpend(),p.minOrders(),p.persistenceWindows(),p.conversionDelayMinutes(),p.economics(),p.automation(),p.tactics(),p.creativeGeneration(),p.rules());}
    @Test void ruleLifecyclePersistsOneInboxMessageWithTheSavedVersion(){
        store.automationStore(journal);store.save(plan,policy(false),1);
        assertEquals("盯盘优化已暂停",jdbc.queryForObject("SELECT title FROM notification",String.class));
        assertThrows(BizException.class,()->store.save(plan,policy(true),1));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));
        assertThrows(BizException.class,()->store.save(plan,policy(true),2));
        store.resumeRules(plan,store.get(plan.tenantId(),plan.id()).orElseThrow(),json.createObjectNode(),json.createObjectNode(),"测试操作人");
        store.save(plan,policy(true),3);
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE tenant_id='tenant' AND biz_id='plan' AND biz_type='OPTIMIZATION_RULES' AND channel='INBOX'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE title='盯盘优化已恢复' AND body LIKE '%ROI%400%480%确认暂停%'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE title='盯盘规则已更新'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE tenant_id='other'",Integer.class));
    }

    @Test void firstRuleStartIsIndependentOfEditsResumesAndTheHistoryWindow() {
        var first = Instant.now().minus(Duration.ofDays(200)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        journal.ruleChanged(plan, policy(false), null, null, 0, null, "operator", first.minusSeconds(60));
        journal.ruleChanged(plan, policy(true), state, null, 0, null, "operator", first.minusSeconds(30));
        assertNull(journal.firstRuleEnabledAt("tenant", "plan"));

        journal.ruleChanged(plan, policy(true), null, null, 0, null, "operator", first);
        journal.ruleChanged(plan, policy(true), state, null, 0, json.createObjectNode(), "operator", Instant.now());
        assertEquals(1, journal.ruleHistory("tenant", "plan", "RULES", 7, 1, 20).total());
        assertEquals(first, journal.firstRuleEnabledAt("tenant", "plan"));
        assertNull(journal.firstRuleEnabledAt("other", "plan"));
        assertNull(journal.firstRuleEnabledAt("tenant", "another-plan"));
    }

    @Test void firstRuleStartReadsHistoricalActivationRecordedAsAnUpdate() {
        var first = Instant.parse("2026-11-05T02:27:38Z");
        journal.ruleChanged(plan, policy(true), null, null, 0, null, "operator", first);
        jdbc.update("UPDATE optimization_event SET title=?, report_json=? WHERE kind='RULES_CHANGE'",
            "盯盘规则已更新", json.createObjectNode().put("enabled", true).toString());
        journal.ruleChanged(plan, policy(true), null, null, 0, null, "operator", first.plusSeconds(60));
        assertEquals(first, journal.firstRuleEnabledAt("tenant", "plan"));
    }

    @Test void zeroDeliveryAdviceFitsTheEventSchemaAndNotifiesOnce() {
        var report = json.createObjectNode().put("rule_version","merchant-rules-v2").put("complete",true);
        report.putObject("delivery_check").put("activity","ZERO");
        report.putArray("recommendations").addObject().put("type","DELIVERY_NO_ACTIVITY").put("title","持续无曝光")
            .put("evidence","可补充不同卖点的素材");
        journal.recordAdvice(state,report);
        journal.recordAdvice(state,report);
        assertEquals(1,journal.ruleHistory("tenant","plan","RISK",7,1,20).total());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE body LIKE '%可补充不同卖点的素材%'",Integer.class));
        report.putArray("recommendations");
        ((ObjectNode)report.path("delivery_check")).put("activity","UNKNOWN");
        journal.recordAdvice(state,report);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM optimization_event WHERE status='RECOMMENDED'",Integer.class));
        ((ObjectNode)report.path("delivery_check")).put("activity","ACTIVE");
        journal.recordAdvice(state,report);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM optimization_event WHERE status='RESOLVED'",Integer.class));
    }

    @Test void pausedEditsAndLaterVerificationDoNotChangeTheSnapshot(){
        store.automationStore(journal);
        store.ruleMetrics(new OptimizationRuleMetricsStore(jdbc));
        var observed=Instant.now();
        jdbc.update("INSERT INTO optimization_rule_day VALUES(?,?,?,?,?,?)","tenant","plan","2026-11-06",5400,Timestamp.from(observed),true);
        jdbc.update("UPDATE optimization_policy SET result_json=?",
            json.createObjectNode().put("daily_spend","320.00").put("observed_at",observed.toString()).toString());
        var event=create("one");
        jdbc.update("UPDATE optimization_event SET status='VERIFY_PENDING' WHERE id=?",event.id());
        var advice=create("two");
        store.save(plan,policy(false),1,"测试操作人");
        assertEquals("RESOLVED",journal.event("tenant",advice.id()).orElseThrow().status());
        assertEquals("VERIFY_PENDING",journal.event("tenant",event.id()).orElseThrow().status());
        jdbc.update("UPDATE optimization_event SET status='SUCCEEDED' WHERE id=?",event.id());
        store.save(plan,policy(false),2);
        var pause=journal.lastRulePause("tenant","plan",3).orElseThrow();
        assertEquals(5400,pause.snapshot().effectiveSeconds());
        assertEquals("320.00",pause.snapshot().dailySpend().asText());
        assertEquals(observed.toString(),pause.snapshot().dataAt());
        assertEquals(0,pause.snapshot().summary().completed());
        assertEquals(1,journal.ruleSummary("tenant","plan",false).completed());
        var history=journal.ruleHistory("tenant","plan","RULES",1,1,20);
        assertEquals(List.of("EDIT","ADVICE_EXPIRED","PAUSE"),history.records().stream()
            .map(e->e.report().path("activity").asText()).toList());
        assertEquals(5,history.records().getFirst().report().path("previous_rules").path("runawayMinutes").asInt());
        assertEquals(1,history.records().get(1).report().path("expired_count").asInt());
        assertEquals(1,history.records().get(2).report().path("pending_count").asInt());
        assertEquals("测试操作人",history.records().get(2).report().path("operator").asText());
        assertEquals(0,journal.ruleHistory("other","plan","RULES",1,1,20).total());
        assertTrue(journal.lastRulePause("other","plan",3).isEmpty());
        assertFalse(store.get(plan.tenantId(),plan.id()).orElseThrow().enabled());
    }
    @Test void resumeRechecksSpendAndRequiresConfirmationForTheNewOutcome(){
        store.automationStore(journal);
        var rules=state.policy().rules();
        var automatic=new OptimizationRules(rules.roiFloor(),rules.spendReminder(),rules.spendProtection(),
            OptimizationRules.AUTO_PAUSE,rules.runawayMinutes(),rules.runawaySpend(),rules.maxAddition(),rules.dailyAddition());
        var policy=state.policy();
        store.save(plan,new OptimizationPolicy(false,policy.mode(),policy.dailySpendLimit(),policy.maxLoss(),
            policy.minSpend(),policy.minOrders(),policy.persistenceWindows(),policy.conversionDelayMinutes(),
            List.of(),null,null,null,automatic),1);
        var gateway=mock(OptimizationExecutionGateway.class);
        var insights=mock(PlanInsightsService.class);
        var execution=new OptimizationExecutionService(store,journal,gateway,insights);
        when(plan.status()).thenReturn(PlanStatus.DELIVERING);
        when(gateway.plan("tenant","plan")).thenReturn(plan);
        when(gateway.ready()).thenReturn(true);
        var platformStatus=new java.util.concurrent.atomic.AtomicReference<>("DELIVERING");
        when(gateway.readPlanFacts(plan,false)).thenAnswer(call->planFacts(platformStatus.get(), "PAUSED".equals(platformStatus.get())?"DISABLE":"DELIVERY_OK"));
        doAnswer(call->{platformStatus.set("PAUSED");return null;}).when(gateway).write(eq(plan),any());
        when(insights.data(eq(new TenantId("tenant")),eq(new PlanId("plan")),any(),eq(true)))
            .thenReturn(spend("320"),spend("480"),spend("480"),spend("520"));
        var service=new OptimizationRuleResumeService(store,journal,gateway,insights,confirmations,execution,json);
        var below=service.preview(plan.tenantId(),plan.id());
        assertEquals(OptimizationRuleResumeService.Outcome.BELOW_LIMIT,below.outcome());
        assertThrows(BizException.class,()->service.resume(plan.tenantId(),plan.id(),confirm(below),"测试操作人"));
        assertFalse(store.get(plan.tenantId(),plan.id()).orElseThrow().enabled());
        assertEquals(1,journal.ruleHistory("tenant","plan","RULES",1,1,20).total());
        verify(gateway,never()).write(any(),any());
        var over=service.preview(plan.tenantId(),plan.id());
        assertEquals(OptimizationRuleResumeService.Outcome.LIMIT_PAUSE,over.outcome());
        assertEquals("SUCCEEDED",service.resume(plan.tenantId(),plan.id(),confirm(over),"测试操作人").pauseStatus());
        var saved=store.get(plan.tenantId(),plan.id()).orElseThrow();
        assertTrue(saved.enabled());
        assertEquals(automatic,saved.policy().rules());
        var pause=journal.ruleHistory("tenant","plan","RISK",1,1,20).records().getFirst();
        assertEquals("PAUSE",pause.kind());
        assertEquals("520",pause.report().path("daily_spend").asText());
        assertEquals("PAUSED",pause.actual().path("status").asText());
        assertThrows(BizException.class,()->service.resume(plan.tenantId(),plan.id(),confirm(over),"测试操作人"));
        var records=journal.ruleHistory("tenant","plan","RULES",1,1,20).records();
        assertEquals(List.of("RESUME","RESUME_CHECK","PAUSE"),records.stream().map(e->e.report().path("activity").asText()).toList());
        assertEquals("测试操作人",records.getFirst().report().path("operator").asText());
        assertEquals("LIMIT_PAUSE",records.getFirst().report().path("resume_outcome").asText());
        assertTrue(records.getFirst().report().hasNonNull("paused_at"));
        var checked=records.get(1).report();
        assertEquals("小灵",checked.path("operator").asText());
        assertEquals("520",checked.path("daily_spend").asText());
        assertEquals("DELIVERING",checked.path("plan_status").asText());
        assertTrue(checked.hasNonNull("daily_committed_addition"));
        assertEquals(1,journal.ruleSummary("tenant","plan",false).completed());
        verify(gateway,times(1)).write(eq(plan),eq(pause.proposal()));
    }
    @Test void resumingReminderRulesRecordsAdviceWithoutSubmittingAPause(){
        store.automationStore(journal);
        store.save(plan,policy(false),1);
        var gateway=mock(OptimizationExecutionGateway.class);
        var insights=mock(PlanInsightsService.class);
        var execution=mock(OptimizationExecutionService.class);
        when(plan.status()).thenReturn(PlanStatus.DELIVERING);
        when(gateway.plan("tenant","plan")).thenReturn(plan);
        when(gateway.ready()).thenReturn(true);
        when(gateway.readPlanFacts(plan,false)).thenReturn(planFacts("DELIVERING","DELIVERY_OK"));
        when(insights.data(eq(new TenantId("tenant")),eq(new PlanId("plan")),any(),eq(true))).thenReturn(spend("520"));
        var service=new OptimizationRuleResumeService(store,journal,gateway,insights,confirmations,execution,json);
        var preview=service.preview(plan.tenantId(),plan.id());
        assertEquals(OptimizationRuleResumeService.Outcome.LIMIT_REMINDER,preview.outcome());
        assertNull(service.resume(plan.tenantId(),plan.id(),confirm(preview),"测试操作人").pauseStatus());
        assertEquals("RECOMMENDED",journal.ruleActions("tenant","plan",3).getFirst().status());
        verifyNoInteractions(execution);
        verify(gateway,never()).write(any(),any());
    }
    @Test void expiredAuthorizationLeavesRulesPausedEvenAfterSuccessfulPreview(){
        store.automationStore(journal);
        var pausedPolicy=policy(false);
        store.save(plan,pausedPolicy,1);
        var gateway=mock(OptimizationExecutionGateway.class);
        var insights=mock(PlanInsightsService.class);
        var execution=mock(OptimizationExecutionService.class);
        when(plan.status()).thenReturn(PlanStatus.DELIVERING);
        when(gateway.plan("tenant","plan")).thenReturn(plan);
        when(gateway.ready()).thenReturn(true);
        when(gateway.readPlanFacts(plan,false)).thenReturn(planFacts("DELIVERING","DELIVERY_OK"));
        when(insights.data(eq(new TenantId("tenant")),eq(new PlanId("plan")),any(),eq(true)))
            .thenReturn(spend("520"))
            .thenThrow(new BizException(1211,"授权已失效"));
        var service=new OptimizationRuleResumeService(store,journal,gateway,insights,confirmations,execution,json);
        var preview=service.preview(plan.tenantId(),plan.id());
        var error=assertThrows(BizException.class,()->service.resume(plan.tenantId(),plan.id(),confirm(preview),"测试操作人"));
        assertEquals(1211,error.code());
        assertEquals(1211,assertThrows(BizException.class,()->service.preview(plan.tenantId(),plan.id())).code());
        var saved=store.get(plan.tenantId(),plan.id()).orElseThrow();
        assertFalse(saved.enabled());
        assertEquals(preview.version(),saved.version());
        assertEquals(pausedPolicy.rules(),saved.policy().rules());
        assertTrue(journal.ruleActions("tenant","plan",preview.version()+1).isEmpty());
        verifyNoInteractions(execution);
        verify(gateway,never()).write(any(),any());
    }
    @Test void manuallyPausedPlanResumesRulesWithoutAnyDeliveryWriteEvenAboveProtectionLimit(){
        store.automationStore(journal);
        var p=state.policy();
        var rules=p.rules();
        var automatic=new OptimizationRules(rules.roiFloor(),rules.spendReminder(),rules.spendProtection(),
            OptimizationRules.AUTO_PAUSE,rules.runawayMinutes(),rules.runawaySpend(),rules.maxAddition(),rules.dailyAddition());
        store.save(plan,new OptimizationPolicy(false,p.mode(),p.dailySpendLimit(),p.maxLoss(),p.minSpend(),p.minOrders(),
            p.persistenceWindows(),p.conversionDelayMinutes(),List.of(),null,null,null,automatic),1);
        var gateway=mock(OptimizationExecutionGateway.class);
        var insights=mock(PlanInsightsService.class);
        var execution=mock(OptimizationExecutionService.class);
        when(plan.status()).thenReturn(PlanStatus.PAUSED);
        when(gateway.plan("tenant","plan")).thenReturn(plan);
        when(gateway.ready()).thenReturn(true);
        when(gateway.readPlanFacts(plan,false)).thenReturn(planFacts("PAUSED","DISABLE"),planFacts("DELIVERING","DELIVERY_OK"),planFacts("PAUSED","DISABLE"));
        when(insights.data(eq(new TenantId("tenant")),eq(new PlanId("plan")),any(),eq(true))).thenAnswer(call->spend("520"));
        var service=new OptimizationRuleResumeService(store,journal,gateway,insights,confirmations,execution,json);
        var preview=service.preview(plan.tenantId(),plan.id());
        assertEquals("DISABLE",preview.qcStatus());
        assertEquals(OptimizationRuleResumeService.Outcome.PLAN_NOT_DELIVERING,preview.outcome());
        assertEquals(1804,assertThrows(BizException.class,()->service.resume(plan.tenantId(),plan.id(),confirm(preview),"测试操作人")).code());
        assertFalse(store.get(plan.tenantId(),plan.id()).orElseThrow().enabled());

        var checked=service.preview(plan.tenantId(),plan.id());
        assertNull(service.resume(plan.tenantId(),plan.id(),confirm(checked),"测试操作人").pauseStatus());
        var resumed=store.get(plan.tenantId(),plan.id()).orElseThrow();
        assertTrue(resumed.enabled());
        assertEquals(automatic,resumed.policy().rules());
        assertEquals("PAUSED",resumed.result().path("state").asText());
        assertEquals("520",resumed.result().path("daily_spend").asText());
        assertTrue(journal.ruleActions("tenant","plan",resumed.version()).isEmpty());
                verify(gateway,never()).write(any(),any());
        verifyNoInteractions(execution);
    }
    @Test void enablingWithoutReliableSpendDoesNotSaveRules(){
        var gateway=mock(OptimizationExecutionGateway.class);
        var insights=mock(PlanInsightsService.class);
        when(gateway.ready()).thenReturn(true);
        when(gateway.readPlanFacts(plan,false)).thenReturn(planFacts("DELIVERING","DELIVERY_OK"));
        var newPlan=new PlanId("new");
        when(plan.id()).thenReturn(newPlan);
        when(gateway.plan("tenant","new")).thenReturn(plan);
        when(insights.data(eq(new TenantId("tenant")),eq(newPlan),any(),eq(true)))
            .thenReturn(new PlanInsightsService.Statistics(Map.of(),List.of(),null,Instant.now(),null));
        var service=new OptimizationRuleResumeService(store,journal,gateway,insights,confirmations,mock(OptimizationExecutionService.class),json);
        assertEquals(1803,assertThrows(BizException.class,
            ()->service.configure(plan.tenantId(),newPlan,state.policy().rules(),true,0,null,"测试操作人")).code());
        assertTrue(store.get(plan.tenantId(),newPlan).isEmpty());
        verify(gateway,never()).write(any(),any());
    }

    @Test void serviceExpiryStagesRulesAndRecoveryRequiresTheNewVersion(){
        store.automationStore(journal);
        var gateway=mock(OptimizationExecutionGateway.class);
        var insights=mock(PlanInsightsService.class);
        var service=new OptimizationRuleResumeService(store,journal,gateway,insights,confirmations,mock(OptimizationExecutionService.class),json);
        when(gateway.plan("tenant","plan")).thenReturn(plan);
        when(gateway.ready()).thenReturn(true);
        doThrow(new com.lynx.ad.common.exception.DomainException(1102,"服务到期"))
            .doNothing().when(gateway).writable(plan);
        var old=state.policy().rules();
        var changed=new OptimizationRules(new BigDecimal("4.2"),old.spendReminder(),old.spendProtection(),
            old.protectionAction(),old.runawayMinutes(),old.runawaySpend(),old.maxAddition(),old.dailyAddition());
        service.configure(plan.tenantId(),plan.id(),changed,true,1,null,"测试操作人");
        var held=store.get(plan.tenantId(),plan.id()).orElseThrow();
        assertEquals(OptimizationStore.SERVICE_HOLD,held.holdReason());
        assertEquals(old,held.policy().rules());
        assertEquals(changed,held.pendingRules());
        when(gateway.readPlanFacts(plan,false)).thenReturn(planFacts("DELIVERING","DELIVERY_OK"));
        when(insights.data(eq(new TenantId("tenant")),eq(new PlanId("plan")),any(),eq(true))).thenReturn(spend("320"));
        var preview=service.preview(plan.tenantId(),plan.id());
        assertEquals(changed,preview.rules());
        assertEquals(1804,assertThrows(BizException.class,()->service.resume(plan.tenantId(),plan.id(),
            new OptimizationRuleResumeService.Confirmation(1,preview.day(),preview.planStatus(),preview.outcome()),"测试操作人")).code());
        service.resume(plan.tenantId(),plan.id(),confirm(preview),"测试操作人");
        var recovered=store.get(plan.tenantId(),plan.id()).orElseThrow();
        assertNull(recovered.holdReason());
        assertNull(recovered.pendingRules());
        assertEquals(changed,recovered.policy().rules());
        verify(gateway,never()).write(any(),any());
    }

    @Test void revocationFencesNewDispatchAndKeepsSentOperations(){
        store.automationStore(journal);
        var sent=create("sent");
        assertTrue(journal.claimAccount("tenant","account","owner"));
        approve(sent,new BigDecimal("40"));
        assertTrue(journal.dispatch(sent,"owner"));
        var unsent=create("unsent");
        approve(unsent,new BigDecimal("60"));
        journal.finish(sent,"VERIFY_PENDING","等待回查",null);
        var old=state.policy().rules();
        var automatic=new OptimizationRules(old.roiFloor(),old.spendReminder(),old.spendProtection(),
            OptimizationRules.AUTO_PAUSE,old.runawayMinutes(),old.runawaySpend(),old.maxAddition(),old.dailyAddition());
        jdbc.update("UPDATE optimization_policy SET config_json=? WHERE tenant_id='tenant' AND plan_id='plan'",
            json.valueToTree(automatic.policy(true)).toString());
        var gateway=mock(OptimizationExecutionGateway.class);
        when(gateway.plan("tenant","plan")).thenReturn(plan);
        var service=new OptimizationRuleResumeService(store,journal,gateway,mock(PlanInsightsService.class),
            confirmations,mock(OptimizationExecutionService.class),json);
        service.configure(plan.tenantId(),plan.id(),old,true,1,null,"测试操作人");
        var held=store.get(plan.tenantId(),plan.id()).orElseThrow();
        assertEquals(OptimizationStore.REVISION_HOLD,held.holdReason());
        assertEquals(old,held.pendingRules());
        assertFalse(journal.dispatch(unsent,"owner"));
        assertEquals("VERIFY_PENDING",journal.event("tenant",sent.id()).orElseThrow().status());
        verify(gateway,never()).readPlanFacts(any(),anyBoolean());
    }

    @Test void sameAccountAuthorizationHoldMergesInboxButPreservesEachPlanHistory(){
        store.automationStore(journal);
        var secondId=new PlanId("second");
        var second=mock(AdPlan.class);
        when(second.tenantId()).thenReturn(new TenantId("tenant"));
        when(second.accountId()).thenReturn(new AccountId("account"));
        when(second.id()).thenReturn(secondId);
        when(second.name()).thenReturn(new PlanName("第二个计划"));
        store.save(second,policy(true),0);
        store.suspend(plan,state,OptimizationStore.AUTHORIZATION_HOLD);
        store.suspend(second,store.get(plan.tenantId(),secondId).orElseThrow(),OptimizationStore.AUTHORIZATION_HOLD);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE biz_type='OPTIMIZATION_ACCOUNT_HOLD'",Integer.class));
        for(var id:List.of(plan.id(),secondId)) {
            var held=store.get(plan.tenantId(),id).orElseThrow();
            assertEquals(OptimizationStore.AUTHORIZATION_HOLD,held.holdReason());
            assertEquals("SUSPENDED",journal.ruleHistory("tenant",id.value(),"RULES",7,1,20).records().getFirst().report().path("activity").asText());
            assertFalse(store.claim(held,"poll",Instant.now()));
        }
    }

    private OptimizationExecutionGateway.PlanFacts planFacts(String status,String qcStatus){
        return new OptimizationExecutionGateway.PlanFacts(json.createObjectNode().put("qcId","123").put("status",status).put("qcStatus",qcStatus),qcStatus);
    }
    private PlanInsightsService.Statistics spend(String amount){
        return new PlanInsightsService.Statistics(Map.of("cost",new BigDecimal(amount)),List.of(),null,Instant.now(),null);
    }
    private OptimizationRuleResumeService.Confirmation confirm(OptimizationRuleResumeService.Preview preview){
        return new OptimizationRuleResumeService.Confirmation(preview.version(),preview.day(),preview.planStatus(),preview.outcome());
    }
    @Test void notificationFailureCannotLeaveRulesSilentlySaved(){
        store.automationStore(journal);jdbc.execute("ALTER TABLE notification ADD CONSTRAINT reject_rules CHECK (biz_type<>'OPTIMIZATION_RULES')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->store.save(plan,policy(false),1));
        var actual=store.get(plan.tenantId(),plan.id()).orElseThrow();assertEquals(1,actual.version());assertTrue(actual.enabled());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM optimization_event",Integer.class));
    }
    @Test void repeatedPollUpdatesOneIssueAndOneNotification(){journal.recordAdvice(state,report("object"));journal.recordAdvice(state,report("object"));assertEquals(1,journal.ruleActions("tenant","plan",state.version()).size());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));assertTrue(journal.ruleActions("other","plan",state.version()).isEmpty());}
    @Test void deliveryRestartExpiresUnsentAdviceButKeepsDispatchedAllowance() {
        var sent=create("dispatched");
        assertTrue(journal.claimAccount("tenant","account","owner"));
        approve(sent,new BigDecimal("100"));
        assertTrue(journal.dispatch(sent,"owner"));
        var approval=confirmations.find("tenant",sent.id()).orElseThrow();
        confirmations.dispatched(approval);
        var queued=create("queued");
        approve(queued,new BigDecimal("100"));
        var suggested=create("suggested");

        journal.expireDeliveryAdvice(state);

        assertEquals("EXECUTING",journal.event("tenant",sent.id()).orElseThrow().status());
        assertEquals("RESOLVED",journal.event("tenant",queued.id()).orElseThrow().status());
        assertEquals("RESOLVED",journal.event("tenant",suggested.id()).orElseThrow().status());
        assertEquals(new BigDecimal("100.00"),confirmations.committed("tenant","plan",approval.day()));
    }
    @Test void positiveCommitmentsSurviveDispatchTimeoutAndRetries(){var e=create("one");assertTrue(journal.claimAccount("tenant","account","owner"));approve(e,new BigDecimal("100"));approve(e,new BigDecimal("100"));var c=confirmations.find("tenant",e.id()).orElseThrow();assertEquals("user",c.actor());assertEquals(new BigDecimal("100.00"),confirmations.committed("tenant","plan",c.day()));confirmations.dispatched(c);confirmations.notSent(c);assertEquals(new BigDecimal("100.00"),confirmations.committed("tenant","plan",c.day()));assertTrue(confirmations.find("other",e.id()).isEmpty());assertEquals(0,confirmations.committed("other","plan",c.day()).signum());}
    @Test void onlyDefinitelyUnsentReservationsReleaseAllowance(){var e=create("one");journal.claimAccount("tenant","account","owner");approve(e,new BigDecimal("100"));var c=confirmations.find("tenant",e.id()).orElseThrow();confirmations.notSent(c);assertEquals(0,confirmations.committed("tenant","plan",c.day()).signum());}
    @Test void beforeSnapshotSurvivesTheFinalReadback(){var e=create("one");journal.claimAccount("tenant","account","owner");approve(e,new BigDecimal("100"));e=journal.event("tenant",e.id()).orElseThrow();var before=json.createObjectNode().put("budget","300");journal.rememberBefore(e,before);assertTrue(journal.dispatch(e,"owner"));journal.succeeded(e,json.createObjectNode().put("budget","400"));var finalEvent=journal.event("tenant",e.id()).orElseThrow();assertEquals("300",finalEvent.report().path("pre_action_facts").path("budget").asText());assertEquals("400",finalEvent.actual().path("budget").asText());}
    @Test void dailyCapAndWrongLeaseRollbackEventAndReservation(){var one=create("one");journal.claimAccount("tenant","account","owner");approve(one,new BigDecimal("150"));var two=create("two");assertThrows(BizException.class,()->approve(two,new BigDecimal("100")));assertEquals("RECOMMENDED",journal.event("tenant",two.id()).orElseThrow().status());assertTrue(confirmations.find("tenant",two.id()).isEmpty());journal.releaseAccount("tenant","account","owner");assertThrows(BizException.class,()->approve(two,BigDecimal.ZERO));}
    @Test void changedPolicyCannotCommitAStaleConfirmation(){var e=create("one");journal.claimAccount("tenant","account","owner");jdbc.update("UPDATE optimization_policy SET policy_version=policy_version+1");assertThrows(BizException.class,()->approve(e,new BigDecimal("100")));assertTrue(confirmations.find("tenant",e.id()).isEmpty());assertEquals("RECOMMENDED",journal.event("tenant",e.id()).orElseThrow().status());}
    @Test void reviewIsIdempotentAndOnlyRealReadbackCountsAsCompleted(){var e=create("one");var facts=json.createObjectNode().put("budget","300");var r=reviews.start(e,"budget",facts,json.createObjectNode(),MonitoringStrategy.Parameters.shadow());assertEquals(r.id(),reviews.start(e,"budget",facts,json.createObjectNode(),MonitoringStrategy.Parameters.shadow()).id());assertTrue(reviews.find("other",r.id()).isEmpty());assertEquals("RECOMMENDED",journal.event("tenant",e.id()).orElseThrow().status());assertTrue(reviews.applied(r,json.createObjectNode().put("budget","400")));assertFalse(reviews.applied(r,json.createObjectNode().put("budget","500")));reviews.verifiedWorkflow(r,json.createObjectNode().put("budget","400"));assertEquals("SUCCEEDED",journal.event("tenant",e.id()).orElseThrow().status());assertEquals("400",reviews.find("tenant",r.id()).orElseThrow().after().path("budget").asText());}
    @Test void disabledRulesDoNotErasePendingReviewWork(){var e=create("one");var r=reviews.start(e,"budget",json.createObjectNode(),json.createObjectNode(),MonitoringStrategy.Parameters.shadow());reviews.applied(r,json.createObjectNode().put("budget","400"));jdbc.update("UPDATE optimization_policy SET enabled=FALSE");jdbc.update("UPDATE optimization_review SET next_check_at=?",Timestamp.from(Instant.now().minusSeconds(1)));assertEquals(1,reviews.due().size());assertEquals(0,reviews.history("other","plan",false).size());}
}
