package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.application.VideoRemakeService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/creative/video-remakes")
public class VideoRemakeController {
    private final VideoRemakeService service;

    public VideoRemakeController(VideoRemakeService service) { this.service = service; }

    @GetMapping("/capability")
    public ApiResponse<VideoRemakeService.Capability> capability() { return ApiResponse.ok(service.capability(tenant())); }

    @GetMapping
    public ApiResponse<List<VideoRemakeService.View>> history() { return ApiResponse.ok(service.history(tenant(), actor())); }

    @GetMapping("/{id}")
    public ApiResponse<VideoRemakeService.View> task(@PathVariable String id) { return ApiResponse.ok(service.get(tenant(), actor(), id)); }

    @PostMapping
    public ApiResponse<VideoRemakeService.View> create(@RequestBody VideoRemakeService.Start request) {
        return ApiResponse.ok(service.create(tenant(), actor(), request));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<VideoRemakeService.View> confirm(@PathVariable String id, @RequestBody VideoRemakeService.Confirm request) {
        return ApiResponse.ok(service.confirm(tenant(), actor(), id, request));
    }

    @PostMapping("/{id}/save")
    public ApiResponse<Map<String, String>> save(@PathVariable String id) {
        return ApiResponse.ok(Map.of("assetId", service.save(tenant(), actor(), id)));
    }

    @PostMapping("/uploads")
    public ApiResponse<Map<String, Object>> upload(@RequestParam String bindingId, @RequestBody VideoRemakeService.Upload request) {
        return ApiResponse.ok(service.upload(tenant(), actor(), bindingId, request));
    }

    @PutMapping(value = "/uploads/{id}/chunks/{index}", consumes = "application/octet-stream")
    public ApiResponse<Void> chunk(@RequestParam String bindingId, @PathVariable String id, @PathVariable int index,
            HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > VideoRemakeService.CHUNK_BYTES)
            throw new BizException(1402, "单个分片不能超过 1 MiB");
        byte[] data = request.getInputStream().readNBytes(VideoRemakeService.CHUNK_BYTES + 1);
        service.chunk(tenant(), actor(), bindingId, id, index, data);
        return ApiResponse.ok(null);
    }

    @PostMapping("/uploads/{id}/finish")
    public ApiResponse<Map<String,String>> finish(@RequestParam String bindingId, @PathVariable String id) {
        return ApiResponse.ok(Map.of("assetId", service.finishUpload(tenant(), actor(), bindingId, id)));
    }

    @PostMapping("/uploads/link")
    public ApiResponse<Map<String, Object>> importLink(@RequestParam String bindingId,
            @RequestBody VideoRemakeService.LinkImport request) {
        return ApiResponse.ok(service.importLink(tenant(), actor(), bindingId, request));
    }

    private String tenant() { return TenantContext.requireTenantId().value(); }
    private String actor() { return TenantContext.userId().value(); }
}
