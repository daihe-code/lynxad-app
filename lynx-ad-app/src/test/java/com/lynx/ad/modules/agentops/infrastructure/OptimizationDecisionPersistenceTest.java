package com.lynx.ad.modules.agentops.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.application.*;
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
        try(var c=ds.getConnection()){for(String migration:List.of("V28__optimization_monitor.sql","V29__optimization_execution.sql","V51__optimization_decision_reviews.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/"+migration));}
        jdbc.execute("CREATE TABLE sys_tenant(id VARCHAR(64) PRIMARY KEY)");jdbc.update("INSERT INTO sys_tenant VALUES('tenant')");jdbc.execute("CREATE TABLE notification(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(64),channel VARCHAR(20),title VARCHAR(255),body TEXT,biz_type VARCHAR(64),biz_id VARCHAR(64),sent_at TIMESTAMP)");
        var p=mock(AdPlan.class);plan=p;when(p.tenantId()).thenReturn(new TenantId("tenant"));when(p.id()).thenReturn(new PlanId("plan"));when(p.accountId()).thenReturn(new AccountId("account"));when(p.awemeId()).thenReturn(new AwemeId("aweme"));when(p.name()).thenReturn(new PlanName("测试计划"));when(p.qcPlanId()).thenReturn(Optional.empty());
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
        assertEquals("盯盘优化已关闭",jdbc.queryForObject("SELECT title FROM notification",String.class));
        assertThrows(BizException.class,()->store.save(plan,policy(true),1));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));
        store.save(plan,policy(true),2);store.save(plan,policy(true),3);
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE tenant_id='tenant' AND biz_id='plan' AND biz_type='OPTIMIZATION_RULES' AND channel='INBOX'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE title='盯盘优化已开启' AND body LIKE '%ROI%400%480%确认暂停%'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE title='盯盘规则已更新'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE tenant_id='other'",Integer.class));
    }
    @Test void notificationFailureCannotLeaveRulesSilentlySaved(){
        store.automationStore(journal);jdbc.execute("ALTER TABLE notification ADD CONSTRAINT reject_rules CHECK (biz_type<>'OPTIMIZATION_RULES')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->store.save(plan,policy(false),1));
        var actual=store.get(plan.tenantId(),plan.id()).orElseThrow();assertEquals(1,actual.version());assertTrue(actual.enabled());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM optimization_event",Integer.class));
    }
    @Test void repeatedPollUpdatesOneIssueAndOneNotification(){journal.recordAdvice(state,report("object"));journal.recordAdvice(state,report("object"));assertEquals(1,journal.ruleActions("tenant","plan",state.version()).size());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));assertTrue(journal.ruleActions("other","plan",state.version()).isEmpty());}
    @Test void positiveCommitmentsSurviveDispatchTimeoutAndRetries(){var e=create("one");assertTrue(journal.claimAccount("tenant","account","owner"));approve(e,new BigDecimal("100"));approve(e,new BigDecimal("100"));var c=confirmations.find("tenant",e.id()).orElseThrow();assertEquals("user",c.actor());assertEquals(new BigDecimal("100.00"),confirmations.committed("tenant","plan",c.day()));confirmations.dispatched(c);confirmations.notSent(c);assertEquals(new BigDecimal("100.00"),confirmations.committed("tenant","plan",c.day()));assertTrue(confirmations.find("other",e.id()).isEmpty());assertEquals(0,confirmations.committed("other","plan",c.day()).signum());}
    @Test void onlyDefinitelyUnsentReservationsReleaseAllowance(){var e=create("one");journal.claimAccount("tenant","account","owner");approve(e,new BigDecimal("100"));var c=confirmations.find("tenant",e.id()).orElseThrow();confirmations.notSent(c);assertEquals(0,confirmations.committed("tenant","plan",c.day()).signum());}
    @Test void beforeSnapshotSurvivesTheFinalReadback(){var e=create("one");journal.claimAccount("tenant","account","owner");approve(e,new BigDecimal("100"));e=journal.event("tenant",e.id()).orElseThrow();var before=json.createObjectNode().put("budget","300");journal.rememberBefore(e,before);assertTrue(journal.dispatch(e,"owner"));journal.succeeded(e,json.createObjectNode().put("budget","400"));var finalEvent=journal.event("tenant",e.id()).orElseThrow();assertEquals("300",finalEvent.report().path("pre_action_facts").path("budget").asText());assertEquals("400",finalEvent.actual().path("budget").asText());}
    @Test void dailyCapAndWrongLeaseRollbackEventAndReservation(){var one=create("one");journal.claimAccount("tenant","account","owner");approve(one,new BigDecimal("150"));var two=create("two");assertThrows(BizException.class,()->approve(two,new BigDecimal("100")));assertEquals("RECOMMENDED",journal.event("tenant",two.id()).orElseThrow().status());assertTrue(confirmations.find("tenant",two.id()).isEmpty());journal.releaseAccount("tenant","account","owner");assertThrows(BizException.class,()->approve(two,BigDecimal.ZERO));}
    @Test void changedPolicyCannotCommitAStaleConfirmation(){var e=create("one");journal.claimAccount("tenant","account","owner");jdbc.update("UPDATE optimization_policy SET policy_version=policy_version+1");assertThrows(BizException.class,()->approve(e,new BigDecimal("100")));assertTrue(confirmations.find("tenant",e.id()).isEmpty());assertEquals("RECOMMENDED",journal.event("tenant",e.id()).orElseThrow().status());}
    @Test void reviewIsIdempotentAndOnlyRealReadbackCountsAsCompleted(){var e=create("one");var facts=json.createObjectNode().put("budget","300");var r=reviews.start(e,"budget",facts,json.createObjectNode(),MonitoringStrategy.Parameters.shadow());assertEquals(r.id(),reviews.start(e,"budget",facts,json.createObjectNode(),MonitoringStrategy.Parameters.shadow()).id());assertTrue(reviews.find("other",r.id()).isEmpty());assertEquals("RECOMMENDED",journal.event("tenant",e.id()).orElseThrow().status());assertTrue(reviews.applied(r,json.createObjectNode().put("budget","400")));assertFalse(reviews.applied(r,json.createObjectNode().put("budget","500")));reviews.verifiedWorkflow(r,json.createObjectNode().put("budget","400"));assertEquals("SUCCEEDED",journal.event("tenant",e.id()).orElseThrow().status());assertEquals("400",reviews.find("tenant",r.id()).orElseThrow().after().path("budget").asText());}
    @Test void disabledRulesDoNotErasePendingReviewWork(){var e=create("one");var r=reviews.start(e,"budget",json.createObjectNode(),json.createObjectNode(),MonitoringStrategy.Parameters.shadow());reviews.applied(r,json.createObjectNode().put("budget","400"));jdbc.update("UPDATE optimization_policy SET enabled=FALSE");jdbc.update("UPDATE optimization_review SET next_check_at=?",Timestamp.from(Instant.now().minusSeconds(1)));assertEquals(1,reviews.due().size());assertEquals(0,reviews.history("other","plan",false).size());}
}
