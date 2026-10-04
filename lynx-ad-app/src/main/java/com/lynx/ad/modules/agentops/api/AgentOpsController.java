package com.lynx.ad.modules.agentops.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.agentops.domain.port.AgentActionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentOpsController {

    private final AgentActionRepository repository;

    public AgentOpsController(AgentActionRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/v1/agentops/actions")
    public ApiResponse<List<AgentActionVo>> list() {
        return ApiResponse.ok(repository.listByTenant(TenantContext.requireTenantId()).stream()
                .map(a -> new AgentActionVo(
                        a.id().value(),
                        a.planId().value(),
                        a.type().name(),
                        a.summary(),
                        a.snapshotTs(),
                        a.recoveredAt(),
                        a.open()))
                .toList());
    }

    @PostMapping("/api/v1/agentops/actions/{id}/confirm")
    public ApiResponse<Void> confirm(@PathVariable String id) {
        throw new BizException(501, "确认即将上线");
    }

    public record AgentActionVo(
            String id,
            String planId,
            String type,
            String summary,
            Instant snapshotTs,
            Instant recoveredAt,
            boolean open) {}
}
