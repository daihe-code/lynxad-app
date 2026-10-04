package com.lynx.ad.modules.billing.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.billing.api.dto.TrialVo;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingTrialController {

    private final BillingTrialService billingTrialService;

    public BillingTrialController(BillingTrialService billingTrialService) {
        this.billingTrialService = billingTrialService;
    }

    @PostMapping("/trial/start")
    public ApiResponse<TrialVo> start() {
        return ApiResponse.ok(billingTrialService.start(TenantContext.requireTenantId(), Instant.now()));
    }
}
