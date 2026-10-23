package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.api.dto.PlanSettingsRequest;
import com.lynx.ad.modules.delivery.application.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.domain.service.PlanEditVersion;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Real SQL transactions/row locks, two independent service and store instances, no cloud requests. */
class PlanSettingsConcurrencyTest {
    final TenantId tenant=new TenantId("tenant");final PlanId id=new PlanId("plan");final ObjectMapper json=new ObjectMapper();
    JdbcTemplate jdbc;AdPlanRepository plans;PlanTransferStore transfers;PlanSettingsEditStore first,second;
    void database(String mode) throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:edit_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000","sa","");
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE ad_plan(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),name VARCHAR(128),status VARCHAR(24),budget DECIMAL(14,2),roi DECIMAL(10,2))");
        jdbc.update("INSERT INTO ad_plan VALUES('plan','tenant','原计划','PAUSED',300,2)");
        try(var c=ds.getConnection()) {
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V22__plan_transfer.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V36__plan_settings_edit.sql"));
        }
        plans=mock(AdPlanRepository.class);
        when(plans.findById(any(),any())).thenAnswer(i->read(i.getArgument(0),i.getArgument(1),false));
        when(plans.findByIdForUpdate(any(),any())).thenAnswer(i->read(i.getArgument(0),i.getArgument(1),true));
        doAnswer(i->{AdPlan p=i.getArgument(0);jdbc.update("UPDATE ad_plan SET name=?,status=?,budget=?,roi=? WHERE tenant_id=? AND id=?",p.name().text(),p.status().name(),p.setting().dailyBudget().amount(),p.setting().roi2Goal().map(RoiGoal::value).orElse(null),p.tenantId().value(),p.id().value());return null;}).when(plans).save(any());
        transfers=new PlanTransferStore(jdbc,manager);
        first=new PlanSettingsEditStore(jdbc,json,plans,transfers,manager);
        second=new PlanSettingsEditStore(new JdbcTemplate(ds),json,plans,new PlanTransferStore(new JdbcTemplate(ds),manager),manager);
        principal();
    }
    Optional<AdPlan> read(TenantId t,PlanId p,boolean lock) {
        return jdbc.query("SELECT * FROM ad_plan WHERE tenant_id=? AND id=?"+(lock?" FOR UPDATE":""),(r,n)->new AdPlan(p,t,new AccountId("account"),new AwemeId("aweme"),PlanName.fromPlatform(r.getString("name")),PlanStatus.valueOf(r.getString("status")),PlanSource.LINGTOU,
            new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(r.getBigDecimal("budget")),Optional.of(new RoiGoal(r.getBigDecimal("roi"))),Schedule.fromNow(),AudienceJson.empty()),List.of(new QcProductId("123")),new QcPlanId("456"),"DISABLE",null,0,null,List.of()),t.value(),p.value()).stream().findFirst();
    }
    AdPlan current(){return plans.findById(tenant,id).orElseThrow();}
    PlanSettingsRequest request(String budget){return new PlanSettingsRequest(null,new BigDecimal(budget),null,first.state(current()).version());}
    void principal(){TenantContext.set(new TenantPrincipal(new UserId("user"),Optional.of(tenant),Optional.empty(),SysRole.USER));}
    ObjectNode facts(AdPlan p){var n=json.createObjectNode().put("ad_id","456").put("name",p.name().text()).put("status",p.status()==PlanStatus.PAUSED?"DISABLE":"DELIVERY_OK");n.putObject("delivery_setting").put("smart_bid_type","SMART_BID_CUSTOM").put("budget",p.setting().dailyBudget().amount()).put("roi2_goal",p.setting().roi2Goal().orElseThrow().value());return n;}
    PlanManagementService service(PlanSettingsEditStore edits,AdManagementPort port) {
        var credentials=mock(QianchuanCredentialFacade.class);when(credentials.getUsableCredential(any(),any())).thenReturn(new Credential("synthetic",new AdvertiserId("1")));
        var service=new PlanManagementService(plans,port,credentials,mock(PlanRemoteStore.class),mock(PlanQueryService.class),mock(BillingTrialService.class),mock(AuditRecorder.class),jdbc,json);
        service.settingsEdits(edits);service.transferStore(transfers);return service;
    }
    @AfterEach void close(){TenantContext.clear();if(jdbc!=null)jdbc.execute("SHUTDOWN");}

    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void twoAppsWithTheSamePageVersionDispatchExactlyOnePlatformWrite(String mode) throws Exception {
        database(mode);var port=mock(AdManagementPort.class);var platform=new AtomicReference<>(facts(current()));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(port.detail(any(),eq("456"))).thenAnswer(i->platform.get().deepCopy());
        doAnswer(i->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));var changed=platform.get().deepCopy();changed.withObject("/delivery_setting").put("budget",(BigDecimal)i.getArgument(3));platform.set(changed);return null;}).when(port).settings(any(),eq("456"),isNull(),any(),isNull());
        var one=service(first,port);var two=service(second,port);var request=request("400");
        var pool=Executors.newSingleThreadExecutor();
        try {
            var running=pool.submit(()->{principal();try{one.settings(tenant,id,request);}finally{TenantContext.clear();}});
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals(1523,assertThrows(BizException.class,()->two.settings(tenant,id,request)).code());
            release.countDown();running.get(5,TimeUnit.SECONDS);
            assertEquals(0,new BigDecimal("400").compareTo(current().setting().dailyBudget().amount()));
            assertEquals("IDLE",second.state(current()).status());
            assertEquals(1404,assertThrows(BizException.class,()->two.settings(tenant,id,request)).code());
            verify(port,times(1)).settings(any(),any(),nullable(String.class),any(),nullable(BigDecimal.class));
            assertNull(transfers.group(tenant,id).requestKey());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void twoFirstClaimsInitializeRowsSafelyAndOnlyOneClaimsTheGroup(String mode) throws Exception {
        database(mode);var request=request("400");var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Object> a=()->{start.await();try{return first.claim(tenant,id,request);}catch(BizException failure){return failure.code();}};
            Callable<Object> b=()->{start.await();try{return second.claim(tenant,id,request);}catch(BizException failure){return failure.code();}};
            var left=pool.submit(a);var right=pool.submit(b);start.countDown();var one=left.get(5,TimeUnit.SECONDS);var two=right.get(5,TimeUnit.SECONDS);
            assertNotEquals(one instanceof PlanSettingsEditStore.Claim,two instanceof PlanSettingsEditStore.Claim);
            assertEquals(1523,one instanceof Integer?one:two);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM delivery_plan_settings_edit",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void activeHandlerCannotBeVerifiedOrAcceptedAndIncompleteFactsNeverDispatch(String mode) throws Exception {
        database(mode);var p=current();var request=request("400");
        var port=mock(AdManagementPort.class);var incomplete=facts(p);incomplete.withObject("/delivery_setting").remove("roi2_goal");when(port.detail(any(),any())).thenReturn(incomplete);
        assertThrows(BizException.class,()->service(first,port).settings(tenant,id,request));
        verify(port,never()).settings(any(),any(),any(),any(),any());assertEquals("IDLE",first.state(current()).status());
        assertEquals(request.expectedVersion(),first.state(current()).version());
        var claim=second.claim(tenant,id,request("400"));second.dispatched(claim.pending());
        assertEquals(1523,assertThrows(BizException.class,()->first.verification(tenant,id,first.state(current()).version())).code());
        assertEquals("SENDING",first.state(current()).status());
        var optPaused=facts(p).put("status","DELIVERY_OK").put("opt_status","DISABLE");
        assertDoesNotThrow(()->PlanEditVersion.platformUnchanged(p,optPaused));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void staleClaimRollsBackGroupReservationAndUnchangedReadsDoNotInvalidateVersion(String mode) throws Exception {
        database(mode);var request=request("400");assertEquals(first.state(current()).version(),second.state(current()).version());
        var claimed=first.claim(tenant,id,request);first.dispatched(claimed.pending());first.complete(claimed.pending(),()->{});
        assertEquals(1404,assertThrows(BizException.class,()->second.claim(tenant,id,request)).code());
        assertNull(transfers.group(tenant,id).requestKey());assertEquals("IDLE",first.state(current()).status());
        assertTrue(first.state(current()).version().startsWith("1."));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void uncertainWriteSurvivesAnotherAppAndVerificationOwnershipCannotBeStolen(String mode) throws Exception {
        database(mode);var claim=first.claim(tenant,id,request("400"));first.dispatched(claim.pending());first.uncertain(claim.pending());
        assertEquals(1523,assertThrows(BizException.class,()->second.claim(tenant,id,request("500"))).code());
        var verification=second.verification(tenant,id,second.state(current()).version());
        assertEquals(1523,assertThrows(BizException.class,()->first.verification(tenant,id,first.state(current()).version())).code());
        first.uncertain(claim.pending());assertEquals("VERIFYING",second.state(current()).status());
        assertThrows(BizException.class,()->first.complete(claim.pending(),()->fail("stale writer cannot apply")));
        second.complete(verification,()->{});assertEquals("IDLE",first.state(current()).status());assertNull(transfers.group(tenant,id).requestKey());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void cacheReadStartedBeforeAWriteCannotOverwriteItsConfirmedResult(String mode) throws Exception {
        database(mode);var baseline=current();var expected=first.state(baseline).version();
        var claim=second.claim(tenant,id,request("400"));
        second.dispatched(claim.pending());
        second.complete(claim.pending(),()->{var p=current();p.updateSetting(new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(400),p.setting().roi2Goal(),p.setting().schedule(),p.setting().audience()));plans.save(p);});
        first.applyRead(baseline,expected,()->{plans.save(baseline);fail("delayed read must not apply");});
        assertEquals(0,new BigDecimal("400").compareTo(current().setting().dailyBudget().amount()));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void localFailureRollsBackFactsAndRetainsThePendingClaimForReadOnlyRecovery(String mode) throws Exception {
        database(mode);var claim=first.claim(tenant,id,request("400"));first.dispatched(claim.pending());
        assertThrows(IllegalStateException.class,()->first.complete(claim.pending(),()->{var p=current();p.rename(new PlanName("不得保存"));plans.save(p);throw new IllegalStateException("local persistence failed");}));
        assertEquals("原计划",current().name().text());first.uncertain(claim.pending());
        assertEquals("UNCERTAIN",second.state(current()).status());assertNotNull(transfers.group(tenant,id).requestKey());
        var port=mock(AdManagementPort.class);var actual=facts(current());actual.withObject("/delivery_setting").put("budget",400);
        when(port.detail(any(),any())).thenReturn(actual);
        assertEquals("CONFIRMED",service(second,port).verifySettings(tenant,id,new PlanManagementService.SettingsVerification(second.state(current()).version(),false)).status());
        verify(port,never()).settings(any(),any(),any(),any(),any());assertEquals("IDLE",first.state(current()).status());
        assertEquals(0,new BigDecimal("400").compareTo(current().setting().dailyBudget().amount()));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void acceptingCurrentSettingsCannotSilentlyAcceptANewerPlatformChange(String mode) throws Exception {
        database(mode);var claim=first.claim(tenant,id,request("400"));first.dispatched(claim.pending());first.uncertain(claim.pending());
        jdbc.update("UPDATE delivery_plan_settings_edit SET started_at=?",Timestamp.from(Instant.now().minusSeconds(181)));
        var port=mock(AdManagementPort.class);var changed=facts(current());changed.withObject("/delivery_setting").put("budget",350);when(port.detail(any(),any())).thenReturn(changed);
        var service=service(second,port);var oldReviewedVersion=second.state(current()).version();
        assertEquals(1404,assertThrows(BizException.class,()->service.verifySettings(tenant,id,new PlanManagementService.SettingsVerification(oldReviewedVersion,true))).code());
        assertEquals("UNCERTAIN",first.state(current()).status());assertEquals(0,new BigDecimal("300").compareTo(current().setting().dailyBudget().amount()));
        assertEquals("UNCERTAIN",service.verifySettings(tenant,id,new PlanManagementService.SettingsVerification(second.state(current()).version(),false)).status());
        assertEquals(0,new BigDecimal("350").compareTo(current().setting().dailyBudget().amount()));
        assertEquals("ACCEPTED_CURRENT",service.verifySettings(tenant,id,new PlanManagementService.SettingsVerification(second.state(current()).version(),true)).status());
        verify(port,never()).settings(any(),any(),any(),any(),any());assertEquals("IDLE",first.state(current()).status());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void incompleteOrUnmatchedReadbackNeverReplaysAndAcceptanceRequiresWaitingAndTheCurrentVersion(String mode) throws Exception {
        database(mode);var claim=first.claim(tenant,id,request("400"));first.dispatched(claim.pending());first.uncertain(claim.pending());
        var port=mock(AdManagementPort.class);var initialFacts=facts(current());when(port.detail(any(),any())).thenReturn(initialFacts);var service=service(second,port);
        var result=service.verifySettings(tenant,id,new PlanManagementService.SettingsVerification(second.state(current()).version(),false));
        assertEquals("UNCERTAIN",result.status());assertFalse(result.canAcceptCurrent());
        assertThrows(BizException.class,()->service.verifySettings(tenant,id,new PlanManagementService.SettingsVerification(second.state(current()).version(),true)));
        jdbc.update("UPDATE delivery_plan_settings_edit SET started_at=?",Timestamp.from(Instant.now().minusSeconds(181)));
        assertEquals(1404,assertThrows(BizException.class,()->service.verifySettings(tenant,id,new PlanManagementService.SettingsVerification("0."+PlanEditVersion.snapshot(current()),true))).code());
        assertEquals("ACCEPTED_CURRENT",service.verifySettings(tenant,id,new PlanManagementService.SettingsVerification(second.state(current()).version(),true)).status());
        verify(port,never()).settings(any(),any(),any(),any(),any());assertEquals(0,new BigDecimal("300").compareTo(current().setting().dailyBudget().amount()));
        assertThrows(BizException.class,()->first.claim(new TenantId("foreign"),id,request("400")));
    }
}
