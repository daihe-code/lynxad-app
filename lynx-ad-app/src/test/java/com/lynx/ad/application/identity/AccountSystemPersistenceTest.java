package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.admin.application.AdminAuthService;
import com.lynx.ad.modules.admin.application.AdminPointService;
import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore;
import com.lynx.ad.modules.identity.application.AccountBindingRegistry;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.billing.application.*;
import com.lynx.ad.modules.billing.domain.model.*;
import com.lynx.ad.modules.billing.domain.port.*;
import com.lynx.ad.modules.tenant.application.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

public class AccountSystemPersistenceTest {
    public static String md5(String value) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));}
    record Fixture(JdbcTemplate jdbc,TransactionTemplate tx,AccountBindingRegistry bindings,PointWalletService points) {}
    Fixture fixture(String mode) throws Exception { return fixture(mode,false); }
    Fixture fixture(String mode,boolean duplicates) throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:accounts_"+UUID.randomUUID()+";MODE="+mode+";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE ALIAS HASH_MD5 FOR 'com.lynx.ad.application.identity.AccountSystemPersistenceTest.md5'");
        jdbc.execute("CREATE TABLE sys_tenant(id VARCHAR(36) PRIMARY KEY,name VARCHAR(128),status VARCHAR(16),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(36) PRIMARY KEY,status VARCHAR(16))");
        jdbc.execute("CREATE TABLE tenant_member(tenant_id VARCHAR(36),user_id VARCHAR(36),member_role VARCHAR(16),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id,user_id))");
        jdbc.execute("CREATE TABLE oauth_account(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(36),platform VARCHAR(24),advertiser_id VARCHAR(64),authorized_by_user_id VARCHAR(36),grant_id VARCHAR(36),status VARCHAR(24))");
        for(String t:List.of("a","b")) {
            jdbc.update("INSERT INTO sys_tenant(id,name,status) VALUES(?,?,'ACTIVE')",t,t);
            jdbc.update("INSERT INTO sys_user VALUES(?,'ACTIVE')",t);
            jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role) VALUES(?,?,'OWNER')",t,t);
        }
        if(duplicates) {
            jdbc.update("INSERT INTO oauth_account VALUES('legacy-a','a','QIANCHUAN','1001','a','direct','ACTIVE')");
            jdbc.update("INSERT INTO oauth_account VALUES('legacy-b','b','QIANCHUAN','1001','b','agency','UNUSABLE')");
            jdbc.update("INSERT INTO oauth_account VALUES('unknown-owner','a','QIANCHUAN','1002','missing',NULL,'ACTIVE')");
        }
        String migration=new String(new ClassPathResource("db/"+(mode.equals("MySQL")?"migration-mysql":"migration")+"/V52__workspace_account_system.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8).replace("md5(","HASH_MD5(");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8)));}
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new ClassPathResource("db/"+(mode.equals("MySQL")?"migration-mysql":"migration")+"/V53__workspace_team_and_points.sql"),
                new ClassPathResource("db/"+(mode.equals("MySQL")?"migration-mysql":"migration")+"/V56__workspace_child_roles.sql")).execute(ds);
        var foundation=new WorkspaceFoundation(jdbc);var quotas=mock(EntitlementQueryService.class);when(quotas.adAccountLimit(any())).thenReturn(2);
        return new Fixture(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),new AccountBindingRegistry(jdbc,quotas,foundation),new PointWalletService(jdbc,foundation));
    }

    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void adminAdjustsSharedAvailablePointsOnceWithoutTouchingReservations(String mode) throws Exception {
        var f = fixture(mode);
        f.jdbc.update("INSERT INTO sys_user VALUES('child','ACTIVE')");
        f.jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,status,membership_id) VALUES('a','child','MEMBER','ACTIVE','m-child')");
        var auth = mock(AdminAuthService.class);
        var admin = new AdminAccountStore.Account("admin", "operator", "测试管理员", "ACTIVE", null, null, false);
        when(auth.require()).thenReturn(admin);
        var audit = mock(AdminAccountStore.class);
        var service = new AdminPointService(auth, audit, f.jdbc, new WorkspaceFoundation(f.jdbc), mock(WorkspacePointOrders.class),
                f.points, new PointLedgerService(f.jdbc), new com.fasterxml.jackson.databind.ObjectMapper());
        f.tx.executeWithoutResult(s -> {
            f.points.credit("a", "init", new BigDecimal("10"));
            f.points.reserve("a", "pending", null, "a", new BigDecimal("4"));
        });
        var before = f.tx.execute(s -> service.balance("child", "a"));
        UUID request = UUID.randomUUID();
        var add = new AdminPointService.Change(request, "a", AdminPointService.Direction.ADD, new BigDecimal("5.125"),
                before.version(), "活动补偿");
        var added = f.tx.execute(s -> service.adjust("child", add));
        assertEquals(new BigDecimal("11.1250"), added.available());
        assertEquals(added, f.tx.execute(s -> service.adjust("child", add)));

        var conflict = new AdminPointService.Change(request, "a", AdminPointService.Direction.ADD, new BigDecimal("5.125"),
                before.version(), "另一个原因");
        assertEquals(409, assertThrows(BizException.class, () -> f.tx.execute(s -> service.adjust("child", conflict))).code());
        var tooMuch = new AdminPointService.Change(UUID.randomUUID(), "a", AdminPointService.Direction.DEDUCT,
                new BigDecimal("12"), added.version(), "收回积分");
        assertEquals(1903, assertThrows(BizException.class, () -> f.tx.execute(s -> service.adjust("child", tooMuch))).code());
        var stale = new AdminPointService.Change(UUID.randomUUID(), "a", AdminPointService.Direction.DEDUCT,
                new BigDecimal("1"), before.version(), "收回积分");
        assertEquals(409, assertThrows(BizException.class, () -> f.tx.execute(s -> service.adjust("child", stale))).code());
        assertThrows(BizException.class, () -> f.tx.execute(s -> service.adjust("b", tooMuch)));

        var deduct = new AdminPointService.Change(UUID.randomUUID(), "a", AdminPointService.Direction.DEDUCT,
                added.available(), added.version(), "收回积分");
        var after = f.tx.execute(s -> service.adjust("child", deduct));
        assertEquals(0, after.available().signum());
        assertEquals(new BigDecimal("4.0000"), after.frozen());
        assertEquals(0, after.consumed().signum());
        assertEquals(after, f.tx.execute(s -> service.adjust("child", add)));
        assertEquals(0, f.points.balance("b").available().signum());
        assertEquals(List.of("ADMIN_CREDIT", "ADMIN_DEBIT"), f.jdbc.queryForList(
                "SELECT event_type FROM point_ledger WHERE tenant_id='a' AND event_type LIKE 'ADMIN%' ORDER BY event_type", String.class));
        verify(audit).audit(admin, "POINT_ADJUST", "SUCCESS", "child", "a", "增加 5.125 积分（6 → 11.125） · 活动补偿");
        verify(audit).audit(admin, "POINT_ADJUST", "SUCCESS", "child", "a", "扣减 11.125 积分（11.125 → 0） · 收回积分");
        verifyNoMoreInteractions(audit);
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void migrationPreservesIdsAndQuarantinesDuplicateOrUnknownResponsibility(String mode) throws Exception {
        var f=fixture(mode,true);
        assertEquals(3,f.jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE status='REVIEW_REQUIRED'",Integer.class));
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_claim",Integer.class));
        assertEquals("legacy-a",f.bindings.find("a","legacy-a").orElseThrow().id());
        assertThrows(BizException.class,()->f.bindings.executionScope("a","legacy-a","a"));
    }
        OauthAccount account(String tenant,String id,String adv,String status,String user,String grant) {
        return new OauthAccount(new AccountId(id),new TenantId(tenant),Platform.QIANCHUAN,new AdvertiserId(adv),"Account",new EncryptedToken("encrypted"),new EncryptedToken("encrypted-refresh"),Instant.now().plusSeconds(1000),Instant.now().plusSeconds(10000),status,List.of(),user).withAuthorizationBinding(grant,"app",null);
    }
    void bind(Fixture f,String tenant,String id,String adv,String status,String user,String grant) {f.tx.executeWithoutResult(s->f.bindings.synchronize(account(tenant,id,adv,status,user,grant)));}
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void reauthorizationUsesOneSlotAndKeepsResponsibilityAcrossPaths(String mode) throws Exception {
        var f=fixture(mode);bind(f,"a","x","1001","ACTIVE","a","direct");bind(f,"a","x","1001","ACTIVE","a","agency");
        assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_claim",Integer.class));
        assertEquals(2,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_grant_path",Integer.class));
        f.jdbc.update("INSERT INTO sys_user VALUES('colleague','ACTIVE')");f.jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,status,membership_id) VALUES('a','colleague','OWNER','ACTIVE','m2')");
        assertThrows(BizException.class,()->bind(f,"a","x","1001","ACTIVE","colleague","new"));
        assertEquals("a",f.bindings.find("a","x").orElseThrow().boundUserId());
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void currentClaimCanRenewAfterOtherWorkspaceBindingIsRetired(String mode) throws Exception {
        var f=fixture(mode);bind(f,"a","x","1001","ACTIVE","a","direct");
        f.jdbc.update("INSERT INTO ad_account_binding(id,tenant_id,real_account_id,bound_user_id,status) VALUES('legacy-b','b',?,'b','REVIEW_REQUIRED')",
                f.bindings.find("a","x").orElseThrow().realAccountId());
        assertThrows(BizException.class,()->bind(f,"a","x","1001","ACTIVE","a","renewed"));
        f.tx.executeWithoutResult(s->f.bindings.release("b","legacy-b"));
        bind(f,"a","x","1001","ACTIVE","a","renewed");
        assertEquals("x",f.bindings.executionScope("a","x","a").bindingId());
        assertEquals("RELEASED",f.bindings.find("b","legacy-b").orElseThrow().status());
        assertThrows(BizException.class,()->bind(f,"b","legacy-b","1001","ACTIVE","b","old"));
        assertEquals(2,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_grant_path WHERE binding_id='x'",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void invalidTokensStillOccupyCapacityAndGlobalClaim(String mode) throws Exception {
        var f=fixture(mode);bind(f,"a","x","1001","ACTIVE","a",null);bind(f,"a","x","1001","UNUSABLE","a",null);bind(f,"a","y","1002","ACTIVE","a",null);
        assertThrows(BizException.class,()->bind(f,"a","z","1003","ACTIVE","a",null));
        assertThrows(BizException.class,()->bind(f,"b","other","1001","ACTIVE","b",null));
        bind(f,"a","x","1001","UNBOUND","a",null);
        assertThrows(BizException.class,()->bind(f,"b","other","1001","ACTIVE","b",null)); // Release is not a cross-workspace handover.
        assertThrows(BizException.class,()->f.bindings.executionScope("a","x","a"));
        bind(f,"a","x","1001","ACTIVE","a",null);
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void concurrentCrossTenantBindHasExactlyOneWinner(String mode) throws Exception {
        var f=fixture(mode);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Boolean>>();for(String tenant:List.of("a","b"))tasks.add(pool.submit(()->{start.await();try{bind(f,tenant,tenant+"account","1001","ACTIVE",tenant,null);return true;}catch(RuntimeException ex){return false;}}));
            start.countDown();int wins=0;for(var task:tasks)if(task.get(20,TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_claim",Integer.class));
        }
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void taskSnapshotCannotSurviveMembershipOrBindingVersionChange(String mode) throws Exception {
        var f=fixture(mode);bind(f,"a","x","1001","ACTIVE","a",null);var scope=f.bindings.executionScope("a","x","a");
        f.jdbc.update("UPDATE tenant_member SET permission_version=permission_version+1 WHERE tenant_id='a'");
        assertThrows(BizException.class,()->f.bindings.validate(scope));
        f.jdbc.update("UPDATE tenant_member SET status='REMOVED' WHERE tenant_id='a'");
        assertThrows(BizException.class,()->f.bindings.executionScope("a","x",null));
        assertThrows(BizException.class,()->new WorkspaceAccess(f.jdbc).member("a","a"));
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void walletIdempotencyPartialSettlementAndRefundAreTenantScoped(String mode) throws Exception {
        var f=fixture(mode);var p=f.points;
        f.tx.executeWithoutResult(s->{p.credit("a","payment",new BigDecimal("100"));p.credit("a","payment",new BigDecimal("100"));p.reserve("a","job",null,"a",new BigDecimal("60"));p.reserve("a","job",null,"a",new BigDecimal("60"));});
        assertEquals(0,new BigDecimal("40").compareTo(p.balance("a").available()));
        assertThrows(BizException.class,()->f.tx.executeWithoutResult(s->p.reserve("a","job",null,"a",new BigDecimal("61"))));
        f.tx.executeWithoutResult(s->{p.settle("a","job",new BigDecimal("35"));p.settle("a","job",new BigDecimal("35"));});
        assertEquals(0,new BigDecimal("65").compareTo(p.balance("a").available()));
        f.tx.executeWithoutResult(s->{p.refund("a","job");p.refund("a","job");});
        assertEquals(0,new BigDecimal("100").compareTo(p.balance("a").available()));assertEquals(0,p.balance("a").frozen().signum());
        assertEquals(0,p.balance("b").available().signum());assertTrue(p.ledger("b",1,10).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void concurrentReservationsCannotOverdrawSharedWallet(String mode) throws Exception {
        var f=fixture(mode);f.tx.executeWithoutResult(s->f.points.credit("a","payment",new BigDecimal("100")));var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<Boolean>>();for(String key:List.of("x","y"))futures.add(pool.submit(()->{start.await();try{f.tx.executeWithoutResult(s->f.points.reserve("a",key,null,"a",new BigDecimal("70")));return true;}catch(BizException ex){return false;}}));
            start.countDown();int wins=0;for(var future:futures)if(future.get(20,TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);assertEquals(0,new BigDecimal("30").compareTo(f.points.balance("a").available()));
            assertEquals(0,new BigDecimal("70").compareTo(f.points.balance("a").frozen()));
        }
    }

    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void generationUsesTheOriginalRateAndOnlyChargesDeliveredMaterials(String mode) throws Exception {
        var f=fixture(mode);
        bind(f,"a","x","1001","ACTIVE","a",null);
        var pricing=new WorkspacePricing();
        pricing.setEnabled(true);
        var subscriptions = mock(SubscriptionSupport.class);
        var tenant = new TenantId("a");
        when(subscriptions.effective(eq(tenant), any())).thenReturn(Optional.empty());
        var charges=new GenerationCharges(pricing,mock(WorkspacePointOrders.class),new WorkspaceFoundation(f.jdbc),f.points,
                f.bindings,f.jdbc,new com.fasterxml.jackson.databind.ObjectMapper(),subscriptions);
        f.tx.executeWithoutResult(s -> f.points.credit("a","test-credit",new BigDecimal("600")));
        assertFalse(charges.access("a").enabled());
        assertThrows(BizException.class, () -> f.tx.executeWithoutResult(s ->
                charges.hold("a","trial-generation","x","a",Map.of("video",1))));
        when(subscriptions.effective(eq(tenant), any())).thenReturn(Optional.of(activeSubscription(tenant, 9900)));
        assertEquals(GenerationCharges.BalanceStatus.SUFFICIENT, charges.access("a").balanceStatus());
        assertThrows(BizException.class, () -> charges.validate("a","unreserved"));
        f.tx.executeWithoutResult(s -> {
            assertTrue(charges.hold("a","generation","x","a",Map.of("video",2,"image",1)));
            charges.validate("a","generation");
        });
        assertEquals(GenerationCharges.BalanceStatus.LOW, charges.access("a").balanceStatus());
        assertEquals(0,new BigDecimal("495").compareTo(f.points.balance("a").frozen()));
        assertThrows(BizException.class, () -> f.tx.executeWithoutResult(s ->
                charges.hold("a","overdraw","x","a",Map.of("video",1))));
        pricing.getGenerationPoints().put("video",900L);
        f.tx.executeWithoutResult(s -> {
            assertTrue(charges.hold("a","generation","x","a",Map.of("video",2,"image",1)));
            charges.finish("a","generation",Map.of("video",1));
            charges.finish("a","generation",Map.of("video",1));
        });
        assertEquals(0,new BigDecimal("240").compareTo(f.points.balance("a").consumed()));
        assertEquals(0,new BigDecimal("360").compareTo(f.points.balance("a").available()));
        assertEquals(0,f.points.balance("a").frozen().signum());
        assertEquals(0,f.points.balance("b").consumed().signum());
        f.tx.executeWithoutResult(s -> {
            charges.hold("a","retry","x","a",Map.of("image",1));
            charges.finish("a","retry",Map.of());
        });
        assertEquals(0,new BigDecimal("360").compareTo(f.points.balance("a").available()));

        String remakeUnit = WorkspacePricing.REMAKE_1080P_SECOND;
        assertThrows(BizException.class, () -> f.tx.executeWithoutResult(s ->
                charges.hold("a", "remake-too-long", "x", "a", Map.of(remakeUnit, 16))));
        f.tx.executeWithoutResult(s -> charges.hold("a", "remake", "x", "a", Map.of(remakeUnit, 9)));
        assertEquals(new BigDecimal("216"), charges.quote("a", "remake").total());
        assertEquals(0, new BigDecimal("216").compareTo(f.points.balance("a").frozen()));
        pricing.getGenerationPoints().put(remakeUnit, 50L);
        f.tx.executeWithoutResult(s -> {
            charges.finishAll("a", "remake");
            charges.finishAll("a", "remake");
        });
        assertEquals(0, new BigDecimal("456").compareTo(f.points.balance("a").consumed()));
        assertEquals(0, new BigDecimal("144").compareTo(f.points.balance("a").available()));
        assertEquals(0, f.points.balance("a").frozen().signum());
    }

    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void migrationShortensOnlyActiveStandardTrials(String mode) {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:trial_" + UUID.randomUUID() + ";MODE=" + mode
                + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE billing_trial(tenant_id VARCHAR(36) PRIMARY KEY, started_at TIMESTAMP, expires_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE platform_package_adjustment(tenant_id VARCHAR(36), grant_type VARCHAR(24), after_end TIMESTAMP)");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant start = now.minus(Duration.ofDays(1));
        Instant previousExpiry = start.plus(Duration.ofDays(7));
        Instant expiredStart = now.minus(Duration.ofDays(8));
        for (String tenant : List.of("standard", "admin", "expired", "custom")) {
            Instant trialStart = tenant.equals("expired") ? expiredStart : start;
            int trialDays = tenant.equals("custom") ? 10 : 7;
            jdbc.update("INSERT INTO billing_trial VALUES(?,?,?)", tenant, Timestamp.from(trialStart),
                    Timestamp.from(trialStart.plus(Duration.ofDays(trialDays))));
        }
        jdbc.update("INSERT INTO platform_package_adjustment VALUES('admin','TRIAL',?)", Timestamp.from(previousExpiry));
        String path = "db/" + (mode.equals("MySQL") ? "migration-mysql" : "migration")
                + "/V58__standard_trial_three_days.sql";
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new ClassPathResource(path)).execute(ds);
        var expiries = jdbc.query("SELECT tenant_id, expires_at FROM billing_trial", rs -> {
            Map<String, Instant> result = new HashMap<>();
            while (rs.next()) result.put(rs.getString(1), rs.getTimestamp(2).toInstant());
            return result;
        });
        assertEquals(Map.of("standard", start.plus(Duration.ofDays(3)), "admin", previousExpiry,
                "expired", now.minus(Duration.ofDays(1)), "custom", start.plus(Duration.ofDays(10))), expiries);
    }

    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void subscriptionPointsDifferByPlanAndAreGrantedOncePerPeriod(String mode) throws Exception {
        var f = fixture(mode);
        var pricing = new WorkspacePricing();
        pricing.setEnabled(true);
        var subscriptions = mock(SubscriptionSupport.class);
        var grants = new WorkspacePointOrders(pricing, null, new WorkspaceFoundation(f.jdbc), null, f.points,
                f.jdbc, subscriptions);
        var tenant = new TenantId("a");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        when(subscriptions.effective(eq(tenant), any())).thenReturn(Optional.empty());
        f.tx.executeWithoutResult(s -> grants.ensureCurrentGrant("a"));
        assertEquals(0, f.points.balance("a").available().signum());
        for (var entry : Map.of(PlanCode.BASIC, 3000, PlanCode.PROFESSIONAL, 9000, PlanCode.FLAGSHIP, 30000)
                .entrySet().stream().sorted(Map.Entry.comparingByValue()).toList()) {
            when(subscriptions.effective(eq(tenant), any())).thenReturn(Optional.of(new Subscription(tenant,
                    entry.getKey(), SubStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600),
                    false, "WECHAT", 9900, null, now)));
            f.tx.executeWithoutResult(s -> grants.ensureCurrentGrant("a"));
            f.tx.executeWithoutResult(s -> grants.ensureCurrentGrant("a"));
            assertEquals(entry.getValue().intValue(), f.points.balance("a").available().intValueExact());
        }
    }

    private Subscription activeSubscription(TenantId tenant, int paidFen) {
        Instant now = Instant.now();
        return new Subscription(tenant, PlanCode.BASIC, SubStatus.ACTIVE, now.minusSeconds(3600),
                now.plusSeconds(3600), false, "WECHAT", paidFen, null, now);
    }

    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void pointHistoryPreservesChildAttributionAndDistinguishesAvailableFromConsumed(String mode) throws Exception {
        var f = fixture(mode);
        f.jdbc.execute("ALTER TABLE sys_user ADD phone VARCHAR(20)");
        f.jdbc.execute("CREATE TABLE user_profile(user_id VARCHAR(36) PRIMARY KEY, nickname VARCHAR(64))");
        f.jdbc.update("INSERT INTO sys_user(id,status,phone) VALUES('child','ACTIVE','13900001234')");
        f.jdbc.update("INSERT INTO user_profile VALUES('child','素材运营')");
        f.jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,status,membership_id) VALUES('a','child','OPERATOR','ACTIVE','child-member')");
        f.tx.executeWithoutResult(s -> {
            f.points.credit("a", "recharge:order-a", new BigDecimal("1000"), "a");
            f.points.credit("a", "subscription:interval:PROFESSIONAL", new BigDecimal("100"));
            f.points.reserve("a", "remake:task-a", null, "child", new BigDecimal("240"));
            f.points.settle("a", "remake:task-a", new BigDecimal("180"));
            f.points.refund("a", "remake:task-a");
            f.points.credit("b", "recharge:order-b", new BigDecimal("2000"), "b");
        });
        f.jdbc.update("UPDATE tenant_member SET status='REMOVED' WHERE user_id='child'");
        var ledger = new PointLedgerService(f.jdbc);
        var auth = mock(AdminAuthService.class);
        var audit = mock(AdminAccountStore.class);
        var recharge = mock(WorkspacePointOrders.class);
        var admin = new AdminPointService(auth, audit, f.jdbc, new WorkspaceFoundation(f.jdbc), recharge, f.points,
                ledger, new com.fasterxml.jackson.databind.ObjectMapper());
        var page = admin.history("a", "a", null, null, 1);
        assertEquals(5, page.records().size());
        assertEquals(List.of("a", "child"), page.accounts().stream().map(PointLedgerService.Account::userId).toList());
        var charges = ledger.history("a", "child", PointLedgerService.Kind.SETTLE, 1).records();
        assertEquals(1, charges.size());
        var expense = charges.getFirst();
        assertEquals(new PointLedgerService.Account("child", "素材运营", "1234", "OPERATOR"), expense.account());
        assertEquals("180.0000", expense.amount());
        assertEquals("60.0000", expense.availableChange());
        assertEquals("remake:task-a", expense.reference());
        var receipt = ledger.history("a", null, PointLedgerService.Kind.RECHARGE, 1).records().getFirst();
        assertEquals("order-a", receipt.reference());
        assertEquals("OWNER", receipt.account().role());
        assertEquals("1000.0000", receipt.availableChange());
        assertEquals(f.points.balance("a").available(), page.records().stream()
                .map(item -> new BigDecimal(item.availableChange())).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(ledger.history("a", "b", null, 1).records().isEmpty());
        assertThrows(BizException.class, () -> admin.history("a", "b", null, null, 1));
        verifyNoInteractions(audit, recharge);

        f.jdbc.update("UPDATE sys_user SET status='DELETED' WHERE id='child'");
        var historical = ledger.history("a", "child", PointLedgerService.Kind.SETTLE, 1).records().getFirst().account();
        assertEquals(new PointLedgerService.Account("child", null, null, "OPERATOR"), historical);
        f.tx.executeWithoutResult(s -> {
            for (int index = 0; index < 20; index++) f.points.credit("a", "additional:" + index, BigDecimal.ONE);
        });
        assertTrue(ledger.history("a", null, null, 1).hasMore());
        assertEquals(5, ledger.history("a", null, null, 2).records().size());
        assertFalse(ledger.history("a", null, null, 2).hasMore());
    }

    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void childGenerationRechargeAndAdminDeductionUseTheSameLockedWallet(String mode) throws Exception {
        var f = fixture(mode);
        for (String child : List.of("child-one", "child-two")) {
            f.jdbc.update("INSERT INTO sys_user VALUES(?,'ACTIVE')", child);
            f.jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,status,membership_id) VALUES('a',?,'MEMBER','ACTIVE',?)",
                    child, child + "-membership");
        }
        bind(f, "a", "video-one", "1001", "ACTIVE", "child-one", null);
        bind(f, "a", "video-two", "1002", "ACTIVE", "child-two", null);
        var tenant = new TenantId("a");
        var foundation = new WorkspaceFoundation(f.jdbc);
        var pricing = new WorkspacePricing();
        pricing.setEnabled(true);
        pricing.setPeriodPoints(Map.of());
        var subscriptions = mock(SubscriptionSupport.class);
        when(subscriptions.effective(eq(tenant), any())).thenReturn(Optional.of(activeSubscription(tenant, 9900)));
        var orders = mock(OrderRepository.class);
        var access = mock(WorkspaceAccess.class);
        var owner = com.lynx.ad.common.security.MemberRole.OWNER;
        when(access.require(owner)).thenReturn(new WorkspaceAccess.Membership("owner", "a", "a", owner, 1));
        var recharge = new WorkspacePointOrders(pricing, access, foundation, orders, f.points, f.jdbc, subscriptions);
        var order = org.mockito.ArgumentCaptor.forClass(BillingOrder.class);
        when(orders.findById(eq(tenant), anyString())).thenReturn(Optional.empty());
        f.tx.execute(s -> recharge.create("r99", "combined-recharge"));
        verify(orders).save(order.capture());
        var auth = mock(AdminAuthService.class);
        when(auth.require()).thenReturn(new AdminAccountStore.Account("admin", "operator", "平台管理员", "ACTIVE", null, null, false));
        var audit = mock(AdminAccountStore.class);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var admin = new AdminPointService(auth, audit, f.jdbc, foundation, recharge, f.points, new PointLedgerService(f.jdbc), json);
        var charges = new GenerationCharges(pricing, recharge, foundation, f.points, f.bindings, f.jdbc, json, subscriptions);
        f.tx.execute(s -> f.points.credit("a", "initial", new BigDecimal("400")));
        f.tx.execute(s -> charges.hold("a", "child-video-one", "video-one", "child-one", Map.of("video", 1)));
        assertEquals(1903, assertThrows(BizException.class, () -> f.tx.execute(
                s -> charges.hold("a", "child-video-two", "video-two", "child-two", Map.of("video", 1)))).code());
        var balance = f.tx.execute(s -> admin.balance("child-two", "a"));
        var deduct = new AdminPointService.Change(UUID.randomUUID(), "a", AdminPointService.Direction.DEDUCT,
                new BigDecimal("100"), balance.version(), "测试扣减");
        var latch = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var credit = pool.submit(() -> {
                latch.await();
                f.tx.executeWithoutResult(s -> recharge.settle(order.getValue()));
                return true;
            });
            var adjustment = pool.submit(() -> {
                latch.await();
                try { f.tx.execute(s -> admin.adjust("child-two", deduct)); return true; }
                catch (BizException conflict) { assertEquals(409, conflict.code()); return false; }
            });
            latch.countDown();
            assertTrue(credit.get(15, TimeUnit.SECONDS));
            if (!adjustment.get(15, TimeUnit.SECONDS)) {
                var current = f.tx.execute(s -> admin.balance("child-two", "a"));
                f.tx.execute(s -> admin.adjust("child-two", new AdminPointService.Change(UUID.randomUUID(), "a", deduct.direction(),
                        deduct.amount(), current.version(), deduct.reason())));
            }
        }
        f.tx.executeWithoutResult(s -> {
            recharge.settle(order.getValue());
            charges.finishAll("a", "child-video-one");
        });
        var shared = f.tx.execute(s -> admin.balance("child-one", "a"));
        assertEquals(new BigDecimal("1060.0000"), shared.available());
        assertEquals(new BigDecimal("240.0000"), shared.consumed());
        assertEquals(0, shared.frozen().signum());
        assertEquals(shared, f.tx.execute(s -> admin.balance("a", "a")));
        assertEquals(shared, f.tx.execute(s -> admin.balance("child-two", "a")));
        assertEquals(0, f.points.balance("b").available().signum());
        assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM point_ledger WHERE tenant_id='a' AND event_key=?", Integer.class,
                "recharge:" + order.getValue().id()));
        verify(audit, times(1)).audit(any(AdminAccountStore.Account.class), eq("POINT_ADJUST"), eq("SUCCESS"), eq("child-two"), eq("a"), anyString());
    }

    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void rechargeUsesPurchasedPointsAndOnlyCreditsAValidCallbackOnce(String mode) throws Exception {
        var f=fixture(mode);
        var tenant=new TenantId("a");
        var foundation=new WorkspaceFoundation(f.jdbc);
        var access=mock(WorkspaceAccess.class);
        var role=com.lynx.ad.common.security.MemberRole.OWNER;
        when(access.require(role)).thenReturn(new WorkspaceAccess.Membership("member","a","a",role,1));
        var pricing=new WorkspacePricing();
        pricing.setEnabled(true);
        var orders=mock(OrderRepository.class);
        var payments=mock(PaymentRepository.class);
        var subscriptions=mock(SubscriptionSupport.class);
        var recharge=new WorkspacePointOrders(pricing,access,foundation,orders,f.points,f.jdbc,subscriptions);
        var orderState=new java.util.concurrent.atomic.AtomicReference<BillingOrder>();
        when(orders.findById(eq(tenant),anyString())).thenAnswer(call -> Optional.ofNullable(orderState.get()));
        when(orders.findByIdForUpdate(eq(tenant),anyString())).thenAnswer(call -> Optional.ofNullable(orderState.get()));
        doAnswer(call -> { orderState.set(call.getArgument(0)); return null; }).when(orders).save(any());
        when(subscriptions.effective(eq(tenant), any())).thenReturn(Optional.empty());
        assertTrue(recharge.options().isEmpty());
        assertThrows(BizException.class, () -> f.tx.execute(s -> recharge.create("r99","recharge-request")));
        when(subscriptions.effective(eq(tenant), any())).thenReturn(Optional.of(activeSubscription(tenant, 0)));
        assertTrue(recharge.options().isEmpty());
        assertThrows(BizException.class, () -> f.tx.execute(s -> recharge.create("r99","recharge-request")));
        when(subscriptions.effective(eq(tenant), any())).thenReturn(Optional.of(activeSubscription(tenant, 9900)));
        assertFalse(recharge.options().isEmpty());
        var created=f.tx.execute(s -> recharge.create("r99","recharge-request"));
        assertNotNull(created);
        var order=orderState.get();
        pricing.getRecharge().put("r99",new WorkspacePricing.Recharge(12900,2000));
        assertEquals(created,f.tx.execute(s -> recharge.create("r99","recharge-request")));
        var payment=new BillingPayment("wechat-payment",tenant,order.id(),PayChannel.WECHAT,PaymentStatus.CREATED,
                9900,null,null,Instant.now(),null);
        when(payments.findByIdOnly(payment.id())).thenReturn(Optional.of(payment));
        var paymentGateway=mock(PaymentPort.class);
        var checkout=new PaymentCommandService(orders,payments,mock(SubscriptionRepository.class),subscriptions,
                paymentGateway,null,null,null,null,null);
        org.springframework.test.util.ReflectionTestUtils.setField(checkout,"pointOrders",recharge);
        org.springframework.test.util.ReflectionTestUtils.setField(checkout,"foundation",foundation);
        when(paymentGateway.parseAndVerifyNotify(eq("callback"),anyMap()))
                .thenReturn(new PaymentPort.PaymentNotify(payment.id(),true,"wechat-trade",9899));
        assertThrows(BizException.class,() -> f.tx.execute(s -> checkout.wechatNotify("callback",Map.of())));
        assertEquals(0,f.points.balance("a").available().signum());
        when(paymentGateway.parseAndVerifyNotify(eq("callback"),anyMap()))
                .thenReturn(new PaymentPort.PaymentNotify(payment.id(),true,"wechat-trade",9900));
        f.tx.execute(s -> checkout.wechatNotify("callback",Map.of()));
        f.tx.execute(s -> checkout.wechatNotify("callback",Map.of()));
        assertEquals(OrderStatus.PAID,order.status());
        assertEquals(0,new BigDecimal("1000").compareTo(f.points.balance("a").available()));
        assertEquals(1,f.points.ledger("a",1,10).size());
        assertEquals(0,f.points.balance("b").available().signum());
    }
}
