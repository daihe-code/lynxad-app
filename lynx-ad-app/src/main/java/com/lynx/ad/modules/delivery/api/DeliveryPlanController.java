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
import com.lynx.ad.modules.delivery.application.PlanManagementService;
import com.lynx.ad.modules.delivery.application.PlanQueryService;
import com.lynx.ad.modules.delivery.domain.model.PlanAction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryPlanController {

    @RequiresActiveTrial
    @DeleteMapping("/api/v1/delivery/plans/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable String id) {
        commandService.deletePlan(TenantContext.requireTenantId(), new PlanId(id));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/v1/delivery/plans/{id}/draft")
    public ApiResponse<Void> discardDraft(@PathVariable String id) {
        commandService.discardDraft(TenantContext.requireTenantId(), new PlanId(id));
        return ApiResponse.ok(null);
    }

    private final PlanCommandService commandService;
    private final PlanQueryService queryService;
    private final com.lynx.ad.modules.delivery.application.PlanSyncService syncService;
    private final com.lynx.ad.modules.delivery.application.PlanManagementService managementService;
    private com.lynx.ad.modules.delivery.application.CampaignTrendService campaignTrends;
    @org.springframework.beans.factory.annotation.Autowired public void campaignTrends(com.lynx.ad.modules.delivery.application.CampaignTrendService value){campaignTrends=value;}
    private com.lynx.ad.modules.dataops.application.AuditPollJob reviews;
    @org.springframework.beans.factory.annotation.Autowired public void reviewJob(com.lynx.ad.modules.dataops.application.AuditPollJob value){reviews=value;}

    @PostMapping("/api/v1/delivery/plans/{id}/audit-refresh")
    public ApiResponse<PlanVo> refreshAudit(@PathVariable String id) {
        var tenant=TenantContext.requireTenantId();var planId=new PlanId(id);
        // Query visibility/ownership before any platform read. No publishing or write-trial gate.
        queryService.get(tenant,planId);
        var plan=reviewPlans.findById(tenant,planId).orElseThrow(()->new com.lynx.ad.common.exception.BizException(1405,"计划不存在"));
        reviews.refresh(plan);
        return ApiResponse.ok(queryService.get(tenant,planId));
    }
    private com.lynx.ad.modules.delivery.domain.port.AdPlanRepository reviewPlans;
    @org.springframework.beans.factory.annotation.Autowired public void reviewPlans(com.lynx.ad.modules.delivery.domain.port.AdPlanRepository value){reviewPlans=value;}

    public DeliveryPlanController(PlanCommandService commandService, PlanQueryService queryService,
            com.lynx.ad.modules.delivery.application.PlanSyncService syncService,
            com.lynx.ad.modules.delivery.application.PlanManagementService managementService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.syncService = syncService;
        this.managementService = managementService;
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
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) String status,
            @RequestParam(required=false) String platform, @RequestParam(required=false) String bidType,
            @RequestParam(required=false) Integer createdWithin, @RequestParam(required=false) String accountId,
            @RequestParam(required=false) String sortField, @RequestParam(defaultValue="true") boolean descending,
            @RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate,
            @RequestParam(defaultValue="false") boolean refreshStatistics,
            @RequestParam(defaultValue="PAYMENT") com.lynx.ad.modules.delivery.domain.model.PlanMetricTemplate metricTemplate) {
        return ApiResponse.ok(queryService.list(TenantContext.requireTenantId(), pageNum, pageSize,
                new PlanQueryService.Filters(keyword,status,platform,bidType,createdWithin,accountId,sortField,descending,
                        com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange.optional(startDate,endDate),refreshStatistics,metricTemplate)));
    }

    @GetMapping("/api/v1/delivery/plans/{id}")
    public ApiResponse<PlanVo> detail(@PathVariable String id) {
        return ApiResponse.ok(queryService.get(TenantContext.requireTenantId(), new PlanId(id)));
    }
    @GetMapping("/api/v1/delivery/plans/statistics/trend")
    public ApiResponse<com.lynx.ad.modules.delivery.application.CampaignTrendService.Statistics> statisticsTrend(
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String status,
            @RequestParam(required=false) String platform,@RequestParam(required=false) String bidType,
            @RequestParam(required=false) Integer createdWithin,@RequestParam(required=false) String accountId,
            @RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate,
            @RequestParam(defaultValue="PAYMENT") com.lynx.ad.modules.delivery.domain.model.PlanMetricTemplate metricTemplate,
            @RequestParam(defaultValue="false") boolean refreshStatistics) {
        return ApiResponse.ok(campaignTrends.data(TenantContext.requireTenantId(),new PlanQueryService.Filters(keyword,status,platform,bidType,createdWithin,accountId,null,true,
                com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange.optional(startDate,endDate),refreshStatistics,metricTemplate)));
    }
    @PostMapping("/api/v1/delivery/plans/sync")
    public ApiResponse<com.lynx.ad.modules.delivery.application.PlanSyncService.SyncResult> sync(@RequestParam(required=false) String accountId) {
        return ApiResponse.ok(syncService.sync(TenantContext.requireTenantId(),accountId));
    }
    @PostMapping("/api/v1/delivery/plans/{id}/sync-detail")
    public ApiResponse<PlanVo> syncDetail(@PathVariable String id) {
        return ApiResponse.ok(managementService.detail(TenantContext.requireTenantId(),new PlanId(id)));
    }
    @RequiresActiveTrial
    @PutMapping("/api/v1/delivery/plans/{id}/settings")
    public ApiResponse<PlanVo> settings(@PathVariable String id,@RequestBody com.lynx.ad.modules.delivery.api.dto.PlanSettingsRequest req) {
        return ApiResponse.ok(managementService.settings(TenantContext.requireTenantId(),new PlanId(id),req));
    }
    @RequiresActiveTrial
    @PostMapping("/api/v1/delivery/plans/{id}/settings/reconcile")
    public ApiResponse<PlanManagementService.SettingsResult> verifySettings(@PathVariable String id,@RequestBody PlanManagementService.SettingsVerification request) {
        return ApiResponse.ok(managementService.verifySettings(TenantContext.requireTenantId(),new PlanId(id),request));
    }
    @GetMapping("/api/v1/delivery/plans/{id}/materials")
    public ApiResponse<java.util.List<com.lynx.ad.modules.delivery.application.PlanManagementService.Material>> materials(@PathVariable String id) {
        return ApiResponse.ok(managementService.materials(TenantContext.requireTenantId(),new PlanId(id)));
    }
    @GetMapping("/api/v1/delivery/plans/{id}/control-tasks")
    public ApiResponse<java.util.List<com.fasterxml.jackson.databind.JsonNode>> controls(@PathVariable String id,@RequestParam(required=false) String kind,
            @RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate) {
        return ApiResponse.ok(managementService.controls(TenantContext.requireTenantId(),new PlanId(id),kind,
                com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange.optional(startDate,endDate)));
    }
    @RequiresActiveTrial
    @PostMapping("/api/v1/delivery/plans/{id}/control-tasks")
    public ApiResponse<com.lynx.ad.modules.delivery.application.PlanManagementService.ControlResult> control(@PathVariable String id,
            @RequestBody com.lynx.ad.modules.delivery.api.dto.ControlTaskRequest req) {
        return ApiResponse.ok(managementService.control(TenantContext.requireTenantId(),new PlanId(id),req));
    }
    @RequiresActiveTrial
    @PostMapping("/api/v1/delivery/plans/{id}/control-tasks/{taskId}/actions")
    public ApiResponse<com.lynx.ad.modules.delivery.application.PlanManagementService.ControlResult> controlAction(@PathVariable String id,@PathVariable String taskId,
            @RequestBody com.lynx.ad.modules.delivery.api.dto.ControlTaskActionRequest req) {
        return ApiResponse.ok(managementService.controlAction(TenantContext.requireTenantId(),new PlanId(id),taskId,req));
    }
    @GetMapping("/api/v1/delivery/plans/{id}/control-tasks/{taskId}/report")
    public ApiResponse<com.lynx.ad.modules.delivery.application.PlanManagementService.ControlTaskReport> controlReport(@PathVariable String id,@PathVariable String taskId,
            @RequestParam String kind,@RequestParam String startDate,@RequestParam String endDate,@RequestParam(defaultValue="DAY") String granularity) {
        return ApiResponse.ok(managementService.controlReport(TenantContext.requireTenantId(),new PlanId(id),taskId,kind,
                com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange.optional(startDate,endDate),granularity));
    }

    @RequiresActiveTrial
    @PostMapping({"/api/v1/delivery/plans/{id}/publish", "/plans/{id}/publish"})
    public ApiResponse<PublishResponse> publish(@PathVariable String id, @RequestBody(required = false) PublishRequest req) {
        IdempotencyKey key = req == null || req.idempotencyKey() == null ? null : new IdempotencyKey(req.idempotencyKey());
        return ApiResponse.ok(commandService.publish(TenantContext.requireTenantId(), new PlanId(id), key));
    }

    @RequiresActiveTrial
    @PostMapping("/api/v1/delivery/plans/{id}/actions")
    public ApiResponse<PlanVo> actions(@PathVariable String id, @RequestBody ActionRequest req) {
        commandService.act(TenantContext.requireTenantId(), new PlanId(id), parseAction(req.action()), req.pauseReason());
        return ApiResponse.ok(queryService.get(TenantContext.requireTenantId(), new PlanId(id)));
    }
    private PlanAction parseAction(String action) {
        try { return PlanAction.valueOf(action); }
        catch(RuntimeException ex) { throw new com.lynx.ad.common.exception.BizException(1402,"计划操作不正确"); }
    }
}
