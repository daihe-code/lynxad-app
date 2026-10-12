package com.lynx.ad.modules.delivery.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.delivery.application.PlanProductsService;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delivery/plans/{id}/products")
public class PlanProductsController {
    private final PlanProductsService service;
    public PlanProductsController(PlanProductsService service) { this.service=service; }
    @GetMapping public ApiResponse<PlanProductsService.Result> products(@PathVariable String id,@RequestParam String startDate,@RequestParam String endDate,@RequestParam(defaultValue="false") boolean refreshPrices) {
        var range=PlanStatisticsRange.optional(startDate,endDate);if(range==null)throw new BizException(1402,"请选择统计时间区间");
        return ApiResponse.ok(service.products(TenantContext.requireTenantId(),new PlanId(id),range,refreshPrices));
    }
    @PostMapping("/{product}/delete") @RequiresActiveTrial
    public ApiResponse<PlanProductsService.ChangeResult> delete(@PathVariable String id,@PathVariable String product,@RequestBody PlanProductsService.DeleteRequest request) {
        return ApiResponse.ok(service.delete(TenantContext.requireTenantId(),new PlanId(id),product,request));
    }
}
