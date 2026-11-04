package com.lynx.ad.modules.creative.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.creative.application.LibraryMaterialStatisticsService;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class LibraryMaterialStatisticsController {
    private final LibraryMaterialStatisticsService statistics;
    public LibraryMaterialStatisticsController(LibraryMaterialStatisticsService statistics){this.statistics=statistics;}
    @GetMapping("/api/v1/creative/library/statistics")
    public ResponseEntity<ApiResponse<LibraryMaterialStatisticsService.Result>> query(@RequestParam String id,
            @RequestParam(required=false) String accountId,@RequestParam(required=false) String from,@RequestParam(required=false) String to) {
        var range=PlanStatisticsRange.optional(from,to);
        if(range==null){var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));range=new PlanStatisticsRange(today.minusDays(6),today);}
        return ResponseEntity.ok().header("Cache-Control","private, no-store")
                .body(ApiResponse.ok(statistics.query(TenantContext.requireTenantId(),id,accountId,range)));
    }
}
