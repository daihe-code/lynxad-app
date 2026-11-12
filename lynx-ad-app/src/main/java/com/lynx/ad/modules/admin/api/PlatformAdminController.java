package com.lynx.ad.modules.admin.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.modules.admin.application.*;
import com.lynx.ad.modules.admin.infrastructure.AdminUserQuery;
import com.lynx.ad.modules.tenant.api.dto.SendSmsResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class PlatformAdminController {
    private final AdminAuthService auth;private final AdminUserQuery users;private final AdminPackageService packages;
    public PlatformAdminController(AdminAuthService auth,AdminUserQuery users,AdminPackageService packages) { this.auth=auth;this.users=users;this.packages=packages; }
    public record LoginRequest(String phone,String code) { @Override public String toString(){return "AdminLoginRequest";} }
    public record PhoneRequest(String phone) {}
    @PostMapping("/auth/sms") public ApiResponse<SendSmsResponse> sendSms(@RequestBody PhoneRequest body) { return ApiResponse.ok(auth.sendSms(body.phone())); }
    @PostMapping("/auth/login") public ApiResponse<AdminAuthService.Login> login(@RequestBody LoginRequest body) { return ApiResponse.ok(auth.login(body.phone(),body.code())); }
    @GetMapping("/auth/me") public ApiResponse<AdminAuthService.Me> me() { return ApiResponse.ok(auth.me()); }
    @PostMapping("/auth/logout") public ApiResponse<Void> logout() { auth.logout();return ApiResponse.ok(null); }
    @GetMapping("/admin-phones") public ApiResponse<List<AdminAuthService.Me>> phoneAdmins() { return ApiResponse.ok(auth.phoneAdmins()); }
    @PostMapping("/admin-phones") public ApiResponse<AdminAuthService.Me> grantPhone(@RequestBody PhoneRequest body) { return ApiResponse.ok(auth.grantPhone(body.phone())); }
    @GetMapping("/overview") public ApiResponse<AdminUserQuery.Overview> overview() { return ApiResponse.ok(users.overview()); }
    @GetMapping("/users") public ApiResponse<AdminUserQuery.Page<AdminUserQuery.UserRow>> list(@RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.ok(users.users(q,status,pageNum,pageSize)); }
    @GetMapping("/users/{id}") public ApiResponse<AdminUserQuery.Detail> detail(@PathVariable String id, @RequestParam(required=false) String tenantId) { return ApiResponse.ok(users.detail(id, tenantId)); }
    @GetMapping("/login-logs") public ApiResponse<AdminUserQuery.Page<AdminUserQuery.LoginRow>> logins(@RequestParam(required=false) String userId,@RequestParam(required=false) String q,@RequestParam(required=false) String result,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.ok(users.logins(userId,q,result,from,to,pageNum,pageSize)); }
    @PostMapping("/users/{id}/package/extend") public ApiResponse<AdminUserQuery.Adjustment> extend(@PathVariable String id,@RequestBody AdminPackageService.ExtendRequest body) { return ApiResponse.ok(packages.extend(id,body)); }
    @PostMapping("/users/{id}/package/change")
    public ApiResponse<AdminUserQuery.Adjustment> changePackage(@PathVariable String id, @RequestBody AdminPackageService.ChangeRequest body) {
        return ApiResponse.ok(packages.change(id, body));
    }
    @GetMapping("/package-adjustments") public ApiResponse<AdminUserQuery.Page<AdminUserQuery.Adjustment>> adjustments(@RequestParam(required=false) String userId,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.ok(users.adjustments(userId,pageNum,pageSize)); }
    @GetMapping("/audit-logs") public ApiResponse<AdminUserQuery.Page<AdminUserQuery.AdminAudit>> audit(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.ok(users.audit(pageNum,pageSize)); }
}
