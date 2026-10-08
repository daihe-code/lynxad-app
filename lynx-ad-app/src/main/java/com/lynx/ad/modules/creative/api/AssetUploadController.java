package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/creative/assets")
public class AssetUploadController {
    private final AssetUploadService service;
    public AssetUploadController(AssetUploadService service) { this.service=service; }
    @GetMapping("/upload-policy") public ApiResponse<com.lynx.ad.modules.creative.domain.service.AssetFilePolicy.Policy> policy() {
        return ApiResponse.ok(com.lynx.ad.modules.creative.domain.service.AssetFilePolicy.policy());
    }
    @PostMapping("/uploads/init") public ApiResponse<AssetUploadService.InitResult> init(@RequestBody AssetUploadService.InitRequest request) { return ApiResponse.ok(service.init(TenantContext.requireTenantId(),request)); }
    @PostMapping("/{id}/file") public ApiResponse<AssetUploadService.Detail> upload(@PathVariable String id, @RequestParam MultipartFile file) { return ApiResponse.ok(service.upload(TenantContext.requireTenantId(),id,file)); }
    public record CompleteRequest(List<StoragePort.UploadPart> parts) {}
    @PostMapping("/{id}/upload-complete") public ApiResponse<AssetUploadService.Detail> complete(@PathVariable String id, @RequestBody CompleteRequest request) { return ApiResponse.ok(service.complete(TenantContext.requireTenantId(),id,request.parts())); }
    @GetMapping("/{id}") public ApiResponse<AssetUploadService.Detail> detail(@PathVariable String id) { return ApiResponse.ok(service.detail(TenantContext.requireTenantId(),id)); }
    @PostMapping("/{id}/review/retry") public ApiResponse<AssetUploadService.Detail> retryReview(@PathVariable String id) { return ApiResponse.ok(service.retryReview(TenantContext.requireTenantId(),id)); }
    public record RetryRequest(String accountId, boolean acknowledgeUncertain) {}
    @PostMapping("/{id}/sync/retry") public ApiResponse<AssetUploadService.Detail> retry(@PathVariable String id,@RequestBody RetryRequest request) { return ApiResponse.ok(service.retry(TenantContext.requireTenantId(),id,request.accountId(),request.acknowledgeUncertain())); }
    @DeleteMapping("/{id}/upload") public ApiResponse<Void> cancel(@PathVariable String id) { service.cancel(TenantContext.requireTenantId(),id); return ApiResponse.ok(null); }
}
