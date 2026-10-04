package com.lynx.ad.modules.tenant.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.NotificationId;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.tenant.api.dto.InboxMessageVo;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/notifications")
public class TenantNotificationController {

    private final InboxCommandService inboxCommandService;

    public TenantNotificationController(InboxCommandService inboxCommandService) {
        this.inboxCommandService = inboxCommandService;
    }

    @GetMapping
    public ApiResponse<List<InboxMessageVo>> list() {
        return ApiResponse.ok(inboxCommandService.list(TenantContext.requireTenantId()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id) {
        inboxCommandService.markRead(TenantContext.requireTenantId(), new NotificationId(id), TenantContext.userId());
        return ApiResponse.ok(null);
    }
}
