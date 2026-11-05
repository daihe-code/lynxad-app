package com.lynx.ad.modules.delivery.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.delivery.application.PlanMaterialsService;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delivery/plans/{id}/insights/materials")
public class PlanMaterialsController {
    private final PlanMaterialsService service;
    public PlanMaterialsController(PlanMaterialsService service) {this.service=service;}
    @GetMapping public ApiResponse<PlanMaterialsService.Result> materials(@PathVariable String id,@RequestParam String startDate,@RequestParam String endDate,@RequestParam(defaultValue="VIDEO") String type,@RequestParam(defaultValue="false") boolean currentOnly) {
        var range=PlanStatisticsRange.optional(startDate,endDate);if(range==null)throw new BizException(1402,"请选择统计时间区间");
        return ApiResponse.ok(service.materials(TenantContext.requireTenantId(),new PlanId(id),range,type,currentOnly));
    }
}
