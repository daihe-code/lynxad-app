package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.application.AssetPreviewService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformMaterialPreviewController {
    private final AssetPreviewService previews;
    public PlatformMaterialPreviewController(AssetPreviewService previews) { this.previews = previews; }

    @GetMapping("/api/v1/creative/library/preview")
    public ResponseEntity<ApiResponse<AssetPreviewService.Preview>> preview(@RequestParam String id,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(ApiResponse.ok(previews.platformPreview(TenantContext.requireTenantId(), id, refresh)));
    }
}
