package com.lynx.ad.modules.tenant.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lynx.ad.common.exception.BizException;

@Service
public class WorkspaceFoundation {
    private final JdbcTemplate jdbc;
    public WorkspaceFoundation(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean hasMembership(String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE user_id=?",Integer.class,userId) > 0;
    }

    @Transactional
    public void ensure(String tenant) {
        if(jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? AND status='ACTIVE' FOR UPDATE",String.class,tenant).isEmpty())
            throw new BizException(403,"工作空间不可用");
        jdbc.update("INSERT INTO client_space(id,tenant_id,name,is_default) SELECT ?,?,'我的业务',TRUE WHERE NOT EXISTS (SELECT 1 FROM client_space WHERE tenant_id=? AND is_default=TRUE)",tenant,tenant,tenant);
        jdbc.update("INSERT INTO point_wallet(tenant_id) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM point_wallet WHERE tenant_id=?)",tenant,tenant);
    }
}
