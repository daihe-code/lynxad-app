package com.lynx.ad.modules.admin.api;
import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.modules.tenant.application.AccountLegalService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/qualifications")
public class QualificationController {
    private final AccountLegalService service;
    public QualificationController(AccountLegalService service){this.service=service;}
    @GetMapping public ApiResponse<AccountLegalService.Queue> queue(@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="1") int pageNum){return ApiResponse.ok(service.queue(status,pageNum));}
    @PostMapping("/{tenant}/review") public ApiResponse<AccountLegalService.Legal> review(@PathVariable String tenant,@RequestBody AccountLegalService.Review request){return ApiResponse.ok(service.review(tenant,request));}
}
