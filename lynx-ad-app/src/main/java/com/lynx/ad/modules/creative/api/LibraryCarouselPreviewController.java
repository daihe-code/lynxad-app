package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.application.LibraryCarouselPreviewService;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class LibraryCarouselPreviewController {
    private final LibraryCarouselPreviewService service;
    public LibraryCarouselPreviewController(LibraryCarouselPreviewService service) { this.service=service; }
    @GetMapping("/api/v1/creative/library/carousel-preview")
    public ResponseEntity<ApiResponse<MaterialLibraryPort.CarouselMedia>> preview(@RequestParam String id) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store")
                .body(ApiResponse.ok(service.preview(TenantContext.requireTenantId(),id)));
    }
}
