package com.lynx.ad.modules.delivery.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.delivery.api.dto.PlanTransferRequest;
import com.lynx.ad.modules.delivery.application.PlanTransferService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delivery/plans/{id}/transfer")
public class PlanTransferController {
    private final PlanTransferService service;
    private final com.lynx.ad.modules.delivery.application.ImportedPlanTransferService imported;
    public PlanTransferController(PlanTransferService service,com.lynx.ad.modules.delivery.application.ImportedPlanTransferService imported) { this.service=service;this.imported=imported; }
    public record Association(String currentQcPlanId) {}
    public record Switch(String requestKey,String expectedQcPlanId) {}
    @PostMapping("/switch") @RequiresActiveTrial
    public ApiResponse<PlanTransferService.Result> switchExisting(@PathVariable String id,@RequestBody Switch request) {
        return ApiResponse.ok(service.switchExisting(TenantContext.requireTenantId(),new PlanId(id),request.requestKey(),request.expectedQcPlanId()));
    }
    @PostMapping("/association") @RequiresActiveTrial
    public ApiResponse<PlanTransferService.Context> associate(@PathVariable String id,@RequestBody Association request) {
        imported.associate(TenantContext.requireTenantId(),new PlanId(id),request.currentQcPlanId());
        return ApiResponse.ok(service.context(TenantContext.requireTenantId(),new PlanId(id)));
    }
    @GetMapping public ApiResponse<PlanTransferService.Context> context(@PathVariable String id) {
        return ApiResponse.ok(service.context(TenantContext.requireTenantId(),new PlanId(id)));
    }
    @PostMapping @RequiresActiveTrial public ApiResponse<PlanTransferService.Result> transfer(@PathVariable String id,@RequestBody PlanTransferRequest request) {
        return ApiResponse.ok(service.transfer(TenantContext.requireTenantId(),new PlanId(id),request));
    }
    @PostMapping("/reconcile") @RequiresActiveTrial public ApiResponse<PlanTransferService.Result> reconcile(@PathVariable String id) {
        return ApiResponse.ok(service.reconcile(TenantContext.requireTenantId(),new PlanId(id)));
    }
}
