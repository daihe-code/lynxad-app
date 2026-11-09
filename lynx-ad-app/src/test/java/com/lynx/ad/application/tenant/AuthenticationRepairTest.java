package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.tenant.api.*;
import com.lynx.ad.modules.tenant.application.*;
import com.lynx.ad.modules.tenant.domain.model.*;
import com.lynx.ad.modules.tenant.domain.port.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthenticationRepairTest {
    final UserRepository users = mock(UserRepository.class);
    final OtpStore otp = mock(OtpStore.class);
    final PasswordHasher hasher = mock(PasswordHasher.class);
    final AuthCommandService auth = new AuthCommandService(otp, mock(SmsGateway.class), users,
            mock(TenantRepository.class), mock(InvitationStore.class), hasher,
            mock(JwtService.class), new LynxAdProperties(), mock(AuditRecorder.class),
            mock(PasswordLoginGuard.class), mock(AccountInitializationService.class), mock(PhoneAccess.class));

    @Test void unknownPhoneHasExplicitRegistrationMessage() {
        var ex = assertThrows(BizException.class, () -> auth.passwordLogin(new PhoneNumber("13900009801"), "Password123"));
        assertEquals("该手机号尚未注册", ex.getMessage());
    }

    @Test void credentialChangeInvalidatesExistingJwtAcrossServiceInstances() {
        UserId id = new UserId("credential-test");
        User user = new User(id, new PhoneNumber("13900009801"), "old-hash", null);
        when(users.findById(id)).thenReturn(Optional.of(user));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(LynxAdProperties.class);
            context.registerBean(UserRepository.class, () -> users);
            context.register(JwtService.class);
            context.refresh();
            var jwt = context.getBean(JwtService.class);
            String firstDevice = jwt.issue(id, Optional.empty(), null, SysRole.USER);
            String secondDevice = jwt.issue(id, Optional.empty(), null, SysRole.USER);
            assertEquals(id, jwt.parse(firstDevice).userId());
            when(users.findById(id)).thenReturn(Optional.of(new User(id, user.phone(), "new-hash", null)));
            assertThrows(BizException.class, () -> jwt.parse(firstDevice));
            assertThrows(BizException.class, () -> jwt.parse(secondDevice));
            assertEquals(id, jwt.parse(jwt.issue(id, Optional.empty(), null, SysRole.USER)).userId());
            String admin = jwt.issue(new UserId("admin-only"), Optional.empty(), null, SysRole.ADMIN);
            assertEquals(SysRole.ADMIN, jwt.parse(admin).sysRole());
        }
    }

    @Test void onboardingAndPasswordResponsesProvideReplacementToken() throws Exception {
        var onboarding = mock(OnboardingCommandService.class);
        var account = mock(AccountCommandService.class);
        UserId id = new UserId("header-test");
        when(onboarding.complete(eq(id), any())).thenReturn(new AuthenticatedUserUpdate(null, "replacement-token"));
        when(account.updatePassword(eq(id), any())).thenReturn(new AuthenticatedUserUpdate(null, "replacement-token"));
        TenantContext.set(new TenantPrincipal(id, Optional.empty(), Optional.empty(), SysRole.USER, Optional.empty()));
        try {
            var mvc = MockMvcBuilders.standaloneSetup(new TenantUserController(mock(TenantQuerySupport.class), onboarding, account, auth)).build();
            for (String url : new String[]{"/api/v1/tenant/users/me", "/api/v1/tenant/users/me/onboarding", "/api/v1/tenant/users/me/password"}) {
                mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isOk()).andExpect(header().string("X-Auth-Token", "replacement-token"))
                        .andExpect(jsonPath("$.code").value(0));
            }
        } finally { TenantContext.clear(); }
    }

    @Test void resetEndpointExistsAndRejectsInvalidOtpWithoutCreatingUser() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TenantAuthController(auth, mock(UserLoginJournal.class)))
                .setControllerAdvice(new com.lynx.ad.common.api.GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/tenant/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"13900009801\",\"code\":\"123456\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1101));
        verify(users, never()).save(any());
    }

    @Test void resetRequiresRegisteredAccountAndConsumesOtpBeforeChangingCredential() {
        PhoneNumber phone = new PhoneNumber("13900009801");
        when(otp.verifyAndConsume(phone, "654321")).thenReturn(true, false);
        assertEquals("该手机号尚未注册", assertThrows(BizException.class,
                () -> auth.resetPassword(phone, "654321", "Password123")).getMessage());
        verify(users, never()).save(any());
        User user = new User(new UserId("reset-user"), phone, "old-hash", null);
        when(users.findByPhone(phone)).thenReturn(Optional.of(user));
        when(otp.verifyAndConsume(phone, "123456")).thenReturn(true, false);
        when(hasher.hash("Password123")).thenReturn("new-hash");
        auth.resetPassword(phone, "123456", "Password123");
        assertEquals("new-hash", user.passwordHash());
        assertThrows(BizException.class, () -> auth.resetPassword(phone, "123456", "Password123"));
        verify(users, times(1)).save(any());
    }

    @Test void invalidResetPasswordDoesNotConsumeOtp() {
        assertThrows(com.lynx.ad.common.exception.DomainException.class,
                () -> auth.resetPassword(new PhoneNumber("13900009801"), "123456", "short"));
        verifyNoInteractions(otp, users);
    }

    @Test void existingPhoneStillReportsPasswordError() {
        PhoneNumber phone = new PhoneNumber("13900009801");
        when(users.findByPhone(phone)).thenReturn(Optional.of(new User(new UserId("registered"), phone, "hash", null)));
        assertEquals("账号或密码错误", assertThrows(BizException.class,
                () -> auth.passwordLogin(phone, "wrong")).getMessage());
    }

    @Test void passwordVerifiedBeforeConcurrentResetDoesNotMintFreshSession() {
        PhoneNumber phone = new PhoneNumber("13900009801");
        UserId id = new UserId("racing-login");
        when(users.findByPhone(phone)).thenReturn(Optional.of(new User(id, phone, "old-hash", null)));
        when(users.findById(id)).thenReturn(Optional.of(new User(id, phone, "new-hash", null)));
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(new User(id, phone, "new-hash", null)));
        when(hasher.matches("OldPassword123", "old-hash")).thenReturn(true);
        JwtService jwt = new JwtService(new LynxAdProperties(), users);
        var initialization = mock(AccountInitializationService.class);
        when(initialization.initialize(id)).thenReturn(Tenant.createForFirstOnboarding(id, "Race fixture"));
        var commands = new AuthCommandService(otp, mock(SmsGateway.class), users, mock(TenantRepository.class),
                mock(InvitationStore.class), hasher, jwt, new LynxAdProperties(), mock(AuditRecorder.class),
                mock(PasswordLoginGuard.class), initialization, mock(PhoneAccess.class));
        assertEquals(1101, assertThrows(BizException.class,
                () -> commands.passwordLogin(phone, "OldPassword123")).code());
    }

    @Test void passwordChangeReplacementDoesNotSurviveResetBetweenSaveAndIssuance() throws Exception {
        assertReplacementDoesNotSurviveReset(false);
    }

    @Test void onboardingReplacementDoesNotSurviveResetBetweenSaveAndIssuance() throws Exception {
        assertReplacementDoesNotSurviveReset(true);
    }

    private void assertReplacementDoesNotSurviveReset(boolean onboardingRequest) throws Exception {
        UserId id = new UserId("replacement-race");
        PhoneNumber phone = new PhoneNumber("13900009801");
        User authenticated = new User(id, phone, "old-hash", null);
        User reset = new User(id, phone, "reset-hash", null);
        var persisted = new java.util.concurrent.atomic.AtomicReference<>(authenticated);
        when(users.findById(id)).thenAnswer(ignored -> Optional.of(persisted.get()));
        when(users.findByIdForUpdate(id)).thenAnswer(ignored -> Optional.of(persisted.get()));
        // Deterministically commit an independent reset after this operation saves, before it signs.
        doAnswer(ignored -> { persisted.set(reset); return null; }).when(users).save(any());
        when(hasher.matches("OldPassword123", "old-hash")).thenReturn(true);
        when(hasher.hash("NewPassword123")).thenReturn("operation-hash");
        var tenants = mock(TenantRepository.class);
        when(tenants.findByUserId(id)).thenReturn(Optional.of(Tenant.createForFirstOnboarding(id, "Race fixture")));
        var query = mock(TenantQuerySupport.class);
        JwtService jwt = new JwtService(new LynxAdProperties(), users);
        var onboarding = new OnboardingCommandService(users, tenants, hasher,
                mock(com.lynx.ad.modules.billing.application.BillingTrialService.class), jwt, new LynxAdProperties(), query);
        var account = new AccountCommandService(users, tenants, hasher, otp, query, mock(AuditRecorder.class), jwt, mock(PhoneAccess.class));
        var controller = new TenantUserController(query, onboarding, account, auth);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        TenantContext.set(new TenantPrincipal(id, Optional.empty(), Optional.empty(), SysRole.USER, Optional.empty()));
        try {
            String url = onboardingRequest ? "/api/v1/tenant/users/me/onboarding" : "/api/v1/tenant/users/me/password";
            String body = onboardingRequest
                    ? "{\"password\":\"NewPassword123\",\"agreeTerms\":true,\"agreePrivacy\":true}"
                    : "{\"oldPassword\":\"OldPassword123\",\"newPassword\":\"NewPassword123\"}";
            var response = mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse();
            String token = response.getHeader("X-Auth-Token");
            assertNotNull(token);
            assertEquals(401, assertThrows(BizException.class, () -> jwt.parse(token)).code());
            assertEquals(id, jwt.parse(jwt.issueForUser(reset, Optional.empty(), null)).userId());
        } finally { TenantContext.clear(); }
    }
}
