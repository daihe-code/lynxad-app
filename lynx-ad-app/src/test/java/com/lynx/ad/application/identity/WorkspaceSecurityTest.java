package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.identity.application.*;
import com.lynx.ad.modules.tenant.application.WorkspaceAccess;
import com.lynx.ad.modules.tenant.application.ClientSpaceService;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;

class WorkspaceSecurityTest {
    @AfterEach void clear() { TenantContext.clear(); }
    void principal(String tenant,String role) {
        TenantContext.set(new TenantPrincipal(new UserId(tenant),Optional.of(new TenantId(tenant)),Optional.of(MemberRole.valueOf(role)),SysRole.USER,Optional.of("session")));
    }
    @org.junit.jupiter.api.Test
    void selectedWorkspaceIsKeptWhenAccountCredentialsAreReissued() {
        var repository=org.mockito.Mockito.mock(com.lynx.ad.modules.tenant.domain.port.TenantRepository.class);
        var user=new UserId("a");var a=new TenantId("a");var b=new TenantId("b");
        var first=new com.lynx.ad.modules.tenant.domain.model.Tenant(a,"First","ACTIVE",List.of(new com.lynx.ad.modules.tenant.domain.model.TenantMember(user,MemberRole.OWNER,Instant.now())));
        var second=new com.lynx.ad.modules.tenant.domain.model.Tenant(b,"Selected","ACTIVE",List.of(new com.lynx.ad.modules.tenant.domain.model.TenantMember(user,MemberRole.MEMBER,Instant.now())));
        org.mockito.Mockito.when(repository.findByUserId(user)).thenReturn(Optional.of(first));
        org.mockito.Mockito.when(repository.findById(b)).thenReturn(Optional.of(second));
        org.mockito.Mockito.when(repository.findById(a)).thenReturn(Optional.empty());
        var facade=new com.lynx.ad.modules.tenant.application.TenantMembershipFacade(repository);
        TenantContext.set(new TenantPrincipal(user,Optional.of(b),Optional.of(MemberRole.MEMBER),SysRole.USER,Optional.of("session")));
        assertEquals(b,facade.currentTenantOf(user).orElseThrow());
        principal("a","OWNER");
        assertTrue(facade.currentTenantOf(user).isEmpty()); // A removed membership must not fall back to another workspace.
        TenantContext.clear();
        assertEquals(a,facade.currentTenantOf(user).orElseThrow());
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void freshMembershipOverridesOldJwtRoleAndLogoutRevokesEvenTenantlessSession(String mode) throws Exception {
        var f=new AccountSystemPersistenceTest().fixture(mode);
        var guard=new WorkspaceRequestGuard(new WorkspaceAccess(f.jdbc()));
        principal("a","OWNER");
        var request=new MockHttpServletRequest("GET","/api/v1/billing/subscription");
        assertTrue(guard.preHandle(request,new MockHttpServletResponse(),new Object()));
        f.jdbc().update("UPDATE tenant_member SET member_role='VIEWER',permission_version=2 WHERE tenant_id='a'");
        principal("a","OWNER");
        assertEquals(403,assertThrows(BizException.class,()->guard.preHandle(request,new MockHttpServletResponse(),new Object())).code());
        f.jdbc().update("INSERT INTO workspace_session_revocation VALUES('session',?)",Timestamp.from(Instant.now().plusSeconds(60)));
        TenantContext.set(new TenantPrincipal(new UserId("a"),Optional.empty(),Optional.empty(),SysRole.USER,Optional.of("session")));
        assertEquals(401,assertThrows(BizException.class,()->guard.preHandle(request,new MockHttpServletResponse(),new Object())).code());
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void signedExecutionScopesCannotMoveTenantActorTaskOrPermissionVersion(String mode) throws Exception {
        var test=new AccountSystemPersistenceTest();var f=test.fixture(mode);test.bind(f,"a","account","1001","ACTIVE","a",null);
        var config=new LynxAdProperties();config.getJwt().setSecret("workspace-tests-only-secret-32-bytes");
        var json=new ObjectMapper();var tickets=new EngineExecutionTickets(f.bindings(),f.jdbc(),json,config,true);
        principal("a","OWNER");
        String header=tickets.header(json.readTree("{\"store_id\":\"a\",\"account_id\":\"account\",\"ai_plan_task_id\":\"task\"}"));
        var envelope=json.readTree(Base64.getUrlDecoder().decode(header));String signed=envelope.path("ticket").asText();
        tickets.validate(signed,"a","account","task");
        assertThrows(BizException.class,()->tickets.validate(signed,"b","account","task"));
        assertThrows(BizException.class,()->tickets.validate(signed,"a","account","other-task"));
        assertThrows(BizException.class,()->tickets.validate(signed+"x","a","account","task"));
        tickets.remember(header,json.readTree("{\"ai_plan_task_id\":\"task\"}"));
        f.jdbc().update("UPDATE tenant_member SET permission_version=2 WHERE tenant_id='a'");
        assertThrows(BizException.class,()->tickets.validate(signed,"a","account","task"));
        assertThrows(BizException.class,()->tickets.header(json.readTree("{\"store_id\":\"a\",\"ai_plan_task_id\":\"task\"}")));
        principal("b","OWNER");
        assertThrows(BizException.class,()->tickets.header(json.readTree("{\"store_id\":\"a\",\"account_id\":\"account\"}")));
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void clientGroupingCannotMoveAnAccountIntoAnotherWorkspaceOrDeleteUsedGroup(String mode) throws Exception {
        var test=new AccountSystemPersistenceTest();var f=test.fixture(mode);test.bind(f,"a","account","1001","ACTIVE","a",null);
        var service=new ClientSpaceService(f.jdbc(),new WorkspaceAccess(f.jdbc()),new WorkspaceFoundation(f.jdbc()),f.bindings());
        principal("a","OWNER");
        String group=f.tx().execute(s->service.save(null,"客户A"));
        assertThrows(BizException.class,()->f.tx().executeWithoutResult(s->service.assign("account","b")));
        f.tx().executeWithoutResult(s->service.assign("account",group));
        assertEquals(group,f.bindings().find("a","account").orElseThrow().clientSpaceId());
        assertThrows(BizException.class,()->f.tx().executeWithoutResult(s->service.delete(group)));
        principal("b","OWNER");
        assertThrows(BizException.class,()->f.tx().executeWithoutResult(s->service.assign("account","b")));
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void fallbackSelectsOnlySameTenantVerifiedGrantWithoutChangingClaim(String mode) throws Exception {
        var test=new AccountSystemPersistenceTest();var f=test.fixture(mode);var j=f.jdbc();
        test.bind(f,"a","account","1001","ACTIVE","a","direct");test.bind(f,"a","account","1001","ACTIVE","a","agency");test.bind(f,"a","account","1001","ACTIVE","a","foreign-grant");
        j.execute("ALTER TABLE oauth_account ADD application_id VARCHAR(36)");j.execute("ALTER TABLE oauth_account ADD access_token_enc VARCHAR(255)");j.execute("ALTER TABLE oauth_account ADD refresh_token_enc VARCHAR(255)");j.execute("ALTER TABLE oauth_account ADD token_expires_at TIMESTAMP");j.execute("ALTER TABLE oauth_account ADD refresh_expires_at TIMESTAMP");
        j.execute("CREATE TABLE oauth_application(id VARCHAR(36) PRIMARY KEY,enabled BOOLEAN)");
        j.execute("CREATE TABLE oauth_grant(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(36),platform VARCHAR(24),application_id VARCHAR(36),status VARCHAR(24),access_token_enc VARCHAR(255),refresh_token_enc VARCHAR(255),token_expires_at TIMESTAMP,refresh_expires_at TIMESTAMP)");
        j.update("INSERT INTO oauth_application VALUES('app',TRUE)");
        j.update("INSERT INTO oauth_account(id,tenant_id,platform,advertiser_id,authorized_by_user_id,grant_id,status) VALUES('account','a','QIANCHUAN','1001','a','direct','UNUSABLE')");
        for(String grant:List.of("agency","foreign-grant"))j.update("INSERT INTO oauth_grant VALUES(?,?,'QIANCHUAN','app','ACTIVE','encrypted','encrypted',?,?)",grant,grant.equals("agency")?"a":"b",Timestamp.from(Instant.now().plusSeconds(3600)),Timestamp.from(Instant.now().plusSeconds(7200)));
        var selector=new AccountGrantSelector(j);
        assertTrue(Boolean.TRUE.equals(f.tx().execute(s->selector.activateAlternative("a","account","direct"))));
        assertEquals("agency",j.queryForObject("SELECT grant_id FROM oauth_account WHERE id='account'",String.class));
        assertEquals("a",f.bindings().find("a","account").orElseThrow().boundUserId());
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM ad_account_claim",Integer.class));
        assertFalse(Boolean.TRUE.equals(f.tx().execute(s->selector.activateAlternative("a","account","agency"))));
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void localDraftMutationsAndQueuedPublishKeepResponsibleMemberScope(String mode) throws Exception {
        var test=new AccountSystemPersistenceTest();var f=test.fixture(mode);var j=f.jdbc();
        test.bind(f,"a","account","1001","ACTIVE","a",null);
        j.execute("CREATE TABLE delivery_creation(plan_id VARCHAR(36),tenant_id VARCHAR(36),revision INT,state_json TEXT,work_pending INT,updated_at TIMESTAMP,PRIMARY KEY(tenant_id,plan_id))");
        j.execute("CREATE TABLE delivery_publish_attempt(id VARCHAR(36),tenant_id VARCHAR(36),plan_id VARCHAR(36),revision INT,request_key VARCHAR(64),payload_hash VARCHAR(64),payload_json TEXT,status VARCHAR(20),created_at TIMESTAMP,updated_at TIMESTAMP)");
        var store=new com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore(j,new ObjectMapper().findAndRegisterModules(),new org.springframework.jdbc.datasource.DataSourceTransactionManager(j.getDataSource()));
        store.executionBindings(f.bindings());
        principal("a","OWNER");
        var state=new com.lynx.ad.modules.delivery.domain.model.CreationState();state.planId="plan";state.tenantId="a";state.userId="a";state.accountId="account";
        store.insert(state);
        j.update("INSERT INTO sys_user VALUES('other-owner','ACTIVE')");
        j.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,membership_id,status) VALUES('a','other-owner','OWNER','other-membership','ACTIVE')");
        TenantContext.set(new TenantPrincipal(new UserId("other-owner"),Optional.of(new TenantId("a")),Optional.of(MemberRole.OWNER),SysRole.USER,Optional.of("other-session")));
        assertThrows(BizException.class,()->store.mutate("a","plan",null,s->s.name="changed"));
        assertThrows(BizException.class,()->store.delete("a","plan"));
        principal("a","OWNER");
        assertNull(store.get("a","plan").name);
        var attempt=new com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore.Attempt("attempt","a","plan",1,"request","hash","{}","QUEUED",null,null);
        f.tx().executeWithoutResult(s->store.attempt(attempt));
        TenantContext.clear();
        store.validatePublishScope(attempt);
        j.update("UPDATE tenant_member SET permission_version=permission_version+1 WHERE tenant_id='a' AND user_id='a'");
        assertThrows(BizException.class,()->store.validatePublishScope(attempt));
        var otherTenant=new com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore.Attempt("attempt","b","plan",1,"request","hash","{}","QUEUED",null,null);
        assertThrows(BizException.class,()->store.validatePublishScope(otherTenant));
        // Background receipt reconciliation remains possible after permission changes.
        store.mutate("a","plan",null,s->s.publishStatus="CREATED");
        assertEquals("CREATED",store.get("a","plan").publishStatus);
    }
}
