package com.lynx.ad.modules.delivery.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.security.RequiresActiveTrial;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.delivery.api.dto.ActionRequest;
import com.lynx.ad.modules.delivery.api.dto.PlanDraftRequest;
import com.lynx.ad.modules.delivery.api.dto.PlanDraftResponse;
import com.lynx.ad.modules.delivery.api.dto.PlanListVo;
import com.lynx.ad.modules.delivery.api.dto.PlanVo;
import com.lynx.ad.modules.delivery.api.dto.PublishRequest;
import com.lynx.ad.modules.delivery.api.dto.PublishResponse;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.application.PlanQueryService;
import com.lynx.ad.modules.delivery.domain.model.PlanAction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryPlanController {

    private final PlanCommandService commandService;
    private final PlanQueryService queryService;

    public DeliveryPlanController(PlanCommandService commandService, PlanQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @RequiresActiveTrial
    @PostMapping({"/api/v1/delivery/plans", "/plans"})
    public ApiResponse<PlanDraftResponse> createDraft(@RequestBody PlanDraftRequest req) {
        return ApiResponse.ok(commandService.createDraft(TenantContext.requireTenantId(), req));
    }

    @RequiresActiveTrial
    @PutMapping("/api/v1/delivery/plans/{id}")
    public ApiResponse<PlanVo> update(@PathVariable String id, @RequestBody PlanDraftRequest req) {
        return ApiResponse.ok(commandService.updateDraft(TenantContext.requireTenantId(), new PlanId(id), req));
    }

    @GetMapping({"/api/v1/delivery/plans", "/plans"})
    public ApiResponse<PlanQueryService.PageResult<PlanListVo>> list(
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(queryService.list(TenantContext.requireTenantId(), pageNum, pageSize));
    }

    @GetMapping("/api/v1/delivery/plans/{id}")
    public ApiResponse<PlanVo> detail(@PathVariable String id) {
        return ApiResponse.ok(queryService.get(TenantContext.requireTenantId(), new PlanId(id)));
    }

    @RequiresActiveTrial
    @PostMapping({"/api/v1/delivery/plans/{id}/publish", "/plans/{id}/publish"})
    public ApiResponse<PublishResponse> publish(@PathVariable String id, @RequestBody(required = false) PublishRequest req) {
        IdempotencyKey key = req == null || req.idempotencyKey() == null ? null : new IdempotencyKey(req.idempotencyKey());
        return ApiResponse.ok(commandService.publish(TenantContext.requireTenantId(), new PlanId(id), key));
    }

    @PostMapping("/api/v1/delivery/plans/{id}/actions")
    public ApiResponse<PlanVo> actions(@PathVariable String id, @RequestBody ActionRequest req) {
        return ApiResponse.ok(commandService.act(TenantContext.requireTenantId(), new PlanId(id), PlanAction.valueOf(req.action())));
    }
}
