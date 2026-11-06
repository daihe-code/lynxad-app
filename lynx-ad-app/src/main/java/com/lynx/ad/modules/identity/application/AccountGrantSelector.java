package com.lynx.ad.modules.identity.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Selects only previously verified paths of this binding, never another tenant's grant. */
@Service
public class AccountGrantSelector {
    private final JdbcTemplate jdbc;
    public AccountGrantSelector(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional public boolean activateAlternative(String tenant,String account,String previousGrant) {
        jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE",String.class,tenant);
        var candidates=jdbc.queryForList("SELECT g.* FROM account_grant_path p JOIN ad_account_binding b ON b.id=p.binding_id JOIN oauth_grant g ON g.id=p.grant_id AND g.tenant_id=b.tenant_id JOIN oauth_application app ON app.id=g.application_id AND app.enabled=TRUE WHERE b.id=? AND b.tenant_id=? AND b.status='ACTIVE' AND g.platform='QIANCHUAN' AND g.status='ACTIVE' AND g.id<>? AND g.token_expires_at>? ORDER BY p.created_at DESC,g.id",account,tenant,previousGrant==null?"":previousGrant,Timestamp.from(Instant.now().plusSeconds(60)));
        if(candidates.isEmpty())return false;
        var g=candidates.getFirst();
        // A concurrent reauthorization wins over fallback. Do not replace it.
        return jdbc.update("UPDATE oauth_account SET grant_id=?,application_id=?,access_token_enc=?,refresh_token_enc=?,token_expires_at=?,refresh_expires_at=?,status='ACTIVE' WHERE id=? AND tenant_id=? AND status IN ('ACTIVE','UNUSABLE') AND (grant_id=? OR (grant_id IS NULL AND ? IS NULL))",g.get("id"),g.get("application_id"),g.get("access_token_enc"),g.get("refresh_token_enc"),g.get("token_expires_at"),g.get("refresh_expires_at"),account,tenant,previousGrant,previousGrant)==1;
    }
}
