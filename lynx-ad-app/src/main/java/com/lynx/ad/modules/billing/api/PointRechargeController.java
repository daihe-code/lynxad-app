package com.lynx.ad.modules.billing.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.billing.application.GenerationCharges;
import com.lynx.ad.modules.billing.application.PaymentCommandService;
import com.lynx.ad.modules.billing.application.WorkspacePointOrders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/points")
public class PointRechargeController {
    private final WorkspacePointOrders orders;
    private final GenerationCharges generation;
    private final PaymentCommandService payments;
    public PointRechargeController(WorkspacePointOrders orders, GenerationCharges generation, PaymentCommandService payments) {
        this.orders = orders;
        this.generation = generation;
        this.payments = payments;
    }
    @GetMapping("/generation-access")
    public ApiResponse<GenerationCharges.Access> generationAccess() {
        return ApiResponse.ok(generation.access(TenantContext.requireTenantId().value()));
    }
    public record RechargeRequest(String code) {}
    @GetMapping("/recharge-options")
    public ApiResponse<?> options() { return ApiResponse.ok(orders.options()); }
    @GetMapping("/recharge-catalog")
    public ApiResponse<WorkspacePointOrders.RechargeCatalog> catalog() { return ApiResponse.ok(orders.catalog()); }
    @GetMapping("/recharge-quote")
    public ApiResponse<?> quote(@RequestParam int amountFen) { return ApiResponse.ok(orders.quote(amountFen)); }
    @PostMapping("/orders")
    public ApiResponse<?> create(@RequestBody RechargeRequest request, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(orders.create(request.code(),key));
    }
    @GetMapping("/orders/{id}")
    public ApiResponse<?> status(@PathVariable String id) { return ApiResponse.ok(orders.status(id)); }

    @GetMapping("/orders/pending")
    public ApiResponse<?> pending() { return ApiResponse.ok(orders.pending()); }

    @PostMapping("/orders/{id}/close")
    public ApiResponse<?> close(@PathVariable String id) {
        return ApiResponse.ok(payments.close(TenantContext.requireTenantId(), id, true));
    }
}
