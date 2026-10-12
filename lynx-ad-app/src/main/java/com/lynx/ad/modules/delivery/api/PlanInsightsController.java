package com.lynx.ad.modules.delivery.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.delivery.application.PlanInsightsService;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delivery/plans/{id}/insights")
public class PlanInsightsController {
    private final PlanInsightsService service;
    public PlanInsightsController(PlanInsightsService service) { this.service=service; }
    private PlanStatisticsRange range(String start,String end) {
        var range=PlanStatisticsRange.optional(start,end);
        if(range==null)throw new BizException(1402,"请选择统计时间区间");return range;
    }
    @GetMapping("/data")
    public ApiResponse<PlanInsightsService.Statistics> data(@PathVariable String id,@RequestParam String startDate,@RequestParam String endDate,@RequestParam(defaultValue="false") boolean refresh) {
        return ApiResponse.ok(service.data(TenantContext.requireTenantId(),new PlanId(id),range(startDate,endDate),refresh));
    }
    @GetMapping("/logs")
    public ApiResponse<?> logs(@PathVariable String id,@RequestParam String startDate,@RequestParam String endDate,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="PLATFORM") String source) {
        var tenant=TenantContext.requireTenantId();var plan=new PlanId(id);var range=range(startDate,endDate);
        return switch(source) {
            case "PLATFORM"->ApiResponse.ok(service.platformLogs(tenant,plan,range,page));
            case "LINGTOU"->ApiResponse.ok(service.localLogs(tenant,plan,range,page));
            default->throw new BizException(1402,"日志来源不正确");
        };
    }
    @GetMapping("/guarantee")
    public ApiResponse<PlanInsightsService.GuaranteeView> guarantee(@PathVariable String id,@RequestParam String startDate,@RequestParam String endDate,@RequestParam(defaultValue="1") int page) {
        return ApiResponse.ok(service.guarantee(TenantContext.requireTenantId(),new PlanId(id),range(startDate,endDate),page));
    }
    @GetMapping("/guarantee/daily-spending")
    public ApiResponse<PlanInsightsService.DailySpending> dailySpending(@PathVariable String id,@RequestParam String startDate,@RequestParam String endDate) {
        return ApiResponse.ok(service.dailySpending(TenantContext.requireTenantId(),new PlanId(id),range(startDate,endDate)));
    }
}
