package com.lynx.ad.modules.agentops.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.application.*;
import com.lynx.ad.modules.agentops.infrastructure.OptimizationAutomationStore;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/optimization")
public class OptimizationCenterController {
    private final OptimizationCenterService center;private final OptimizationAutomationStore journal;private final OptimizationExecutionService execution;
    public OptimizationCenterController(OptimizationCenterService center,OptimizationAutomationStore journal,OptimizationExecutionService execution){this.center=center;this.journal=journal;this.execution=execution;}
    public record Limit(BigDecimal budgetLimit){}public record Resume(long expectedVersion){}
    @GetMapping("/statuses") public ApiResponse<java.util.List<OptimizationCenterService.Status>> statuses(@RequestParam java.util.List<String> planIds){return ApiResponse.ok(center.statuses(TenantContext.requireTenantId(),planIds));}
    @GetMapping("/center") public ApiResponse<OptimizationCenterService.Center> center(@RequestParam(required=false) String accountId,@RequestParam(required=false) String state,@RequestParam(required=false) String query,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize){return ApiResponse.ok(center.center(TenantContext.requireTenantId(),accountId,state,query,pageNum,pageSize));}
    @PutMapping("/accounts/{id}/limit") public ApiResponse<Void> limit(@PathVariable String id,@RequestBody Limit body){center.limit(TenantContext.requireTenantId(),id,body.budgetLimit());return ApiResponse.ok(null);}
    @PostMapping("/hold-all") public ApiResponse<Void> holdAll(){center.holdAll(TenantContext.requireTenantId());return ApiResponse.ok(null);}
    @PostMapping("/plans/{id}/resume") public ApiResponse<Void> resume(@PathVariable String id,@RequestBody Resume body){execution.resume(TenantContext.requireTenantId().value(),id,body.expectedVersion());return ApiResponse.ok(null);}
    @GetMapping("/history") public ApiResponse<OptimizationAutomationStore.Page> history(@RequestParam(required=false) String planId,@RequestParam(required=false) String accountId,@RequestParam(required=false) String status,@RequestParam(required=false) LocalDate startDate,@RequestParam(required=false) LocalDate endDate,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize){
        if((startDate==null)!=(endDate==null) || startDate!=null && (endDate.isBefore(startDate) || java.time.temporal.ChronoUnit.DAYS.between(startDate,endDate)>=180))throw new BizException(1802,"历史查询时间区间须为1–180天");var zone=ZoneId.of("Asia/Shanghai");return ApiResponse.ok(journal.history(TenantContext.requireTenantId().value(),planId,accountId,status,startDate==null?null:startDate.atStartOfDay(zone).toInstant(),endDate==null?null:endDate.plusDays(1).atStartOfDay(zone).toInstant(),pageNum,pageSize));}
    @PostMapping("/history/{id}/recheck") public ApiResponse<Void> recheck(@PathVariable String id){execution.recheck(TenantContext.requireTenantId().value(),id,false);return ApiResponse.ok(null);}
    @PostMapping("/history/{id}/acknowledge") public ApiResponse<Void> acknowledge(@PathVariable String id){execution.recheck(TenantContext.requireTenantId().value(),id,true);return ApiResponse.ok(null);}
}
