package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.application.LibraryDeletionService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class LibraryDeletionController {
    private final LibraryDeletionService service;
    public LibraryDeletionController(LibraryDeletionService service) { this.service=service; }
    public record DeleteRequest(List<String> ids,String accountId) {}

    @PostMapping("/api/v1/creative/library/delete")
    public ApiResponse<LibraryDeletionService.Result> delete(@RequestBody DeleteRequest body) {
        return ApiResponse.ok(service.delete(TenantContext.requireTenantId(),body.ids(),body.accountId()));
    }
}
