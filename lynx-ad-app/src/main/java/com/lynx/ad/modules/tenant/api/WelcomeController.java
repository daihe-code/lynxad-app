package com.lynx.ad.modules.tenant.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant/users/me/welcome")
public class WelcomeController {
    private final JdbcTemplate jdbc;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    public WelcomeController(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager) { this.jdbc=jdbc;this.transactionManager=manager; }
    public record Status(boolean acknowledged) {}
    @GetMapping public ApiResponse<Status> status() {
        return ApiResponse.ok(new Status(jdbc.queryForObject("SELECT COUNT(*) FROM user_welcome_ack WHERE user_id=?",Integer.class,TenantContext.userId().value())>0));
    }
    @PutMapping public ApiResponse<Status> acknowledge() {
        String user=TenantContext.userId().value();
        // Serialize acknowledgements without a database-specific upsert.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE",user);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM user_welcome_ack WHERE user_id=?",Integer.class,user)==0)
                jdbc.update("INSERT INTO user_welcome_ack(user_id) VALUES (?)",user);
        });
        return ApiResponse.ok(new Status(true));
    }
}
