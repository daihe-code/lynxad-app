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
import com.lynx.ad.modules.tenant.application.UserLoginJournal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/auth")
public class TenantAuthController {

    private final AuthCommandService authCommandService;

    private final UserLoginJournal logins;
    public TenantAuthController(AuthCommandService authCommandService,UserLoginJournal logins) {
        this.authCommandService = authCommandService;
        this.logins=logins;
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
        return recorded(req.phone(),"SMS",()->authCommandService.smsLogin(new PhoneNumber(req.phone()), req.code()));
    }

    @PostMapping("/password/login")
    public ApiResponse<LoginResponse> passwordLogin(@RequestBody PasswordLoginRequest req) {
        return recorded(req.phone(),"PASSWORD",()->authCommandService.passwordLogin(new PhoneNumber(req.phone()), req.password()));
    }

    private ApiResponse<LoginResponse> recorded(String phone,String method,java.util.function.Supplier<LoginResponse> command) {
        LoginResponse login;
        try { login=command.get(); }
        catch(com.lynx.ad.common.exception.BizException | com.lynx.ad.common.exception.DomainException ex) { logins.record(phone,method,null);throw ex; }
        logins.record(phone,method,login);
        return ApiResponse.ok(login);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authCommandService.logout(TenantContext.userId());
        return ApiResponse.ok(null);
    }
}
