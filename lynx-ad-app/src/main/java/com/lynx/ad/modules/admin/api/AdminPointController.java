package com.lynx.ad.modules.admin.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.modules.admin.application.AdminPointService;
import com.lynx.ad.modules.billing.application.PointLedgerService;
import com.lynx.ad.modules.billing.application.PointWalletService.Wallet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users/{id}/points")
public class AdminPointController {
    private final AdminPointService points;

    public AdminPointController(AdminPointService points) {
        this.points = points;
    }

    @GetMapping
    public ApiResponse<Wallet> balance(@PathVariable String id, @RequestParam String tenantId) {
        return ApiResponse.ok(points.balance(id, tenantId));
    }

    @GetMapping("/history")
    public ApiResponse<PointLedgerService.Page> history(@PathVariable String id, @RequestParam String tenantId,
            @RequestParam(required = false) String account, @RequestParam(required = false) PointLedgerService.Kind kind,
            @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.ok(points.history(id, tenantId, account, kind, page));
    }

    @PostMapping("/adjust")
    public ApiResponse<Wallet> adjust(@PathVariable String id, @RequestBody AdminPointService.Change change) {
        return ApiResponse.ok(points.adjust(id, change));
    }
}
