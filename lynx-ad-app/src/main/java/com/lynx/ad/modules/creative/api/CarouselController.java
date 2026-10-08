package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.application.CarouselService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/creative/carousels")
public class CarouselController {
    private final CarouselService service;
    public CarouselController(CarouselService service) { this.service=service; }
    @PostMapping public ApiResponse<CarouselService.Detail> create(@RequestBody CarouselService.Create request) {
        return ApiResponse.ok(service.create(TenantContext.requireTenantId(),request));
    }
    @GetMapping("/{id}") public ApiResponse<CarouselService.Detail> detail(@PathVariable String id) {
        return ApiResponse.ok(service.detail(TenantContext.requireTenantId(),id));
    }
}
