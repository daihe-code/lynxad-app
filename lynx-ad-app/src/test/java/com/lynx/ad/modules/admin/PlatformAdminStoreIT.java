package com.lynx.ad.modules.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.admin.application.*;
import com.lynx.ad.modules.admin.infrastructure.*;
import com.lynx.ad.modules.billing.application.*;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import com.lynx.ad.modules.billing.domain.port.PlanRepository;
import com.lynx.ad.modules.billing.infrastructure.persistence.SubscriptionPersistenceFixture;
import com.lynx.ad.modules.tenant.application.UserLoginJournal;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import static com.lynx.ad.modules.admin.infrastructure.AdminAccountStore.*;

/** All mutations occur in a fresh verification schema, never the user's database. */
@EnabledIfEnvironmentVariable(named="LYNXAD_ADMIN_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:(?:3306|55406)/lynxad_admin_verify_[a-zA-Z0-9_]+\\?.*")
class PlatformAdminStoreIT {
    static String database,server,user,password;static boolean created;static JdbcTemplate jdbc;static TransactionTemplate tx;static javax.sql.DataSource dataSource;
    static long backfilled;static String legacyMethod;static final ObjectMapper json=new ObjectMapper();
    AdminAccountStore admins;AdminAuthService auth;AdminAccountStore.Account admin;AdminPackageService packages;AdminUserQuery queries;Instant end;
    EntitlementQueryService entitlements;
    @BeforeAll static void setup()throws Exception {
        String url=System.getenv("LYNXAD_ADMIN_TEST_URL");database=url.substring(url.indexOf("/lynxad_")+1,url.indexOf('?'));if(!database.matches("lynxad_admin_verify_[a-zA-Z0-9_]+"))throw new IllegalArgumentException("Unsafe verification schema");
        server=url.replace("/"+database+"?","/?");user=System.getenv("LYNXAD_ADMIN_TEST_USER");password=System.getenv("LYNXAD_ADMIN_TEST_PASSWORD");
        try(var c=DriverManager.getConnection(server,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+database);created=true;}
        var ds=new DriverManagerDataSource(url,user,password);dataSource=ds;jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        try(var c=ds.getConnection()) {for(String script:List.of("V1__init.sql","V3__v02_account_audit_notify.sql","V5__v04_subscription.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/"+script));}
        jdbc.update("INSERT INTO sys_user(id,phone,status,created_at) VALUES('legacy-user','13900000999','ACTIVE',?)",ts(Instant.now()));
        jdbc.update("INSERT INTO audit_record(id,user_id,actor_name,action_type,result,created_at) VALUES('legacy-login','legacy-user','旧用户','LOGIN','SUCCESS',?)",ts(Instant.now()));
        try (var c=ds.getConnection()) {
            for (String script : List.of("V7__api_oauth_operator_audit.sql", "V16__live_qianchuan_oauth.sql",
                    "V32__platform_admin.sql", "V52__workspace_account_system.sql")) {
                ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration-mysql/" + script));
            }
        }
        jdbc.update("INSERT INTO platform_admin_account (id, username, display_name, password_hash, status, created_at, updated_at) VALUES ('legacy-admin', 'legacy-admin', '旧管理员', 'legacy-hash', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO platform_admin_session (id, admin_id, created_at, expires_at) VALUES ('legacy-session', 'legacy-admin', CURRENT_TIMESTAMP, ?)", ts(Instant.now().plusSeconds(3600)));
        try (var c = ds.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration-mysql/V57__platform_admin_sms.sql"));
        }
        assertEquals(List.of("13810900372", "18801061264"), jdbc.queryForList(
                "SELECT phone FROM platform_admin_account WHERE can_manage_admins = TRUE ORDER BY phone", String.class));
        assertFalse(new AdminAccountStore(jdbc).validSession("legacy-admin", "legacy-session", Instant.now()));
        backfilled=jdbc.queryForObject("SELECT COUNT(*) FROM user_login_record",Long.class);legacyMethod=jdbc.queryForObject("SELECT login_method FROM user_login_record WHERE id='legacy-login'",String.class);
    }
    @AfterAll static void cleanup()throws Exception {if(created&&database.matches("lynxad_admin_verify_[a-zA-Z0-9_]+"))try(var c=DriverManager.getConnection(server,user,password);var s=c.createStatement()){s.execute("DROP DATABASE "+database);}}
    @BeforeEach void seed() throws Exception {
        TenantContext.clear();RequestMeta.clear();
        for(String table:List.of("platform_admin_session","platform_admin_audit","platform_package_adjustment","user_login_record","audit_record","billing_payment","billing_order","billing_subscription","billing_trial","point_ledger","point_wallet","client_space","tenant_member","user_profile","sys_user","sys_tenant","platform_admin_account"))jdbc.update("DELETE FROM "+table);
        var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);end=now.plusSeconds(10*86400L);
        for(String id:List.of("t1","t2"))jdbc.update("INSERT INTO sys_tenant(id,name,status,created_at) VALUES(?,?,'ACTIVE',?)",id,"账户"+id,ts(now));
        for(int i=1;i<=2;i++){jdbc.update("INSERT INTO sys_user(id,phone,status,created_at) VALUES(?,?,'ACTIVE',?)","u"+i,"1390000100"+i,ts(now));jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,created_at) VALUES(?,?,'OWNER',?)","t"+i,"u"+i,ts(now));}
        jdbc.update("INSERT INTO user_profile(user_id,nickname,company_name,email,completed_at) VALUES('u1','测试用户','测试公司','test@example.com',?)",ts(now));
        jdbc.update("INSERT INTO billing_trial(tenant_id,started_at,expires_at) VALUES('t1',?,?)",ts(now),ts(end));
        jdbc.update("INSERT INTO billing_trial(tenant_id,started_at,expires_at) VALUES('t2',?,?)",ts(now),ts(end));
        admins=new AdminAccountStore(jdbc);
        jdbc.update("INSERT INTO platform_admin_account (id, username, display_name, status, phone, can_manage_admins, created_at, updated_at) VALUES ('integration-admin', 'integration-admin', '测试管理员', 'ACTIVE', '13900001009', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        admin=admins.activePhone(new PhoneNumber("13900001009")).orElseThrow();
        auth=mock(AdminAuthService.class);when(auth.require()).thenReturn(admin);queries=new AdminUserQuery(jdbc);
        var pricing = new WorkspacePricing();
        pricing.setEnabled(true);
        var foundation = new WorkspaceFoundation(jdbc);
        var subscriptions = new SubscriptionSupport(SubscriptionPersistenceFixture.create(dataSource));
        var trials = mock(BillingTrialService.class);
        entitlements = new EntitlementQueryService(subscriptions, trials, mock(PlanRepository.class));
        entitlements.workspacePricing(pricing);
        var points = new WorkspacePointOrders(pricing, null, foundation, mock(OrderRepository.class),
                new PointWalletService(jdbc, foundation), jdbc, subscriptions, trials);
        packages = new AdminPackageService(auth, admins, jdbc, json, points);
    }
    @AfterEach void clearContext(){TenantContext.clear();RequestMeta.clear();}
    AdminPackageService.ExtendRequest request(String type,int days,Instant before){return new AdminPackageService.ExtendRequest(UUID.randomUUID().toString(),type,"PROFESSIONAL",days,before,"测试客户补偿");}
    AdminUserQuery.Adjustment extend(String id,AdminPackageService.ExtendRequest r){return tx.execute(status->packages.extend(id,r));}
    @Test void migrationPreservesHistoricalLoginRecords(){assertEquals(1,backfilled);assertEquals("LEGACY",legacyMethod);}
    @Test void trialExtensionStartsFromFutureExpiryAndDoesNotChangeOtherAccounts() {
        var result=extend("u1",request("TRIAL",365,end));assertEquals(end.plusSeconds(365*86400L),result.afterEnd());
        assertEquals(result.afterEnd(),queries.detail("u1").trial().expiresAt());assertEquals(end,queries.detail("u2").trial().expiresAt());assertEquals(1,queries.adjustments("u1",1,20).total());assertEquals(1,queries.audit(1,20).total());
    }
    @Test void expiredTrialResumesFromNowAndReplayCannotDoubleExtend() {
        var expired=Instant.now().minusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);jdbc.update("UPDATE billing_trial SET expires_at=? WHERE tenant_id='t1'",ts(expired));
        var r=request("TRIAL",30,expired);var before=Instant.now();var first=extend("u1",r);var second=extend("u1",r);
        assertEquals(first,second);assertTrue(first.afterEnd().isAfter(before.plusSeconds(30*86400L-1)));assertEquals(1,queries.adjustments("u1",1,20).total());assertEquals(1,queries.audit(1,20).total());
    }
    @Test void rejectsStaleSnapshotAndIdempotencyKeyReusedForAnotherGrant() {
        var r=request("TRIAL",7,end);extend("u1",r);
        assertEquals(409,assertThrows(BizException.class,()->extend("u1",request("TRIAL",7,end))).code());
        var changed=new AdminPackageService.ExtendRequest(r.requestId(),"TRIAL","PROFESSIONAL",30,end,"不同请求");assertEquals(409,assertThrows(BizException.class,()->extend("u1",changed)).code());assertEquals(1,queries.adjustments("u1",1,20).total());
    }
    @Test void subscriptionExtensionPreservesPaidFactsRenewalAndScheduledPlan() {
        jdbc.update("INSERT INTO billing_subscription(tenant_id,plan_code,status,period_start,period_end,auto_renew,pay_method,paid_fen_this_period,next_plan_code,updated_at) VALUES('t1','PROFESSIONAL','PENDING_DOWNGRADE',?,?,TRUE,'WECHAT',79900,'BASIC',?)",ts(end.minusSeconds(30*86400L)),ts(end),ts(Instant.now()));
        jdbc.update("INSERT INTO billing_order(id,tenant_id,plan_code,type,status,list_price_fen,payable_fen,created_at) VALUES('paid-order','t1','PROFESSIONAL','NEW','PAID',79900,79900,?)",ts(Instant.now()));
        var result=extend("u1",request("SUBSCRIPTION",30,end));var sub=queries.detail("u1").subscription();
        assertEquals(end.plusSeconds(30*86400L),result.afterEnd());assertTrue(sub.autoRenew());assertEquals("WECHAT",sub.payMethod());assertEquals("BASIC",sub.nextPlanCode());assertEquals("PENDING_DOWNGRADE",sub.status());
        assertEquals(79900,jdbc.queryForObject("SELECT paid_fen_this_period FROM billing_subscription WHERE tenant_id='t1'",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM billing_order",Integer.class));
        assertThrows(BizException.class,()->extend("u1",request("TRIAL",30,end)));
    }
    @Test void newManualPackageDoesNotFabricatePaidOrderAndIsSharedWithMembers() {
        jdbc.update("INSERT INTO sys_user(id,phone,status,created_at) VALUES('u3','13900001003','ACTIVE',?)",ts(Instant.now()));jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,created_at) VALUES('t1','u3','MEMBER',?)",ts(Instant.now()));
        var grant=extend("u1",request("SUBSCRIPTION",90,null));var detail=queries.detail("u3");assertEquals(grant.afterEnd(),detail.subscription().expiresAt());assertEquals("ADMIN",detail.subscription().payMethod());assertFalse(detail.subscription().autoRenew());assertEquals(2,detail.memberCount());assertEquals(1,queries.adjustments("u3",1,20).total());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM billing_order",Integer.class));
    }
    @Test void opensAndSwitchesSharedPackageWithoutPaymentAndCreditsEachPeriodOnlyOnce() {
        jdbc.update("INSERT INTO sys_user(id,phone,status,created_at) VALUES('u3','13900001003','ACTIVE',?)", ts(Instant.now()));
        jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,created_at) VALUES('t1','u3','VIEWER',?)", ts(Instant.now()));
        pendingPackageOrder();
        jdbc.update("""
                INSERT INTO billing_order(id,tenant_id,plan_code,type,status,list_price_fen,payable_fen)
                VALUES('points-order','t1','BASIC','POINT_RECHARGE','PENDING_PAY',9900,9900)
                """);
        var open = changeRequest("BASIC", 30, null);
        var opened = change("u1", open);
        assertEquals(opened, change("u1", open));
        var basic = queries.detail("u3").subscription();
        assertEquals("BASIC", basic.planCode());
        assertEquals("ADMIN", basic.payMethod());
        assertFalse(basic.autoRenew());
        assertEquals(opened.afterEnd(), basic.expiresAt());
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM billing_order WHERE id='pending-order'", String.class));
        assertEquals("PENDING_PAY", jdbc.queryForObject("SELECT status FROM billing_order WHERE id='points-order'", String.class));
        assertEquals(3000, availablePoints());
        assertEquals(2, entitlements.childAccountLimit(new TenantId("t1")));

        var updated = change("u3", changeRequest("FLAGSHIP", null, basic.updatedAt()));
        assertEquals("PLAN_CHANGE", updated.type());
        assertEquals(opened.afterEnd(), updated.afterEnd());
        assertEquals("FLAGSHIP", queries.detail("u1").subscription().planCode());
        assertEquals(30000, availablePoints());
        assertEquals(10, entitlements.childAccountLimit(new TenantId("t1")));
        assertEquals(409, assertThrows(BizException.class, () -> change("u1", changeRequest("BASIC", null, basic.updatedAt()))).code());
        var flagship = queries.detail("u1").subscription();
        change("u1", changeRequest("BASIC", null, flagship.updatedAt()));
        change("u1", changeRequest("FLAGSHIP", null, queries.detail("u1").subscription().updatedAt()));
        assertEquals(30000, availablePoints());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM billing_order WHERE status='PAID'", Integer.class));
        assertEquals(4, queries.adjustments("u1", 1, 20).total());
        assertEquals(4, queries.audit(1, 20).total());
        assertNull(queries.detail("u2").subscription());
    }

    @Test void changesOnlyTheSelectedWorkspaceForAUserWithMultipleMemberships() {
        jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role) VALUES('t1','u2','VIEWER')");
        assertEquals("t2", queries.detail("u2").user().tenantId());
        assertEquals("t1", queries.detail("u2", "t1").user().tenantId());
        assertThrows(BizException.class, () -> queries.detail("u1", "t2"));
        var request = changeRequest("BASIC", 30, null);
        assertEquals("t1", change("u2", request).tenantId());
        assertEquals("BASIC", queries.detail("u2", "t1").subscription().planCode());
        assertNull(queries.detail("u2", "t2").subscription());
    }

    @Test void verifiesAnInFlightCheckoutBeforeChangingThePackage() {
        pendingPackageOrder();
        jdbc.update("""
                INSERT INTO billing_payment(id,tenant_id,order_id,channel,status,amount_fen)
                VALUES('started-payment','t1','pending-order','WECHAT','CREATED',79900)
                """);
        var open = changeRequest("BASIC", 30, null);
        assertEquals(1904, assertThrows(BizException.class, () -> change("u1", open)).code());
        assertNull(queries.detail("u1").subscription());
        assertEquals(0, queries.adjustments("u1", 1, 20).total());
    }

    private void pendingPackageOrder() {
        jdbc.update("""
                INSERT INTO billing_order(id,tenant_id,plan_code,type,status,list_price_fen,payable_fen)
                VALUES('pending-order','t1','PROFESSIONAL','FIRST_SUBSCRIBE','PENDING_PAY',79900,79900)
                """);
    }

    private AdminPackageService.ChangeRequest changeRequest(String plan, Integer days, Instant version) {
        return new AdminPackageService.ChangeRequest(UUID.randomUUID().toString(), "t1", plan, days, version, "测试套餐调整");
    }

    private AdminUserQuery.Adjustment change(String user, AdminPackageService.ChangeRequest request) {
        return tx.execute(status -> packages.change(user, request));
    }

    private int availablePoints() {
        return jdbc.queryForObject("SELECT available FROM point_wallet WHERE tenant_id='t1'", Integer.class);
    }
    @Test void invalidDaysMissingReasonAndUnonboardedUserCannotBeGranted() {
        assertThrows(BizException.class,()->extend("u1",request("TRIAL",0,end)));assertThrows(BizException.class,()->extend("u1",request("TRIAL",3651,end)));
        var r=new AdminPackageService.ExtendRequest(UUID.randomUUID().toString(),"TRIAL",null,30,end,"");assertThrows(BizException.class,()->extend("u1",r));
        jdbc.update("INSERT INTO sys_user(id,phone,status,created_at) VALUES('u0','13900001000','ACTIVE',?)",ts(Instant.now()));assertThrows(BizException.class,()->extend("u0",request("TRIAL",30,null)));assertEquals(0,queries.adjustments(null,1,20).total());
    }
    @Test void unresolvedCheckoutCannotBeShortenedOrOverwrittenByAdministrativeGrant() {
        jdbc.update("INSERT INTO billing_order(id,tenant_id,plan_code,type,status,list_price_fen,payable_fen,created_at) VALUES('pending-order','t1','PROFESSIONAL','FIRST_SUBSCRIBE','PENDING_PAY',79900,79900,?)",ts(Instant.now()));
        assertEquals(1904,assertThrows(BizException.class,()->extend("u1",request("TRIAL",30,end))).code());assertEquals(end,queries.detail("u1").trial().expiresAt());assertEquals(0,queries.adjustments("u1",1,20).total());
    }
    @Test void auditFailureRollsBackExpiryAndReceiptTogether() {
        var failing=spy(admins);doThrow(new IllegalStateException("audit unavailable")).when(failing).audit(any(AdminAccountStore.Account.class),eq("PACKAGE_EXTEND"),anyString(),anyString(),anyString(),anyString());
        var service=new AdminPackageService(auth,failing,jdbc,json,mock(WorkspacePointOrders.class));assertThrows(IllegalStateException.class,()->tx.execute(status->service.extend("u1",request("TRIAL",30,end))));
        assertEquals(end,queries.detail("u1").trial().expiresAt());assertEquals(0,queries.adjustments(null,1,20).total());
    }
    @Test void simultaneousReplayCommitsOnlyOneGrant()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var r=request("TRIAL",30,end);
        try{var one=pool.submit(()->extend("u1",r));var two=pool.submit(()->extend("u1",r));assertEquals(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS));assertEquals(end.plusSeconds(30*86400L),queries.detail("u1").trial().expiresAt());assertEquals(1,queries.adjustments("u1",1,20).total());}finally{pool.shutdownNow();}
    }
    void subscription() { jdbc.update("INSERT INTO billing_subscription(tenant_id,plan_code,status,period_start,period_end,auto_renew,pay_method,paid_fen_this_period,updated_at) VALUES('t1','PROFESSIONAL','ACTIVE',?,?,TRUE,'WECHAT',79900,?)",ts(end.minusSeconds(30*86400L)),ts(end),ts(Instant.now())); }
    @Test void customerRenewalSettingCannotLoadStaleExpiryWhileAdministratorGrantIsCommitting()throws Exception {
        subscription();var repository=com.lynx.ad.modules.billing.infrastructure.persistence.SubscriptionPersistenceFixture.create(dataSource);
        var written=new CountDownLatch(1);var release=new CountDownLatch(1);var customerStarted=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var grant=pool.submit(()->tx.execute(status->{var a=packages.extend("u1",request("SUBSCRIPTION",30,end));written.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timed out");}catch(InterruptedException e){throw new IllegalStateException(e);}return a;}));
            assertTrue(written.await(5,TimeUnit.SECONDS));
            var setting=pool.submit(()->tx.execute(status->{customerStarted.countDown();var s=repository.findByTenant(new TenantId("t1")).orElseThrow();s.setAutoRenew(false,Instant.now());repository.save(s);return s.periodEnd();}));
            assertTrue(customerStarted.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->setting.get(200,TimeUnit.MILLISECONDS));release.countDown();
            assertEquals(grant.get(5,TimeUnit.SECONDS).afterEnd(),setting.get(5,TimeUnit.SECONDS));assertEquals(end.plusSeconds(30*86400L),queries.detail("u1").subscription().expiresAt());assertFalse(queries.detail("u1").subscription().autoRenew());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void customerWriteStartedFirstAndLaterGrantBothSurvive()throws Exception {
        subscription();var repository=com.lynx.ad.modules.billing.infrastructure.persistence.SubscriptionPersistenceFixture.create(dataSource);
        var loaded=new CountDownLatch(1);var release=new CountDownLatch(1);var adminStarted=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var setting=pool.submit(()->tx.execute(status->{var s=repository.findByTenant(new TenantId("t1")).orElseThrow();loaded.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timed out");}catch(InterruptedException e){throw new IllegalStateException(e);}s.setAutoRenew(false,Instant.now());repository.save(s);return true;}));
            assertTrue(loaded.await(5,TimeUnit.SECONDS));var grant=pool.submit(()->{adminStarted.countDown();return extend("u1",request("SUBSCRIPTION",30,end));});
            assertTrue(adminStarted.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->grant.get(200,TimeUnit.MILLISECONDS));release.countDown();assertTrue(setting.get(5,TimeUnit.SECONDS));assertEquals(end.plusSeconds(30*86400L),grant.get(5,TimeUnit.SECONDS).afterEnd());assertFalse(queries.detail("u1").subscription().autoRenew());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void passwordChangeAndLogoutActuallyRevokeStoredSessions() {
        var now=Instant.now();admins.login(admin,"session-one",now.plusSeconds(7200),now);admins.login(admin,"session-two",now.plusSeconds(7200),now);assertTrue(admins.validSession(admin.id(),"session-one",now));
        admins.logout(admin,"session-one");assertFalse(admins.validSession(admin.id(),"session-one",now));assertTrue(admins.validSession(admin.id(),"session-two",now));
        var delegated = tx.execute(status -> admins.grantPhone(admin, new PhoneNumber("13900001008")));
        assertEquals(delegated, admins.activePhone(new PhoneNumber("13900001008")).orElseThrow());
        assertFalse(delegated.canManageAdmins());
        assertTrue(admins.validSession(admin.id(),"session-two",now));
        assertEquals(4,queries.audit(1,20).total());
    }
    @Test void failedAndSuccessfulUserLoginsCaptureMetadataWithoutSecrets() {
        var repo=mock(UserRepository.class);var fixtureUser=new User(new UserId("u1"),new PhoneNumber("13900001001"),null,null);
        when(repo.findByPhone(any())).thenReturn(Optional.of(fixtureUser));when(repo.findById(any())).thenReturn(Optional.of(fixtureUser));var jwt=new JwtService(new LynxAdProperties(),repo);
        var journal=new UserLoginJournal(jdbc,jwt,repo);RequestMeta.set("127.0.0.1","FixtureBrowser/1.0");journal.record("13900001001","PASSWORD",null);
        String token=jwt.issue(new UserId("u1"),Optional.of(new TenantId("t1")),MemberRole.OWNER,SysRole.USER);journal.record("13900001001","PASSWORD",new com.lynx.ad.modules.tenant.api.dto.LoginResponse(token,3600,false,false));
        var logs=queries.logins("u1",null,null,null,null,1,20);assertEquals(2,logs.total());assertEquals(1,queries.logins("u1",null,"FAILURE",null,null,1,20).total());assertEquals(1,queries.detail("u1").user().loginCount());assertTrue(logs.records().stream().allMatch(l->"127.0.0.1".equals(l.ip())));assertFalse(logs.toString().contains(token));
    }
    @Test void adminQueriesFilterAndPaginateWithoutExposingPasswordHash()throws Exception {
        assertEquals(2,queries.users(null,"TRIAL",1,20).total());assertEquals(1,queries.users("测试公司",null,1,20).total());assertEquals(1,queries.users(null,null,2,1).records().size());
        assertEquals(0,queries.users("' OR 1=1 --",null,1,20).total());assertThrows(BizException.class,()->queries.users(null,null,0,20));assertThrows(BizException.class,()->queries.users(null,null,1,101));
        assertFalse(json.findAndRegisterModules().writeValueAsString(queries.detail("u1")).contains("password_hash"));
        assertThrows(BizException.class,()->queries.logins(null,null,null,LocalDate.now(),LocalDate.now().minusDays(1),1,20));
    }
}
