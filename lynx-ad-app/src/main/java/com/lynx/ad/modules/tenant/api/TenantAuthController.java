package com.lynx.ad.modules.tenant.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.tenant.api.dto.LoginResponse;
import com.lynx.ad.modules.tenant.api.dto.PasswordLoginRequest;
import com.lynx.ad.modules.tenant.api.dto.PeekSmsRequest;
import com.lynx.ad.modules.tenant.api.dto.PeekSmsResponse;
import com.lynx.ad.modules.tenant.api.dto.SendSmsRequest;
import com.lynx.ad.modules.tenant.api.dto.SendSmsResponse;
import com.lynx.ad.modules.tenant.api.dto.SmsLoginRequest;
import com.lynx.ad.modules.tenant.application.AuthCommandService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/auth")
public class TenantAuthController {

    private final AuthCommandService authCommandService;

    public TenantAuthController(AuthCommandService authCommandService) {
        this.authCommandService = authCommandService;
    }

    @PostMapping("/sms/send")
    public ApiResponse<SendSmsResponse> sendSms(@RequestBody SendSmsRequest req) {
        return ApiResponse.ok(authCommandService.sendSms(new PhoneNumber(req.phone())));
    }

    @PostMapping("/sms/verify")
    public ApiResponse<PeekSmsResponse> verifySms(@RequestBody PeekSmsRequest req) {
        return ApiResponse.ok(authCommandService.peekSms(new PhoneNumber(req.phone()), req.code()));
    }

    @PostMapping("/sms/login")
    public ApiResponse<LoginResponse> smsLogin(@RequestBody SmsLoginRequest req) {
        return ApiResponse.ok(authCommandService.smsLogin(new PhoneNumber(req.phone()), req.code()));
    }

    @PostMapping("/password/login")
    public ApiResponse<LoginResponse> passwordLogin(@RequestBody PasswordLoginRequest req) {
        return ApiResponse.ok(authCommandService.passwordLogin(new PhoneNumber(req.phone()), req.password()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authCommandService.logout(TenantContext.userId());
        return ApiResponse.ok(null);
    }
}
