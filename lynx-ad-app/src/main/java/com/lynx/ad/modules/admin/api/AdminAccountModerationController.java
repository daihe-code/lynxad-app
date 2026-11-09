package com.lynx.ad.modules.admin.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.modules.admin.application.AdminAccountModeration;
import com.lynx.ad.modules.admin.infrastructure.AdminUserQuery.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccountModerationController {
    private final AdminAccountModeration moderation;

    public AdminAccountModerationController(AdminAccountModeration moderation) {
        this.moderation = moderation;
    }

    public record BlockRequest(String phone, String userId, String reason) {}
    public record DeleteRequest(String reason) {}

    @GetMapping("/phone-blacklist")
    public ApiResponse<Page<AdminAccountModeration.BlacklistEntry>> list(
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(moderation.blacklist(q, pageNum, pageSize));
    }

    @PostMapping("/phone-blacklist")
    public ApiResponse<Void> block(@RequestBody BlockRequest request) {
        moderation.block(request.phone(), request.userId(), request.reason());
        return ApiResponse.ok(null);
    }

    @PostMapping("/phone-blacklist/{phone}/unblock")
    public ApiResponse<Void> unblock(@PathVariable String phone) {
        moderation.unblock(phone);
        return ApiResponse.ok(null);
    }

    @PostMapping("/users/{id}/delete")
    public ApiResponse<Void> delete(@PathVariable String id, @RequestBody DeleteRequest request) {
        moderation.delete(id, request.reason());
        return ApiResponse.ok(null);
    }
}
