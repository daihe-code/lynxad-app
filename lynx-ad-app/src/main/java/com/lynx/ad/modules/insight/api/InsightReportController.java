package com.lynx.ad.modules.insight.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.insight.application.InsightReportQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class InsightReportController {

    private final InsightReportQueryService queryService;

    public InsightReportController(InsightReportQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping({"/api/v1/insight/reports", "/reports"})
    public ApiResponse<List<ReportRowVo>> reports(
            @RequestParam(defaultValue = "plan") String dim,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<String> adIds) {
        // from/to ignored in v0.1 — latest snapshot per entity
        return ApiResponse.ok(queryService.query(TenantContext.requireTenantId(), dim, adIds));
    }

    @PostMapping("/api/v1/insight/reports/export")
    public ApiResponse<Void> export() {
        throw new BizException(501, "导出即将上线");
    }

    @GetMapping("/api/v1/insight/reports/export")
    public ResponseEntity<Void> exportGetNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    public record ReportRowVo(
            String id,
            String name,
            BigDecimal cost,
            BigDecimal gmv,
            BigDecimal roi,
            long impression,
            long click,
            long convert,
            Instant snapshotTs) {}
}
