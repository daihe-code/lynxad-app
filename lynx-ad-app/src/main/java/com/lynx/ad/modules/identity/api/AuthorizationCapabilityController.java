package com.lynx.ad.modules.identity.api;
import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.identity.application.AuthorizationCapabilityService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/identity/accounts/{id}/capabilities")
public class AuthorizationCapabilityController {
    private final AuthorizationCapabilityService service;
    public AuthorizationCapabilityController(AuthorizationCapabilityService service){this.service=service;}
    @GetMapping public ApiResponse<AuthorizationCapabilityService.Check> get(@PathVariable String id){return ApiResponse.ok(service.inspect(TenantContext.requireTenantId(),id));}
    @PostMapping public ApiResponse<AuthorizationCapabilityService.Check> check(@PathVariable String id){return ApiResponse.ok(service.record(TenantContext.requireTenantId(),id));}
}
