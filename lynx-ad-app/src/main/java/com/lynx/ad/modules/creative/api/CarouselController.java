package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.application.CarouselService;
import com.lynx.ad.modules.creative.application.AssetPreviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/creative/carousels")
public class CarouselController {
    private final CarouselService service;
    private final AssetPreviewService previews;
    public CarouselController(CarouselService service, AssetPreviewService previews) { this.service=service; this.previews=previews; }
    @GetMapping("/{id}/preview") public ApiResponse<AssetPreviewService.Preview> preview(@PathVariable String id) {
        var tenant=TenantContext.requireTenantId();
        var image=service.detail(tenant,id).parts().stream().filter(p->"IMAGE".equals(p.role())).findFirst().orElseThrow();
        return ApiResponse.ok(previews.preview(tenant,image.asset().id()));
    }
    @PostMapping public ApiResponse<CarouselService.Detail> create(@RequestBody CarouselService.Create request) {
        return ApiResponse.ok(service.create(TenantContext.requireTenantId(),request));
    }
    @GetMapping("/{id}") public ApiResponse<CarouselService.Detail> detail(@PathVariable String id) {
        return ApiResponse.ok(service.detail(TenantContext.requireTenantId(),id));
    }
}
