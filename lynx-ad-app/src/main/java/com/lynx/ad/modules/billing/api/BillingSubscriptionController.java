package com.lynx.ad.modules.billing.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.billing.api.dto.BillsView;
import com.lynx.ad.modules.billing.api.dto.CreateOrderRequest;
import com.lynx.ad.modules.billing.api.dto.CreatePaymentRequest;
import com.lynx.ad.modules.billing.api.dto.MockCompleteRequest;
import com.lynx.ad.modules.billing.api.dto.OrderView;
import com.lynx.ad.modules.billing.api.dto.PatchSubscriptionRequest;
import com.lynx.ad.modules.billing.api.dto.PaymentView;
import com.lynx.ad.modules.billing.api.dto.PlansView;
import com.lynx.ad.modules.billing.api.dto.SubscriptionView;
import com.lynx.ad.modules.billing.application.OrderCommandService;
import com.lynx.ad.modules.billing.application.PaymentCommandService;
import com.lynx.ad.modules.billing.application.SubscriptionQueryService;
import jakarta.validation.Valid;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingSubscriptionController {

    private final SubscriptionQueryService subscriptionQueryService;
    private final OrderCommandService orderCommandService;
    private final PaymentCommandService paymentCommandService;

    public BillingSubscriptionController(
            SubscriptionQueryService subscriptionQueryService,
            OrderCommandService orderCommandService,
            PaymentCommandService paymentCommandService) {
        this.subscriptionQueryService = subscriptionQueryService;
        this.orderCommandService = orderCommandService;
        this.paymentCommandService = paymentCommandService;
    }

    @GetMapping("/plans")
    public ApiResponse<PlansView> plans() {
        return ApiResponse.ok(subscriptionQueryService.listPlans(TenantContext.requireTenantId()));
    }

    @GetMapping("/subscription")
    public ApiResponse<SubscriptionView> subscription() {
        return ApiResponse.ok(subscriptionQueryService.current(TenantContext.requireTenantId()));
    }

    @PatchMapping("/subscription")
    public ApiResponse<SubscriptionView> patch(
            @RequestBody PatchSubscriptionRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
        if (req == null || req.autoRenew() == null) {
            throw new BizException(1001, "autoRenew 必填");
        }
        return ApiResponse.ok(subscriptionQueryService.patchAutoRenew(TenantContext.requireTenantId(), req.autoRenew()));
    }

    @GetMapping("/bills")
    public ApiResponse<BillsView> bills() {
        return ApiResponse.ok(subscriptionQueryService.listBills(TenantContext.requireTenantId()));
    }

    @PostMapping("/orders")
    public ApiResponse<OrderView> createOrder(
            @Valid @RequestBody CreateOrderRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
        return ApiResponse.ok(orderCommandService.create(TenantContext.requireTenantId(), req.planCode(), idem));
    }

    @GetMapping("/orders/pending")
    public ApiResponse<OrderView> pendingOrder() {
        return ApiResponse.ok(orderCommandService.pending(TenantContext.requireTenantId()));
    }

    @PostMapping("/orders/{orderId}/close")
    public ApiResponse<OrderView> close(
            @PathVariable String orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
        return ApiResponse.ok(orderCommandService.close(TenantContext.requireTenantId(), orderId));
    }

    @PostMapping("/payments")
    public ApiResponse<PaymentView> createPayment(
            @Valid @RequestBody CreatePaymentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
        return ApiResponse.ok(paymentCommandService.create(TenantContext.requireTenantId(), req.orderId(), idem));
    }

    @PostMapping("/payments/{paymentId}/mock-complete")
    public ApiResponse<PaymentView> mockComplete(
            @PathVariable String paymentId, @RequestBody MockCompleteRequest req) {
        if (req == null || req.success() == null) {
            throw new BizException(1001, "success 必填");
        }
        return ApiResponse.ok(paymentCommandService.mockComplete(
                TenantContext.requireTenantId(), paymentId, req.success(), req.nonce(), req.paid()));
    }

    @PostMapping(value = "/payments/wechat/notify", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String wechatNotify(@RequestBody(required = false) String body, jakarta.servlet.http.HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return paymentCommandService.wechatNotify(body == null ? "" : body, headers);
    }
}
