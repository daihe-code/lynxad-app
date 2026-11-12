package com.lynx.ad.modules.billing.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.billing.application.GenerationCharges;
import com.lynx.ad.modules.billing.application.WorkspacePointOrders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/points")
public class PointRechargeController {
    private final WorkspacePointOrders orders;
    private final GenerationCharges generation;
    public PointRechargeController(WorkspacePointOrders orders, GenerationCharges generation) {
        this.orders = orders;
        this.generation = generation;
    }
    @GetMapping("/generation-access")
    public ApiResponse<GenerationCharges.Access> generationAccess() {
        return ApiResponse.ok(generation.access(TenantContext.requireTenantId().value()));
    }
    public record RechargeRequest(String code) {}
    @GetMapping("/recharge-options")
    public ApiResponse<?> options() { return ApiResponse.ok(orders.options()); }
    @PostMapping("/orders")
    public ApiResponse<?> create(@RequestBody RechargeRequest request, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(orders.create(request.code(),key));
    }
    @GetMapping("/orders/{id}")
    public ApiResponse<?> status(@PathVariable String id) { return ApiResponse.ok(orders.status(id)); }
}
