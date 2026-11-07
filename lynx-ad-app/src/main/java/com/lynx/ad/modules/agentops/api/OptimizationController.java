package com.lynx.ad.modules.agentops.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.agentops.application.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delivery/plans/{id}/optimization")
public class OptimizationController {
    private final OptimizationMonitoringService service;
    private OptimizationCreativeService creative;
    private OptimizationRuleDetailsService details;
    private OptimizationRuleResumeService resume;
    @org.springframework.beans.factory.annotation.Autowired public void resume(OptimizationRuleResumeService value){resume=value;}
    private OptimizationDecisionService decisions;private OptimizationReviewService reviews;
    @org.springframework.beans.factory.annotation.Autowired public void decisions(OptimizationDecisionService value,OptimizationReviewService review){decisions=value;reviews=review;}
    @org.springframework.beans.factory.annotation.Autowired public void details(OptimizationRuleDetailsService value){details=value;}
    @org.springframework.beans.factory.annotation.Autowired public void creative(OptimizationCreativeService value){creative=value;}
    public record Change(long expectedVersion,OptimizationPolicy policy) {}
    public OptimizationController(OptimizationMonitoringService service) {this.service=service;}
    @GetMapping public ApiResponse<OptimizationMonitoringService.View> view(@PathVariable String id) {return ApiResponse.ok(service.view(TenantContext.requireTenantId(),new PlanId(id)));}
    public record RulesChange(long expectedVersion,boolean enabled,OptimizationRules rules) {}
    @GetMapping("/rules") public ApiResponse<OptimizationMonitoringService.View> rules(@PathVariable String id) {return ApiResponse.ok(service.rulesView(TenantContext.requireTenantId(),new PlanId(id)));}
    @GetMapping("/details") public ApiResponse<OptimizationRuleDetailsService.Details> details(@PathVariable String id,@RequestParam(defaultValue="false") boolean today,@RequestParam(defaultValue="ALL") String category,@RequestParam(defaultValue="7") int days,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize){return ApiResponse.ok(details.details(TenantContext.requireTenantId(),new PlanId(id),today,category,days,pageNum,pageSize));}
    @PutMapping("/rules") public ApiResponse<OptimizationMonitoringService.View> rules(@PathVariable String id,@RequestBody RulesChange body) {return ApiResponse.ok(service.saveRules(TenantContext.requireTenantId(),new PlanId(id),body.rules(),body.enabled(),body.expectedVersion()));}
    @GetMapping("/rules/resume-preview") public ApiResponse<OptimizationRuleResumeService.Preview> resumePreview(@PathVariable String id){
        return ApiResponse.ok(resume.preview(TenantContext.requireTenantId(),new PlanId(id)));
    }
    @PostMapping("/rules/resume") public ApiResponse<OptimizationRuleResumeService.Result> resume(@PathVariable String id,@RequestBody OptimizationRuleResumeService.Confirmation body){
        return ApiResponse.ok(resume.resume(TenantContext.requireTenantId(),new PlanId(id),body));
    }
    @GetMapping("/decisions/{event}/preview") public ApiResponse<?> preview(@PathVariable String id,@PathVariable String event){return ApiResponse.ok(decisions.preview(TenantContext.requireTenantId().value(),id,event));}
    public record Confirmation(long expectedVersion,String previewHash){}
    @PostMapping("/decisions/{event}/confirm") public ApiResponse<?> confirm(@PathVariable String id,@PathVariable String event,@RequestBody Confirmation body){return ApiResponse.ok(decisions.confirm(TenantContext.requireTenantId().value(),id,event,TenantContext.userId().value(),body.expectedVersion(),body.previewHash()));}
    public record Workflow(String workflow){}
    @PostMapping("/decisions/{event}/track") public ApiResponse<?> track(@PathVariable String id,@PathVariable String event,@RequestBody Workflow body){return ApiResponse.ok(reviews.start(TenantContext.requireTenantId().value(),id,event,body.workflow()));}
    @PutMapping public ApiResponse<OptimizationMonitoringService.View> save(@PathVariable String id,@RequestBody Change body) {return ApiResponse.ok(service.save(TenantContext.requireTenantId(),new PlanId(id),body.policy()==null?null:body.policy().authorized(TenantContext.userId().value()),body.expectedVersion()));}
    @PostMapping("/check") public ApiResponse<OptimizationMonitoringService.View> check(@PathVariable String id) {return ApiResponse.ok(service.run(TenantContext.requireTenantId(),new PlanId(id)));}
    @GetMapping("/candidates") public ApiResponse<?> candidates(@PathVariable String id){return ApiResponse.ok(service.candidates(TenantContext.requireTenantId(),new PlanId(id)));}
    @GetMapping("/creative-jobs") public ApiResponse<?> creativeJobs(@PathVariable String id){return ApiResponse.ok(creative.history(TenantContext.requireTenantId().value(),id));}
    public record CreativeReview(boolean approved){}
    @PostMapping("/creative-jobs/{job}/review") public ApiResponse<?> review(@PathVariable String id,@PathVariable String job,@RequestBody CreativeReview body){creative.approve(TenantContext.requireTenantId().value(),id,job,body.approved());return ApiResponse.ok(null);}
}
