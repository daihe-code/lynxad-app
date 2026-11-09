package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneAccess {
    private final JdbcTemplate jdbc;
    private final String ensurePhone;

    public PhoneAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        String database = jdbc.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
        ensurePhone = switch (database) {
            case "MySQL", "MariaDB" -> "INSERT IGNORE INTO platform_phone_access(phone) VALUES (?)";
            case "H2" -> "MERGE INTO platform_phone_access(phone) KEY(phone) VALUES (?)";
            default -> "INSERT INTO platform_phone_access(phone) VALUES (?) ON CONFLICT (phone) DO NOTHING";
        };
    }

    public void requireAllowed(PhoneNumber phone) {
        if (!jdbc.queryForList("""
                SELECT phone FROM platform_phone_access WHERE phone=? AND blocked=TRUE
                UNION ALL SELECT phone FROM sys_user WHERE phone=? AND status='DELETED'
                """, String.class, phone.value(), phone.value()).isEmpty()) {
            throw blocked();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAllowed(PhoneNumber phone) {
        if (lock(phone)) throw blocked();
        requireAllowed(phone);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lock(PhoneNumber phone) {
        jdbc.update(ensurePhone, phone.value());
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT blocked FROM platform_phone_access WHERE phone=? FOR UPDATE", Boolean.class, phone.value()));
    }

    private static BizException blocked() {
        return new BizException(1121, "该手机号已被限制注册或登录");
    }
}
