package com.lynx.ad.modules.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.api.GlobalExceptionHandler;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.admin.api.*;
import com.lynx.ad.modules.admin.application.*;
import com.lynx.ad.modules.admin.infrastructure.*;
import com.lynx.ad.modules.tenant.api.TenantAuthController;
import com.lynx.ad.modules.tenant.api.dto.PasswordLoginRequest;
import com.lynx.ad.modules.tenant.application.*;
import com.lynx.ad.modules.tenant.domain.port.OtpStore;
import com.lynx.ad.modules.tenant.domain.port.SmsGateway;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class PlatformAdminTest {
    final AdminAccountStore accounts=mock(AdminAccountStore.class);
    final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    final OtpStore otp=mock(OtpStore.class);
    final SmsGateway sms=mock(SmsGateway.class);
    final com.lynx.ad.modules.tenant.domain.port.UserRepository tenantUsers=mock(com.lynx.ad.modules.tenant.domain.port.UserRepository.class);
    final LynxAdProperties properties=new LynxAdProperties();
    final JwtService jwt=new JwtService(properties, tenantUsers);
    final AdminAuthService auth=new AdminAuthService(accounts,jwt,properties,otp,sms,passwords);
    final PhoneNumber phone=new PhoneNumber("13900009810");
    final PhoneNumber delegatePhone=new PhoneNumber("13900009811");
    final AdminAccountStore.Account account=new AdminAccountStore.Account("admin-id","+86 "+phone.value(),"管理员","ACTIVE",null,phone.value(),true);
    final AdminAccountStore.Account delegate=new AdminAccountStore.Account("delegate-id","+86 "+delegatePhone.value(),"管理员","ACTIVE",null,delegatePhone.value(),false);
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
        when(accounts.account(account.id())).thenReturn(Optional.of(new AdminAccountStore.Account(
                account.id(), "legacy-admin", "管理员", "ACTIVE", null, null, false)));
        assertEquals(401,assertThrows(BizException.class,auth::require).code());
        when(accounts.account(account.id())).thenReturn(Optional.empty());assertEquals(403,assertThrows(BizException.class,auth::require).code());
    }
    @Test void loginIssuesAdminOnlySessionWithNoCustomerTenantAndDoesNotReturnPasswordHash()throws Exception {
        when(accounts.activePhone(phone)).thenReturn(Optional.of(account));
        when(otp.verifyAndConsumeAdminLogin(phone, "197531")).thenReturn(true, false);
        var login=auth.login(phone.value(),"197531");var principal=jwt.parse(login.token());
        assertEquals(SysRole.ADMIN,principal.sysRole());assertTrue(principal.tenantId().isEmpty());assertEquals(account.id(),principal.userId().value());
        verify(accounts).login(eq(account),eq(principal.sessionId().orElseThrow()),any(),any());
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(login.admin()).contains("password"));assertFalse(login.toString().contains(login.token()));
        assertEquals(1101, assertThrows(BizException.class, () -> auth.login(phone.value(), "197531")).code());
        verify(otp, never()).verifyAndConsume(any(), anyString());
    }
    @Test void unlistedPhonesCannotSendOrVerifyAndLegacyCredentialsAreRejected() {
        assertEquals(1901, assertThrows(BizException.class, () -> auth.sendSms(delegatePhone.value())).code());
        assertEquals(1901, assertThrows(BizException.class, () -> auth.login(delegatePhone.value(), "197531")).code());
        assertThrows(DomainException.class, () -> auth.login("legacy-admin", "fixture-password"));
        verifyNoInteractions(otp, sms);
    }
    @Test void sendsLiveSmsOnlyAndReservesTheCooldownBeforeSending() {
        when(accounts.activePhone(phone)).thenReturn(Optional.of(account));
        assertEquals(1114, assertThrows(BizException.class, () -> auth.sendSms(phone.value())).code());
        verifyNoInteractions(otp, sms);
        configureLiveSms();
        when(otp.reserveAdminSms(phone, Duration.ofSeconds(90))).thenReturn(true, false);
        assertEquals(90, auth.sendSms(phone.value()).cooldownSec());
        assertEquals(1115, assertThrows(BizException.class, () -> auth.sendSms(phone.value())).code());
        var code=ArgumentCaptor.forClass(String.class);
        var hash=ArgumentCaptor.forClass(String.class);
        var order=inOrder(otp,sms);
        order.verify(otp).reserveAdminSms(phone, Duration.ofSeconds(90));
        order.verify(sms).send(eq(phone),code.capture());
        order.verify(otp).saveAdminLogin(eq(phone),hash.capture(),eq(Duration.ofMinutes(5)));
        assertTrue(code.getValue().matches("[0-9]{6}"));
        assertTrue(passwords.matches(code.getValue(),hash.getValue()));
        verify(sms,times(1)).send(any(),anyString());
    }
    @Test void onlyGrantorsCanListAndAddAdminPhones() {
        when(accounts.validSession(anyString(),eq("session"),any())).thenReturn(true);
        when(accounts.account(delegate.id())).thenReturn(Optional.of(delegate));
        TenantContext.set(new TenantPrincipal(new UserId(delegate.id()),Optional.empty(),Optional.empty(),SysRole.ADMIN,Optional.of("session")));
        assertEquals(1904,assertThrows(BizException.class,auth::phoneAdmins).code());
        assertEquals(1904,assertThrows(BizException.class,()->auth.grantPhone("13900009812")).code());
        verify(accounts,never()).grantPhone(any(),any());
        when(accounts.account(account.id())).thenReturn(Optional.of(account));
        TenantContext.set(new TenantPrincipal(new UserId(account.id()),Optional.empty(),Optional.empty(),SysRole.ADMIN,Optional.of("session")));
        when(accounts.grantPhone(account,delegatePhone)).thenReturn(delegate).thenThrow(new DuplicateKeyException("duplicate"));
        assertFalse(auth.grantPhone(delegatePhone.value()).canManageAdmins());
        when(accounts.activePhone(delegatePhone)).thenReturn(Optional.of(delegate));
        when(otp.verifyAndConsumeAdminLogin(delegatePhone,"197531")).thenReturn(true);
        assertEquals(delegatePhone.value(),auth.login(delegatePhone.value(),"197531").admin().phone());
        assertEquals(1903,assertThrows(BizException.class,()->auth.grantPhone(delegatePhone.value())).code());
    }
    private void configureLiveSms() {
        properties.getSms().setMode("live");
        var live=properties.getSms().getLive();
        live.setVendor("aliyun"); live.setEndpoint("sms.example.invalid");
        live.setAccessKey("test"); live.setAccessSecret("test"); live.setSignName("test"); live.setTemplateCode("test");
    }
    @Test void actualRegisteredInterceptorProtectsAllAdministrativeEndpointsAndLeavesLoginPublic()throws Exception {
        var users=mock(AdminUserQuery.class);var packages=mock(AdminPackageService.class);
        var moderation=mock(AdminAccountModeration.class);
        when(accounts.activePhone(phone)).thenReturn(Optional.of(account));when(accounts.account(account.id())).thenReturn(Optional.of(account));when(accounts.validSession(eq(account.id()),anyString(),any())).thenReturn(true);
        when(otp.verifyAndConsumeAdminLogin(phone,"197531")).thenReturn(true);
        when(users.overview()).thenReturn(new AdminUserQuery.Overview(1,1,1,0,1));
        class Registry extends InterceptorRegistry { List<Object> all(){return getInterceptors();} }
        var registry=new Registry();new AdminAccessConfiguration(auth).addInterceptors(registry);
        var mvc=MockMvcBuilders.standaloneSetup(new PlatformAdminController(auth,users,packages), new AdminAccountModerationController(moderation)).setControllerAdvice(new GlobalExceptionHandler()).addFilters(new JwtAuthFilter(jwt)).addInterceptors((HandlerInterceptor)registry.all().getFirst()).build();
        assertTrue(mvc.perform(get("/api/v1/admin/overview")).andReturn().getResponse().getContentAsString().contains("\"code\":401"));
        when(tenantUsers.findById(new UserId("customer"))).thenReturn(Optional.of(new com.lynx.ad.modules.tenant.domain.model.User(
                new UserId("customer"), new com.lynx.ad.common.domain.PhoneNumber("13900009801"), null, null)));
        String customer=jwt.issue(new UserId("customer"),Optional.of(new TenantId("tenant")),MemberRole.OWNER,SysRole.USER);
        for(String path:List.of("/overview","/users","/users/one","/login-logs","/package-adjustments","/audit-logs","/auth/me","/phone-blacklist","/admin-phones"))assertTrue(mvc.perform(get("/api/v1/admin"+path).header("Authorization","Bearer "+customer)).andReturn().getResponse().getContentAsString().contains("\"code\":403"));
        for (String path : List.of("/users/one/package/extend", "/users/one/package/change", "/users/one/delete", "/phone-blacklist", "/phone-blacklist/13900009801/unblock", "/admin-phones")) {
            assertTrue(mvc.perform(post("/api/v1/admin" + path).header("Authorization","Bearer " + customer)
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getContentAsString().contains("\"code\":403"));
        }
        verifyNoInteractions(users, packages, moderation);
        var smsResult=mvc.perform(post("/api/v1/admin/auth/sms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone.value() + "\"}")).andReturn().getResponse().getContentAsString();
        assertEquals(1114,new ObjectMapper().readTree(smsResult).path("code").asInt());
        var raw=mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone.value() + "\",\"code\":\"197531\"}")).andReturn().getResponse().getContentAsString();
        String token=new ObjectMapper().readTree(raw).path("data").path("token").asText();assertFalse(token.isEmpty());
        assertTrue(mvc.perform(get("/api/v1/admin/overview").header("Authorization","Bearer "+token)).andReturn().getResponse().getContentAsString().contains("\"users\":1"));
        assertTrue(mvc.perform(post("/api/v1/admin/phone-blacklist").header("Authorization","Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"13900009801\",\"reason\":\"test\"}"))
                .andReturn().getResponse().getContentAsString().contains("\"code\":0"));
        verify(moderation).block("13900009801", null, "test");
        assertTrue(mvc.perform(post("/api/v1/admin/users/one/package/change").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"id\",\"tenantId\":\"tenant\",\"planCode\":\"BASIC\",\"days\":30,\"reason\":\"测试\"}"))
                .andReturn().getResponse().getContentAsString().contains("\"code\":0"));
        verify(packages).change("one", new AdminPackageService.ChangeRequest("id", "tenant", "BASIC", 30, null, "测试"));
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
