package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TaskId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.security.RequiresActiveTrial;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.api.dto.LibraryResponse;
import com.lynx.ad.modules.creative.api.dto.LibraryVideoVo;
import com.lynx.ad.modules.creative.api.dto.TaskVo;
import com.lynx.ad.modules.creative.application.CreativeCommandService;
import com.lynx.ad.modules.creative.application.CreativeQueryService;
import com.lynx.ad.modules.creative.application.LibraryQueryService;
import com.lynx.ad.modules.creative.domain.model.CreativeMode;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.domain.service.MediaTokenSigner;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CreativeController {

    private final CreativeCommandService commandService;
    private final CreativeQueryService queryService;
    private final LibraryQueryService libraryQueryService;
    private final StoragePort storagePort;
    private final LynxAdProperties properties;

    public CreativeController(
            CreativeCommandService commandService,
            CreativeQueryService queryService,
            LibraryQueryService libraryQueryService,
            StoragePort storagePort,
            LynxAdProperties properties) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.libraryQueryService = libraryQueryService;
        this.storagePort = storagePort;
        this.properties = properties;
    }

    @RequiresActiveTrial
    @PostMapping({"/api/v1/creative/creatives/generate", "/creatives/generate"})
    public ApiResponse<GenerateResponse> generate(@RequestBody GenerateRequest req) {
        CreativeMode mode = req.mode() == null ? CreativeMode.STANDARD : CreativeMode.valueOf(req.mode());
        List<QcProductId> ids = req.productIds() == null ? List.of() : req.productIds().stream().map(QcProductId::new).toList();
        var taskId = commandService.submit(
                TenantContext.requireTenantId(),
                Optional.ofNullable(req.planId()).map(PlanId::new),
                ids,
                mode);
        return ApiResponse.ok(new GenerateResponse(taskId.value()));
    }

    @GetMapping("/api/v1/creative/creatives/tasks/{taskId}")
    public ApiResponse<TaskVo> task(@PathVariable String taskId) {
        return ApiResponse.ok(queryService.getTask(TenantContext.requireTenantId(), new TaskId(taskId)));
    }

    @PostMapping("/api/v1/creative/creatives/tasks/{taskId}/cancel")
    public ApiResponse<TaskVo> cancel(@PathVariable String taskId) {
        commandService.cancel(TenantContext.requireTenantId(), new TaskId(taskId));
        return task(taskId);
    }

    @PostMapping("/api/v1/creative/packages/{id}/confirm")
    public ApiResponse<PackageVo> confirm(@PathVariable String id) {
        commandService.confirm(TenantContext.requireTenantId(), new PackageId(id));
        return ApiResponse.ok(new PackageVo(id, true));
    }

    @PostMapping("/api/v1/creative/packages/{id}/items/{productId}/replace")
    public ApiResponse<PackageVo> replace(
            @PathVariable String id, @PathVariable String productId, @RequestParam("file") MultipartFile file)
            throws Exception {
        commandService.replaceItem(
                TenantContext.requireTenantId(),
                new PackageId(id),
                new QcProductId(productId),
                file.getInputStream(),
                file.getOriginalFilename());
        return ApiResponse.ok(new PackageVo(id, false));
    }

    @GetMapping("/api/v1/creative/library")
    public ApiResponse<LibraryResponse> libraryFull(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(libraryQueryService.query(
                TenantContext.requireTenantId(),
                type,
                platform,
                parseDate(from),
                parseDate(to),
                status,
                q,
                page == null ? 1 : page,
                pageSize == null ? 8 : pageSize));
    }

    @GetMapping("/api/v1/creative/library/videos")
    public ApiResponse<List<LibraryVideoVo>> library() {
        return ApiResponse.ok(libraryQueryService.libraryVideos(TenantContext.requireTenantId()));
    }

    @GetMapping("/api/v1/creative/media")
    public ResponseEntity<byte[]> media(@RequestParam String token) {
        var payload = MediaTokenSigner.verify(properties.getCrypto().getAesKey(), token);
        byte[] body = storagePort.read(new TenantId(payload.tenantId()), payload.objectKey());
        String ct = payload.objectKey().toLowerCase().endsWith(".mp4") ? "video/mp4" : "image/jpeg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .contentType(MediaType.parseMediaType(ct))
                .body(body);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw);
    }

    public record GenerateRequest(String planId, List<String> productIds, String mode) {}

    public record GenerateResponse(String taskId) {}

    public record PackageVo(String packageId, boolean confirmed) {}
}
