package com.lynx.ad.modules.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.api.GlobalExceptionHandler;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.admin.api.*;
import com.lynx.ad.modules.admin.application.*;
import com.lynx.ad.modules.admin.infrastructure.*;
import com.lynx.ad.modules.tenant.api.TenantAuthController;
import com.lynx.ad.modules.tenant.api.dto.PasswordLoginRequest;
import com.lynx.ad.modules.tenant.application.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class PlatformAdminTest {
    final AdminAccountStore accounts=mock(AdminAccountStore.class);
    final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    final com.lynx.ad.modules.tenant.domain.port.UserRepository tenantUsers=mock(com.lynx.ad.modules.tenant.domain.port.UserRepository.class);
    final JwtService jwt=new JwtService(new LynxAdProperties(), tenantUsers);
    final AdminAuthService auth=new AdminAuthService(accounts,passwords,jwt,new LynxAdProperties());
    final AdminAccountStore.Account account=new AdminAccountStore.Account("admin-id","platform-admin","管理员",passwords.encode("AdminFixture2026!"),"ACTIVE",0,null,null,Instant.now());
    @AfterEach void cleanup(){TenantContext.clear();}
    @Test void customerRoleCannotReadOrGrantEvenWithValidTenantOwnerToken() {
        TenantContext.set(new TenantPrincipal(new UserId("customer"),Optional.of(new TenantId("tenant")),Optional.of(MemberRole.OWNER),SysRole.USER));
        assertEquals(403,assertThrows(BizException.class,auth::require).code());verifyNoInteractions(accounts);
    }
    @Test void signedAdminClaimRequiresRegisteredActiveAccountAndLiveSession() {
        TenantContext.set(new TenantPrincipal(new UserId(account.id()),Optional.empty(),Optional.empty(),SysRole.ADMIN,Optional.of("session")));
        when(accounts.account(account.id())).thenReturn(Optional.of(account));
        assertEquals(401,assertThrows(BizException.class,auth::require).code());
        when(accounts.validSession(eq(account.id()),eq("session"),any())).thenReturn(true);
        assertEquals(account.id(),auth.me().id());
        when(accounts.account(account.id())).thenReturn(Optional.empty());assertEquals(403,assertThrows(BizException.class,auth::require).code());
    }
    @Test void loginIssuesAdminOnlySessionWithNoCustomerTenantAndDoesNotReturnPasswordHash()throws Exception {
        when(accounts.username(account.username())).thenReturn(Optional.of(account));
        var login=auth.login(account.username(),"AdminFixture2026!");var principal=jwt.parse(login.token());
        assertEquals(SysRole.ADMIN,principal.sysRole());assertTrue(principal.tenantId().isEmpty());assertEquals(account.id(),principal.userId().value());
        verify(accounts).login(eq(account),eq(principal.sessionId().orElseThrow()),any(),any());
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(login.admin()).contains("password"));assertFalse(login.toString().contains(login.token()));
    }
    @Test void repeatedInvalidPasswordsAreThrottledAndHaveGenericErrors() {
        when(accounts.username(account.username())).thenReturn(Optional.of(account));
        for(int i=0;i<6;i++)assertEquals(1901,assertThrows(BizException.class,()->auth.login(account.username(),"WrongFixture2026!")).code());
        verify(accounts,times(5)).failed(eq(account),any());verify(accounts,never()).login(any(),any(),any(),any());
        assertEquals(1901,assertThrows(BizException.class,()->auth.login("missing-admin","WrongFixture2026!")).code());
        assertThrows(BizException.class,()->auth.login(account.username(),"密".repeat(30))); // no BCrypt exception on long UTF-8 input
    }
    @Test void passwordChangeRequiresCurrentPasswordAndDelegatesSessionRevocation() {
        TenantContext.set(new TenantPrincipal(new UserId(account.id()),Optional.empty(),Optional.empty(),SysRole.ADMIN,Optional.of("session")));
        when(accounts.account(account.id())).thenReturn(Optional.of(account));when(accounts.validSession(eq(account.id()),anyString(),any())).thenReturn(true);
        assertThrows(BizException.class,()->auth.changePassword("wrong","NewFixture2026!"));verify(accounts,never()).password(any(),anyString());
        auth.changePassword("AdminFixture2026!","NewFixture2026!");verify(accounts).password(eq(account),anyString());
    }
    @Test void actualRegisteredInterceptorProtectsAllAdministrativeEndpointsAndLeavesLoginPublic()throws Exception {
        var users=mock(AdminUserQuery.class);var packages=mock(AdminPackageService.class);
        var moderation=mock(AdminAccountModeration.class);
        when(accounts.username(account.username())).thenReturn(Optional.of(account));when(accounts.account(account.id())).thenReturn(Optional.of(account));when(accounts.validSession(eq(account.id()),anyString(),any())).thenReturn(true);
        when(users.overview()).thenReturn(new AdminUserQuery.Overview(1,1,1,0,1));
        class Registry extends InterceptorRegistry { List<Object> all(){return getInterceptors();} }
        var registry=new Registry();new AdminAccessConfiguration(auth).addInterceptors(registry);
        var mvc=MockMvcBuilders.standaloneSetup(new PlatformAdminController(auth,users,packages), new AdminAccountModerationController(moderation)).setControllerAdvice(new GlobalExceptionHandler()).addFilters(new JwtAuthFilter(jwt)).addInterceptors((HandlerInterceptor)registry.all().getFirst()).build();
        assertTrue(mvc.perform(get("/api/v1/admin/overview")).andReturn().getResponse().getContentAsString().contains("\"code\":401"));
        when(tenantUsers.findById(new UserId("customer"))).thenReturn(Optional.of(new com.lynx.ad.modules.tenant.domain.model.User(
                new UserId("customer"), new com.lynx.ad.common.domain.PhoneNumber("13900009801"), null, null)));
        String customer=jwt.issue(new UserId("customer"),Optional.of(new TenantId("tenant")),MemberRole.OWNER,SysRole.USER);
        for(String path:List.of("/overview","/users","/users/one","/login-logs","/package-adjustments","/audit-logs","/auth/me","/phone-blacklist"))assertTrue(mvc.perform(get("/api/v1/admin"+path).header("Authorization","Bearer "+customer)).andReturn().getResponse().getContentAsString().contains("\"code\":403"));
        for (String path : List.of("/users/one/package/extend", "/users/one/delete", "/phone-blacklist", "/phone-blacklist/13900009801/unblock")) {
            assertTrue(mvc.perform(post("/api/v1/admin" + path).header("Authorization","Bearer " + customer)
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getContentAsString().contains("\"code\":403"));
        }
        verifyNoInteractions(users, packages, moderation);
        var raw=mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"platform-admin\",\"password\":\"AdminFixture2026!\"}")).andReturn().getResponse().getContentAsString();
        String token=new ObjectMapper().readTree(raw).path("data").path("token").asText();assertFalse(token.isEmpty());
        assertTrue(mvc.perform(get("/api/v1/admin/overview").header("Authorization","Bearer "+token)).andReturn().getResponse().getContentAsString().contains("\"users\":1"));
        assertTrue(mvc.perform(post("/api/v1/admin/phone-blacklist").header("Authorization","Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"13900009801\",\"reason\":\"test\"}"))
                .andReturn().getResponse().getContentAsString().contains("\"code\":0"));
        verify(moderation).block("13900009801", null, "test");
    }
    @Test void failedUserLoginsAreRecordedAfterAuthFailureAndSuccessRemainsCompatible() {
        var commands=mock(AuthCommandService.class);var journal=mock(UserLoginJournal.class);var controller=new TenantAuthController(commands,journal);
        when(commands.passwordLogin(any(),any())).thenThrow(new BizException(1101,"账号或密码错误"));
        assertThrows(BizException.class,()->controller.passwordLogin(new PasswordLoginRequest("13900001001","wrong")));
        verify(journal).record("13900001001","PASSWORD",null);
        var login=new com.lynx.ad.modules.tenant.api.dto.LoginResponse("fixture-token",3600,false,false);
        doReturn(login).when(commands).passwordLogin(any(),any());assertEquals(login,controller.passwordLogin(new PasswordLoginRequest("13900001001","correct")).data());verify(journal).record("13900001001","PASSWORD",login);
    }
}
