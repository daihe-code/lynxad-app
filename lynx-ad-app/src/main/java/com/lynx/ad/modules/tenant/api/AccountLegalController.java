package com.lynx.ad.modules.tenant.api;
import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.modules.tenant.application.AccountLegalService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/tenant/account")
public class AccountLegalController {
    private final AccountLegalService service;
    public AccountLegalController(AccountLegalService service){this.service=service;}
    @GetMapping("/legal-profile") public ApiResponse<AccountLegalService.Legal> get(){return ApiResponse.ok(service.get());}
    @PutMapping("/company") public ApiResponse<AccountLegalService.Legal> company(@RequestBody AccountLegalService.Company req){return ApiResponse.ok(service.company(req));}
    @PutMapping("/invoice") public ApiResponse<AccountLegalService.Legal> invoice(@RequestBody AccountLegalService.Invoice req){return ApiResponse.ok(service.saveInvoice(req));}
}
