package com.lynx.ad.modules.identity.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.application.EngineExecutionTickets;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/execution")
public class InternalExecutionController {
    private final EngineExecutionTickets tickets;private final String key;
    public InternalExecutionController(EngineExecutionTickets tickets,@Value("${lynx.ad.monitoring.engine-api-key:}") String key) {this.tickets=tickets;this.key=key;}
    public record ValidateRequest(String ticket,String tenant_id,String binding_id,String task_id) {}
    @PostMapping("/validate") public ApiResponse<?> validate(@RequestHeader(value="X-API-Key",defaultValue="") String provided,@RequestBody ValidateRequest request) {
        if(key.isBlank()||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8)))throw new BizException(403,"服务身份无效");
        tickets.validate(request.ticket(),request.tenant_id(),request.binding_id(),request.task_id());
        return ApiResponse.ok(java.util.Map.of("allowed",true));
    }
}
