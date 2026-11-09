package com.lynx.ad.modules.tenant.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.api.GlobalExceptionHandler;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.admin.application.AdminAuthService;
import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore;
import com.lynx.ad.modules.billing.application.*;
import com.lynx.ad.modules.billing.domain.port.TrialRepository;
import com.lynx.ad.modules.billing.infrastructure.persistence.MpTrialRepository;
import com.lynx.ad.modules.tenant.api.*;
import com.lynx.ad.modules.tenant.application.*;
import com.lynx.ad.modules.tenant.domain.model.*;
import com.lynx.ad.modules.tenant.domain.port.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Real repository mappings, SQL row locks, transaction proxies, controllers, and signed sessions. */
class AccountInitializationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}

    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    UserRepository users;
    TenantRepository tenants;
    BillingTrialService trials;
    AccountInitializationService initialization;
    AuthCommandService auth;
    InvitationStore invitations;
    OtpStore otp;
    JwtService jwt;
    MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();

    void database(String mode) throws Exception {
        DataSource ds = new DriverManagerDataSource("jdbc:h2:mem:registration_" + UUID.randomUUID()
                + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(36) PRIMARY KEY,phone VARCHAR(20) UNIQUE NOT NULL,password_hash VARCHAR(128),status VARCHAR(16),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_tenant(id VARCHAR(36) PRIMARY KEY,name VARCHAR(128) NOT NULL,status VARCHAR(16),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE tenant_member(tenant_id VARCHAR(36) REFERENCES sys_tenant(id),user_id VARCHAR(36) REFERENCES sys_user(id),member_role VARCHAR(16),created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id,user_id))");
        jdbc.execute("CREATE TABLE user_profile(user_id VARCHAR(36) PRIMARY KEY REFERENCES sys_user(id),nickname VARCHAR(64),email VARCHAR(128),avatar_url VARCHAR(512),company_name VARCHAR(128),industry VARCHAR(64),position VARCHAR(64),monthly_budget VARCHAR(64),ad_experience VARCHAR(64),core_demand VARCHAR(128),company_scale VARCHAR(64),completed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE billing_trial(tenant_id VARCHAR(36) PRIMARY KEY,started_at TIMESTAMP NOT NULL,expires_at TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE user_welcome_ack(user_id VARCHAR(36) PRIMARY KEY REFERENCES sys_user(id),acknowledged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE tenant_legal_profile(tenant_id VARCHAR(36) PRIMARY KEY,company_name VARCHAR(128),credit_code VARCHAR(18),verification_status VARCHAR(16),review_reason VARCHAR(512),revision INT,submitted_at TIMESTAMP,reviewed_at TIMESTAMP,contact_name VARCHAR(64),industry VARCHAR(64),email VARCHAR(128),company_scale VARCHAR(64),contact_user_id VARCHAR(36))");
        jdbc.execute("ALTER TABLE tenant_member ADD membership_id VARCHAR(64)");
        jdbc.execute("ALTER TABLE tenant_member ADD status VARCHAR(16) DEFAULT 'ACTIVE' NOT NULL");
        jdbc.execute("ALTER TABLE tenant_member ADD permission_version BIGINT DEFAULT 1 NOT NULL");
        jdbc.execute("CREATE TABLE client_space(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(36),name VARCHAR(128),is_default BOOLEAN)");
        jdbc.execute("CREATE TABLE point_wallet(tenant_id VARCHAR(36) PRIMARY KEY,available NUMERIC(20,4) DEFAULT 0,frozen NUMERIC(20,4) DEFAULT 0,consumed NUMERIC(20,4) DEFAULT 0,version BIGINT DEFAULT 1)");
        jdbc.execute("CREATE TABLE workspace_session_revocation(session_id VARCHAR(64) PRIMARY KEY,expires_at TIMESTAMP)");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V53__workspace_team_and_points.sql")).execute(ds);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper : List.of(UserMapper.class, UserProfileMapper.class, TenantMapper.class, TenantMemberMapper.class)) config.addMapper(mapper);
        Class<?> trialMapper = Class.forName("com.lynx.ad.modules.billing.infrastructure.persistence.TrialMapper");
        config.addMapper(trialMapper);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        users = new MpUserRepository(session.getMapper(UserMapper.class), session.getMapper(UserProfileMapper.class));
        TrialRepository trialRepository = (TrialRepository) MpTrialRepository.class.getConstructor(trialMapper).newInstance(session.getMapper(trialMapper));
        otp = mock(OtpStore.class);
        when(otp.verifyAndConsume(any(), eq("123456"))).thenReturn(true);
        invitations = mock(InvitationStore.class);
        var properties = new LynxAdProperties();
        properties.getJwt().setSecret("registration-tests-only-32-bytes-secret");
        jwt = new JwtService(properties, users);
        trials = spy(new BillingTrialService(trialRepository, mock(SubscriptionSupport.class)));
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(ds));
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(WorkspaceFoundation.class, () -> new WorkspaceFoundation(jdbc));
        context.registerBean(WorkspaceAccess.class);
        context.registerBean(WorkspacePricing.class);
        context.registerBean(EntitlementQueryService.class, () -> {
            var limits=mock(EntitlementQueryService.class);
            when(limits.childAccountLimit(any())).thenReturn(1);
            return limits;
        });
        context.registerBean(WorkspaceTeamService.class);
        context.registerBean(UserRepository.class, () -> users);
        context.registerBean(TenantRepository.class, () -> new MpTenantRepository(session.getMapper(TenantMapper.class), session.getMapper(TenantMemberMapper.class)));
        context.registerBean(BillingTrialService.class, () -> trials);
        context.registerBean(OtpStore.class, () -> otp);
        context.registerBean(InvitationStore.class, () -> invitations);
        context.registerBean(SmsGateway.class, () -> mock(SmsGateway.class));
        context.registerBean(PasswordHasher.class, () -> new PasswordHasher() {
            public String hash(String raw) { return "test-hash:" + raw; }
            public boolean matches(String raw, String hashed) { return hash(raw).equals(hashed); }
        });
        context.registerBean(JwtService.class, () -> jwt);
        context.registerBean(LynxAdProperties.class, () -> properties);
        context.registerBean(AuditRecorder.class, () -> mock(AuditRecorder.class));
        context.registerBean(PasswordLoginGuard.class, () -> mock(PasswordLoginGuard.class));
        context.registerBean(AccountInitializationService.class);
        context.registerBean(AuthCommandService.class);
        context.refresh();
        users = context.getBean(UserRepository.class);
        tenants = context.getBean(TenantRepository.class);
        initialization = context.getBean(AccountInitializationService.class);
        auth = context.getBean(AuthCommandService.class);
        var queries = new TenantQuerySupport(users, tenants, trials, mock(SubscriptionQueryService.class), properties);
        mvc = MockMvcBuilders.standaloneSetup(
                new TenantAuthController(auth, mock(UserLoginJournal.class)),
                new TenantUserController(queries, mock(OnboardingCommandService.class), mock(AccountCommandService.class), auth),
                new AccountLegalController(new AccountLegalService(jdbc, queries, mock(AdminAuthService.class), mock(AdminAccountStore.class), mock(AuditRecorder.class))),
                new WelcomeController(jdbc, context.getBean(PlatformTransactionManager.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new JwtAuthFilter(jwt)).build();
    }

    @AfterEach void close() { TenantContext.clear(); if (context != null) context.close(); }

    JsonNode call(String method, String path, String body, String token) throws Exception {
        var request = switch (method) { case "POST" -> post(path); case "PUT" -> put(path); default -> get(path); };
        request.contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(body);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString());
    }
    JsonNode login(String phone) throws Exception {
        var result = call("POST", "/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null);
        assertEquals(0, result.path("code").asInt(), result.toString());
        return result.path("data");
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @ParameterizedTest @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void firstLoginCreatesUsableOwnerSessionAndTrialWithoutCollectingProfile(String mode) throws Exception {
        database(mode);
        JsonNode result = login("13900008801");
        assertFalse(result.path("needOnboarding").asBoolean());
        String token = result.path("token").asText();
        var principal = jwt.parse(token);
        assertEquals(MemberRole.OWNER, principal.memberRole().orElseThrow());
        assertTrue(principal.tenantId().isPresent());
        var me = call("GET", "/api/v1/tenant/users/me", null, token).path("data");
        assertFalse(me.path("needOnboarding").asBoolean());
        assertTrue(me.path("trialActive").asBoolean());
        assertFalse(me.path("hasPassword").asBoolean());
        assertTrue(me.path("email").isNull());
        assertTrue(me.path("companyName").isNull());
        assertEquals(0, count("user_profile"));
        assertNull(users.findById(principal.userId()).orElseThrow().passwordHash());
        assertEquals("我的工作空间", tenants.findByUserId(principal.userId()).orElseThrow().name());
        var legal = call("GET", "/api/v1/tenant/account/legal-profile", null, token).path("data");
        assertTrue(legal.path("companyName").isNull(), "A workspace display name is not a supplied company name");
        assertEquals("UNSUBMITTED", legal.path("verificationStatus").asText());
        var trial = trials.current(principal.tenantId().orElseThrow(), Instant.now());
        assertEquals(Duration.ofDays(7), Duration.between(trial.startedAt(), trial.expiresAt()));
        assertFalse(call("GET", "/api/v1/tenant/users/me/welcome", null, token).path("data").path("acknowledged").asBoolean());
        assertTrue(call("PUT", "/api/v1/tenant/users/me/welcome", "{}", token).path("data").path("acknowledged").asBoolean());
        assertTrue(call("GET", "/api/v1/tenant/users/me/welcome", null, token).path("data").path("acknowledged").asBoolean());
    }

    @ParameterizedTest @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void simultaneousInitializationCreatesOnlyOneWorkspaceAndNeverRenewsTrial(String mode) throws Exception {
        database(mode);
        User user = User.register(new PhoneNumber("13900008802")); users.save(user);
        var ready = new CountDownLatch(6); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) results.add(pool.submit(() -> { ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS)); return initialization.initialize(user.id()).id().value(); }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            Set<String> ids = new HashSet<>();
            for (var result : results) ids.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(1, ids.size());
        }
        assertEquals(1, count("sys_tenant")); assertEquals(1, count("tenant_member")); assertEquals(1, count("billing_trial"));
        var tenant = tenants.findByUserId(user.id()).orElseThrow();
        var expired = Instant.parse("2025-01-08T00:00:00Z");
        jdbc.update("UPDATE billing_trial SET started_at=?,expires_at=?", java.sql.Timestamp.from(expired.minus(Duration.ofDays(7))), java.sql.Timestamp.from(expired));
        var again = login(user.phone().value());
        assertEquals(tenant.id(), jwt.parse(again.path("token").asText()).tenantId().orElseThrow());
        assertEquals(expired, trials.current(tenant.id(), Instant.now()).expiresAt());
        assertFalse(trials.current(tenant.id(), Instant.now()).active());
        verify(trials, times(1)).start(eq(tenant.id()), any());
    }

    @ParameterizedTest @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void invitedMemberJoinsExistingWorkspaceWithoutStartingAnotherTrial(String mode) throws Exception {
        database(mode);
        var owner = jwt.parse(login("13900008803").path("token").asText());
        PhoneNumber invited = new PhoneNumber("13900008804");
        var team=context.getBean(WorkspaceTeamService.class);
        String invitation;
        try {
            TenantContext.set(owner);
            invitation=team.invite(invited,MemberRole.OPERATOR).token();
            assertThrows(com.lynx.ad.common.exception.BizException.class,
                    () -> team.invite(new PhoneNumber("13900008809"),MemberRole.VIEWER));
        } finally { TenantContext.clear(); }
        var bad=call("POST","/api/v1/tenant/auth/sms/login",
                json.writeValueAsString(Map.of("phone","13900008809","code","123456","invite",invitation)),null);
        assertNotEquals(0,bad.path("code").asInt());
        var result=call("POST","/api/v1/tenant/auth/sms/login",
                json.writeValueAsString(Map.of("phone",invited.value(),"code","123456","invite",invitation)),null);
        assertEquals(0,result.path("code").asInt(),result.toString());
        var member = jwt.parse(result.path("data").path("token").asText());
        assertEquals(owner.tenantId(), member.tenantId());
        assertEquals(MemberRole.OPERATOR, member.memberRole().orElseThrow());
        assertEquals(1, count("sys_tenant")); assertEquals(2, count("tenant_member")); assertEquals(1, count("billing_trial"));
        assertEquals(0, count("user_profile"));
        try {
            TenantContext.set(owner);
            var child=team.list().members().getFirst();
            team.update(child.id(),MemberRole.OPERATOR,false);
            assertEquals(0,team.list().used());
            TenantContext.set(member);
            assertThrows(com.lynx.ad.common.exception.BizException.class,() -> context.getBean(WorkspaceAccess.class).current());
        } finally { TenantContext.clear(); }
        assertThrows(com.lynx.ad.common.exception.BizException.class,() -> auth.smsLogin(invited,"123456"));
        assertEquals(1,count("sys_tenant"));
    }

    @ParameterizedTest @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void oldTenantlessSessionCanInitializeAndRefreshItsClaimsIdempotently(String mode) throws Exception {
        database(mode);
        User legacy = User.register(new PhoneNumber("13900008805")); users.save(legacy);
        String old = jwt.issue(legacy.id(), Optional.empty(), null, SysRole.USER);
        assertTrue(call("GET", "/api/v1/tenant/users/me", null, old).path("data").path("needOnboarding").asBoolean());
        var initialized = call("POST", "/api/v1/tenant/users/me/initialize", "{}", old);
        assertEquals(0, initialized.path("code").asInt());
        String token = initialized.path("data").path("token").asText();
        var principal = jwt.parse(token);
        assertEquals(legacy.id(), principal.userId());
        assertEquals(MemberRole.OWNER, principal.memberRole().orElseThrow());
        assertTrue(principal.tenantId().isPresent());
        assertFalse(call("GET", "/api/v1/tenant/users/me", null, token).path("data").path("needOnboarding").asBoolean());
        assertTrue(call("GET", "/api/v1/tenant/users/me", null, old).path("data").path("needOnboarding").asBoolean(), "A pre-existing tenantless JWT must still request refreshed claims after another session initializes the workspace");
        var replay = call("POST", "/api/v1/tenant/users/me/initialize", "{}", old);
        assertEquals(principal.tenantId(), jwt.parse(replay.path("data").path("token").asText()).tenantId());
        assertEquals(1, count("sys_tenant")); assertEquals(1, count("billing_trial"));
    }

    @ParameterizedTest @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void failedAuthenticationAndAnonymousRequestsCannotInitialize(String mode) throws Exception {
        database(mode);
        assertEquals(1101, call("POST", "/api/v1/tenant/auth/sms/login", "{\"phone\":\"13900008806\",\"code\":\"000000\"}", null).path("code").asInt());
        assertEquals(1101, call("POST", "/api/v1/tenant/auth/password/login", "{\"phone\":\"13900008806\",\"password\":\"WrongPass123\"}", null).path("code").asInt());
        assertEquals(401, call("POST", "/api/v1/tenant/users/me/initialize", "{}", null).path("code").asInt());
        assertEquals(0, count("sys_user")); assertEquals(0, count("sys_tenant")); assertEquals(0, count("billing_trial"));
    }

    @ParameterizedTest @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void initializationFailureRollsBackUserWorkspaceAndMembershipTogether(String mode) throws Exception {
        database(mode);
        doThrow(new IllegalStateException("test trial storage failure")).when(trials).start(any(), any());
        assertThrows(IllegalStateException.class, () -> auth.smsLogin(new PhoneNumber("13900008807"), "123456"));
        assertEquals(0, count("sys_user")); assertEquals(0, count("sys_tenant")); assertEquals(0, count("tenant_member")); assertEquals(0, count("billing_trial"));
    }

    @ParameterizedTest @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void existingWorkspaceWithoutTrialIsNotGrantedANewTrialAndPasswordLoginKeepsProfile(String mode) throws Exception {
        database(mode);
        User existing = User.register(new PhoneNumber("13900008808"));
        existing.profileOrCreate().updateAccount("已有昵称", "old@example.test", "已有企业", "零售", null);
        existing.setPassword("ExistingPass123", context.getBean(PasswordHasher.class)); users.save(existing);
        Tenant tenant = Tenant.createForFirstOnboarding(existing.id(), "已有企业"); tenants.save(tenant);
        var response = auth.passwordLogin(existing.phone(), "ExistingPass123");
        assertFalse(response.needOnboarding());
        assertEquals(tenant.id(), jwt.parse(response.token()).tenantId().orElseThrow());
        var current = call("GET", "/api/v1/tenant/users/me", null, response.token()).path("data");
        assertFalse(current.path("needOnboarding").asBoolean());
        assertEquals("已有昵称", current.path("nickname").asText());
        assertEquals("old@example.test", current.path("email").asText());
        assertEquals(0, count("billing_trial"));
        assertFalse(users.findById(existing.id()).orElseThrow().profile().orElseThrow().isCompleted());
    }
}
