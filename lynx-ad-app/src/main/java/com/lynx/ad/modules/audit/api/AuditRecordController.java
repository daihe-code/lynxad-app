package com.lynx.ad.modules.audit.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.modules.audit.api.dto.AuditPageVo;
import com.lynx.ad.modules.audit.application.AuditQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditRecordController {

    private final AuditQueryService queryService;

    public AuditRecordController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/records")
    public ApiResponse<AuditPageVo> list(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(queryService.page(actionType, from, to, q, pageNum, pageSize));
    }
}
