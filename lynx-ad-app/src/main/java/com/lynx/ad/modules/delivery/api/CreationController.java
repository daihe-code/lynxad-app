package com.lynx.ad.modules.delivery.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delivery/creation")
public class CreationController {
    private final CreationWorkflowService service;
    private final CreationQianchuanGateway qc;
    private com.lynx.ad.modules.delivery.application.PublishedCompositionService compositions;
    @org.springframework.beans.factory.annotation.Autowired public void compositions(com.lynx.ad.modules.delivery.application.PublishedCompositionService value){compositions=value;}
    private com.lynx.ad.modules.delivery.application.ProductRecommendationService productSuggestions;
    @org.springframework.beans.factory.annotation.Autowired public void productSuggestions(com.lynx.ad.modules.delivery.application.ProductRecommendationService value){productSuggestions=value;}
    private com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore draftProducts;
    @org.springframework.beans.factory.annotation.Autowired public void draftProducts(com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore value){draftProducts=value;}
    public CreationController(CreationWorkflowService service,CreationQianchuanGateway qc){this.service=service;this.qc=qc;}
    @GetMapping("/products/suggestions") public ApiResponse<com.lynx.ad.modules.delivery.application.ProductRecommendationService.Result> productSuggestions(
            @RequestParam String accountId,@RequestParam String awemeId,@RequestParam java.util.List<String> productIds,
            @RequestParam(required=false) String draftId,@RequestParam(defaultValue="false") boolean refresh,
            @RequestParam(required=false) java.util.List<String> excludeProductIds) {
        var tenant=TenantContext.requireTenantId();
        return ApiResponse.ok(excludeProductIds==null?productSuggestions.recommend(tenant,accountId,awemeId,productIds,draftId,refresh)
                :productSuggestions.recommend(tenant,accountId,awemeId,productIds,excludeProductIds,draftId,refresh));
    }
    @GetMapping("/products/restore") public ApiResponse<java.util.List<CreationState.Product>> restoreProducts(
            @RequestParam String accountId,@RequestParam String awemeId,@RequestParam java.util.List<String> productIds,@RequestParam(required=false) String draftId) {
        var tenant=TenantContext.requireTenantId();
        var products=qc.restorable(tenant,accountId,awemeId,productIds);
        return ApiResponse.ok(draftProducts==null?products:draftProducts.decorate(tenant.value(),accountId,draftId,products));
    }
    @GetMapping("/products") public ApiResponse<CreationQianchuanGateway.Page> products(@RequestParam String accountId,@RequestParam String awemeId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="ALL") String tab,@RequestParam(defaultValue="false") boolean unpromoted,@RequestParam(defaultValue="false") boolean refreshPrices,@RequestParam(required=false) String draftId,@RequestParam(required=false) String cursor){
        var tenant=TenantContext.requireTenantId();var result=qc.products(tenant,accountId,awemeId,page,pageSize,keyword,tab,unpromoted,null,refreshPrices,cursor);
        return ApiResponse.ok(draftProducts==null?result:new CreationQianchuanGateway.Page(draftProducts.decorate(tenant.value(),accountId,draftId,result.items()),result.total(),result.page(),result.pageSize(),result.nextCursor(),result.hasMore()));
    }
    @RequiresActiveTrial @PostMapping public ApiResponse<CreationView> select(@RequestBody CreationWorkflowService.Selection req){return view(service.select(TenantContext.requireTenantId(),req));}
    @GetMapping("/{id}") public ApiResponse<CreationView> get(@PathVariable String id){return ApiResponse.ok(CreationView.of(service.get(TenantContext.requireTenantId(),id)));}
    @RequiresActiveTrial @PostMapping("/{id}/content-edit") public ApiResponse<CreationView> beginContentEdit(@PathVariable String id){return view(compositions.begin(TenantContext.requireTenantId(),id));}
    @RequiresActiveTrial @PostMapping("/{id}/content-edit/save") public ApiResponse<CreationView> saveContentEdit(@PathVariable String id,@RequestBody CreationWorkflowService.Version req){return view(compositions.save(TenantContext.requireTenantId(),id,req.revision()));}
    @PostMapping("/{id}/content-edit/verify") public ApiResponse<CreationView> verifyContentEdit(@PathVariable String id){return view(compositions.verify(TenantContext.requireTenantId(),id));}
    @RequiresActiveTrial @PostMapping("/{id}/analysis") public ApiResponse<CreationView> analysis(@PathVariable String id,@RequestBody CreationWorkflowService.Analysis req){return view(service.analyze(TenantContext.requireTenantId(),id,req));}
    @GetMapping("/{id}/recommendations") public ApiResponse<CreationView> recommendations(@PathVariable String id,@RequestParam(defaultValue="false") boolean refresh){return view(service.recommendations(TenantContext.requireTenantId(),id,refresh));}
    @RequiresActiveTrial @PutMapping("/{id}/goal") public ApiResponse<CreationView> goal(@PathVariable String id,@RequestBody CreationWorkflowService.Goal req){return view(service.goal(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @PutMapping("/{id}/step") public ApiResponse<CreationView> step(@PathVariable String id,@RequestBody CreationWorkflowService.Step req){return view(service.step(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @PutMapping("/{id}/visibility") public ApiResponse<CreationView> visibility(@PathVariable String id,@RequestBody CreationWorkflowService.Visibility req){return view(service.visibility(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @PostMapping("/{id}/generation") public ApiResponse<CreationView> generate(@PathVariable String id,@RequestBody CreationWorkflowService.Generate req){return view(service.generate(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @PostMapping("/{id}/generation/additions") public ApiResponse<CreationView> addition(@PathVariable String id,@RequestBody CreationWorkflowService.Addition req){return view(service.addition(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @PostMapping("/{id}/materials") public ApiResponse<CreationView> add(@PathVariable String id,@RequestBody CreationWorkflowService.MaterialSelection req){return view(service.add(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @DeleteMapping("/{id}/materials/{material}") public ApiResponse<CreationView> remove(@PathVariable String id,@PathVariable String material,@RequestBody CreationWorkflowService.Feedback req){return view(service.remove(TenantContext.requireTenantId(),id,material,req));}
    @RequiresActiveTrial @PostMapping("/{id}/materials/{material}/regenerate") public ApiResponse<CreationView> regenerate(@PathVariable String id,@PathVariable String material,@RequestBody CreationWorkflowService.Feedback req){return view(service.regenerate(TenantContext.requireTenantId(),id,material,req));}
    @RequiresActiveTrial @PostMapping("/{id}/materials/{material}/version-decision") public ApiResponse<CreationView> materialVersion(@PathVariable String id,@PathVariable String material,@RequestBody CreationWorkflowService.VersionDecision req){return view(service.resolveMaterialVersion(TenantContext.requireTenantId(),id,material,req));}
    @RequiresActiveTrial @PutMapping("/{id}/materials/{material}/titles") public ApiResponse<CreationView> titles(@PathVariable String id,@PathVariable String material,@RequestBody CreationWorkflowService.Titles req){return view(service.titles(TenantContext.requireTenantId(),id,material,req));}
    @RequiresActiveTrial @PostMapping("/{id}/materials/{material}/recheck") public ApiResponse<CreationView> recheck(@PathVariable String id,@PathVariable String material,@RequestBody CreationWorkflowService.Version req){return view(service.recheck(TenantContext.requireTenantId(),id,material,req));}
    @RequiresActiveTrial @PostMapping("/{id}/materials/{material}/sync/retry") public ApiResponse<CreationView> retrySync(@PathVariable String id,@PathVariable String material,@RequestBody CreationWorkflowService.Version req){return view(service.retryMaterialSync(TenantContext.requireTenantId(),id,material,req));}
    @PostMapping("/{id}/materials/{material}/sync/reconcile") public ApiResponse<CreationWorkflowService.MaterialSyncVerification> verifySync(@PathVariable String id,@PathVariable String material,@RequestBody CreationWorkflowService.Version req){return ApiResponse.ok(service.verifyMaterialSync(TenantContext.requireTenantId(),id,material,req));}
    @RequiresActiveTrial @PostMapping("/{id}/materials/{material}/typography") public ApiResponse<CreationView> typography(@PathVariable String id,@PathVariable String material,@RequestBody CreationWorkflowService.Version req){return view(service.repairTypography(TenantContext.requireTenantId(),id,material,req));}
    @RequiresActiveTrial @PostMapping("/{id}/materials/{material}/context") public ApiResponse<CreationView> context(@PathVariable String id,@PathVariable String material){return view(service.retryContext(TenantContext.requireTenantId(),id,material));}
    @RequiresActiveTrial @PostMapping("/{id}/confirm") public ApiResponse<CreationView> confirm(@PathVariable String id,@RequestBody CreationWorkflowService.Version req){return view(service.confirm(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @PostMapping("/{id}/publish") public ApiResponse<CreationView> publish(@PathVariable String id,@RequestBody CreationWorkflowService.Publish req){return view(service.publish(TenantContext.requireTenantId(),id,req));}
    @PostMapping("/{id}/reconcile") public ApiResponse<CreationView> reconcile(@PathVariable String id,@RequestBody CreationWorkflowService.Reconcile req){return view(service.reconcile(TenantContext.requireTenantId(),id,req));}
    private ApiResponse<CreationView> view(CreationState state) {
        return ApiResponse.ok(CreationView.of(service.get(TenantContext.requireTenantId(), state.planId)));
    }
    @PutMapping("/{id}/groups/keep") public ApiResponse<CreationView> keepGroup(@PathVariable String id,@RequestBody CreationWorkflowService.Version req){return view(service.keepProductGroup(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @PostMapping("/{id}/generation/retry") public ApiResponse<CreationView> retryGeneration(@PathVariable String id,@RequestBody CreationWorkflowService.RetryGeneration req){return view(service.retryGeneration(TenantContext.requireTenantId(),id,req));}
    @RequiresActiveTrial @PutMapping("/{id}/products/{product}/titles") public ApiResponse<CreationView> productTitles(@PathVariable String id,@PathVariable String product,@RequestBody CreationWorkflowService.ProductTitles req){return view(service.productTitles(TenantContext.requireTenantId(),id,product,req));}
    @DeleteMapping("/{id}") public ApiResponse<Void> discard(@PathVariable String id){service.discard(TenantContext.requireTenantId(),id);return ApiResponse.ok(null);}
}
