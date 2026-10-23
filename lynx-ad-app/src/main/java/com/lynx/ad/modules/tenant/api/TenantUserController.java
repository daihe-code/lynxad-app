package com.lynx.ad.modules.tenant.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.tenant.api.dto.CurrentUserResponse;
import com.lynx.ad.modules.tenant.api.dto.LoginResponse;
import com.lynx.ad.modules.tenant.api.dto.OnboardingRequest;
import com.lynx.ad.modules.tenant.api.dto.UpdatePasswordRequest;
import com.lynx.ad.modules.tenant.api.dto.UpdatePhoneRequest;
import com.lynx.ad.modules.tenant.api.dto.UpdateProfileRequest;
import com.lynx.ad.modules.tenant.application.AccountCommandService;
import com.lynx.ad.modules.tenant.application.AuthCommandService;
import com.lynx.ad.modules.tenant.application.OnboardingCommandService;
import com.lynx.ad.modules.tenant.application.TenantQuerySupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/users")
public class TenantUserController {

    private final TenantQuerySupport querySupport;
    private final OnboardingCommandService onboardingCommandService;
    private final AccountCommandService accountCommandService;
    private final AuthCommandService authCommandService;

    public TenantUserController(
            TenantQuerySupport querySupport,
            OnboardingCommandService onboardingCommandService,
            AccountCommandService accountCommandService,
            AuthCommandService authCommandService) {
        this.querySupport = querySupport;
        this.onboardingCommandService = onboardingCommandService;
        this.accountCommandService = accountCommandService;
        this.authCommandService = authCommandService;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.ok(querySupport.current(TenantContext.userId()));
    }

    @PostMapping("/me/initialize")
    public ApiResponse<LoginResponse> initialize() {
        return ApiResponse.ok(authCommandService.initializeAccount(TenantContext.userId()));
    }

    @PutMapping("/me")
    public ApiResponse<CurrentUserResponse> onboarding(@RequestBody OnboardingRequest req, jakarta.servlet.http.HttpServletResponse response) {
        var updated = onboardingCommandService.complete(TenantContext.userId(), req);
        response.setHeader("X-Auth-Token", updated.token());
        return ApiResponse.ok(updated.currentUser());
    }

    @PutMapping("/me/onboarding")
    public ApiResponse<CurrentUserResponse> onboardingAlias(@RequestBody OnboardingRequest req, jakarta.servlet.http.HttpServletResponse response) {
        return onboarding(req, response);
    }

    @PutMapping("/me/profile")
    public ApiResponse<CurrentUserResponse> profile(@RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(accountCommandService.updateProfile(TenantContext.userId(), req));
    }

    @PutMapping("/me/password")
    public ApiResponse<CurrentUserResponse> password(@RequestBody UpdatePasswordRequest req, jakarta.servlet.http.HttpServletResponse response) {
        var updated = accountCommandService.updatePassword(TenantContext.userId(), req);
        response.setHeader("X-Auth-Token", updated.token());
        return ApiResponse.ok(updated.currentUser());
    }

    @PutMapping("/me/phone")
    public ApiResponse<CurrentUserResponse> phone(@RequestBody UpdatePhoneRequest req) {
        return ApiResponse.ok(accountCommandService.updatePhone(TenantContext.userId(), req));
    }
}
