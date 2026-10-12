package com.lynx.ad.modules.identity.infrastructure.persistence;

import com.lynx.ad.common.exception.BizException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** OAuth sessions and shared grants; no plaintext credentials leave the application layer. */
@Repository
public class OauthAuthorizationStore {
    private final JdbcTemplate jdbc;
    public OauthAuthorizationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Application(String id, String platform, String appId, String secretEnc, String redirectUri) {
        @Override public String toString() { return "OAuth application " + platform + "/" + appId; }
    }
    public record Session(String id, String tenantId, String userId, String applicationId, String grantId,
            String status, String catalogJson, String selectedJson, String error, String returnPath, Instant expiresAt) {}
    public record IdentityMetadata(String appId,String authorizedByName,String catalogJson) {}
    public IdentityMetadata identityMetadata(String tenant,String account) {
        return jdbc.query("SELECT app.app_id,p.nickname,(SELECT s.catalog_json FROM oauth_authorization_session s WHERE s.tenant_id=a.tenant_id AND s.grant_id=a.grant_id ORDER BY s.created_at DESC LIMIT 1) AS catalog_json FROM oauth_account a LEFT JOIN oauth_application app ON app.id=a.application_id LEFT JOIN user_profile p ON p.user_id=a.authorized_by_user_id WHERE a.tenant_id=? AND a.id=?",
            (r,i)->new IdentityMetadata(r.getString("app_id"),r.getString("nickname"),r.getString("catalog_json")),tenant,account).stream().findFirst()
            .orElseThrow(()->new BizException(1201,"授权账户不存在"));
    }
    public record Grant(String id, String tenantId, String applicationId, String accessEnc, String refreshEnc,
            Instant accessExpiresAt, Instant refreshExpiresAt, String status, long version) {
        @Override public String toString() { return "OAuth grant " + id + " [credentials redacted]"; }
    }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name); return value == null ? null : value.toInstant();
    }
    public Application application(String id) {
        return jdbc.query("SELECT * FROM oauth_application WHERE id=? AND enabled=TRUE", (r,i) ->
            new Application(r.getString("id"), r.getString("platform"), r.getString("app_id"),
                r.getString("secret_enc"), r.getString("redirect_uri")), id).stream().findFirst()
            .orElseThrow(() -> new BizException(1205, "授权应用不存在或已停用"));
    }
    public synchronized Application configure(String platform, String appId, String secretEnc, String redirect) {
        List<String> ids = jdbc.queryForList("SELECT id FROM oauth_application WHERE platform=? AND app_id=?", String.class, platform, appId);
        if (ids.isEmpty()) {
            String id = UUID.randomUUID().toString();
            try { jdbc.update("INSERT INTO oauth_application(id,platform,app_id,secret_enc,redirect_uri,enabled,updated_at) VALUES(?,?,?,?,?,TRUE,?)",
                id, platform, appId, secretEnc, redirect, Timestamp.from(Instant.now())); }
            catch (DuplicateKeyException ex) { return configure(platform, appId, secretEnc, redirect); }
            return application(id);
        }
        jdbc.update("UPDATE oauth_application SET secret_enc=?,redirect_uri=?,updated_at=? WHERE id=?", secretEnc, redirect, Timestamp.from(Instant.now()), ids.getFirst());
        return application(ids.getFirst());
    }
    public String createSession(String tenant, String user, String application, String stateHash, String returnPath) {
        String id = UUID.randomUUID().toString(); Instant now = Instant.now();
        jdbc.update("INSERT INTO oauth_authorization_session(id,tenant_id,user_id,application_id,state_hash,status,return_path,expires_at,created_at) VALUES(?,?,?,?,?,'CREATED',?,?,?)",
            id, tenant, user, application, stateHash, returnPath, Timestamp.from(now.plusSeconds(900)), Timestamp.from(now));
        return id;
    }
    private Session session(ResultSet r, int i) throws SQLException {
        return new Session(r.getString("id"),r.getString("tenant_id"),r.getString("user_id"),r.getString("application_id"),
            r.getString("grant_id"),r.getString("status"),r.getString("catalog_json"),r.getString("selected_json"),
            r.getString("error_message"),r.getString("return_path"),instant(r,"expires_at"));
    }
    public Session byState(String hash) {
        return jdbc.query("SELECT * FROM oauth_authorization_session WHERE state_hash=?", this::session, hash).stream().findFirst()
            .orElseThrow(() -> new BizException(1206, "授权状态无效，请重新发起授权"));
    }
    public Session session(String id, String tenant, String user, boolean lock) {
        return jdbc.query("SELECT * FROM oauth_authorization_session WHERE id=? AND tenant_id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
            this::session, id, tenant, user).stream().findFirst().orElseThrow(() -> new BizException(1201, "授权会话不存在"));
    }
    public boolean claim(Session session, String codeHash) {
        try { return jdbc.update("UPDATE oauth_authorization_session SET status='EXCHANGING',auth_code_hash=? WHERE id=? AND status='CREATED' AND expires_at>?",
            codeHash, session.id(), Timestamp.from(Instant.now())) == 1; }
        catch (DuplicateKeyException ex) { throw new BizException(1206, "授权码已使用，请重新发起授权"); }
    }
    public String createGrant(Session session, String access, String refresh, Instant accessExp, Instant refreshExp) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO oauth_grant(id,tenant_id,application_id,platform,access_token_enc,refresh_token_enc,token_expires_at,refresh_expires_at,status,created_at) VALUES(?,?,?,'QIANCHUAN',?,?,?,?,'ACTIVE',?)",
            id,session.tenantId(),session.applicationId(),access,refresh,Timestamp.from(accessExp),refreshExp==null?null:Timestamp.from(refreshExp),Timestamp.from(Instant.now()));
        jdbc.update("UPDATE oauth_authorization_session SET grant_id=?,status='DISCOVERING',expires_at=? WHERE id=?", id, Timestamp.from(Instant.now().plusSeconds(3600)), session.id());
        return id;
    }
    public Grant grant(String id, String tenant) {
        return jdbc.query("SELECT * FROM oauth_grant WHERE id=? AND tenant_id=? AND platform='QIANCHUAN'", (r,i) -> new Grant(
            r.getString("id"),r.getString("tenant_id"),r.getString("application_id"),r.getString("access_token_enc"),r.getString("refresh_token_enc"),
            instant(r,"token_expires_at"),instant(r,"refresh_expires_at"),r.getString("status"),r.getLong("version")), id,tenant).stream().findFirst()
            .orElseThrow(() -> new BizException(1201, "授权凭证不存在"));
    }
    public void catalog(String id, String json) {
        jdbc.update("UPDATE oauth_authorization_session SET catalog_json=?,status='SELECTING',error_message=NULL WHERE id=? AND status='DISCOVERING'", json,id);
    }
    public boolean retryDiscovery(String id) {
        return jdbc.update("UPDATE oauth_authorization_session SET status='DISCOVERING',error_message=NULL WHERE id=? AND status='DISCOVERY_FAILED' AND expires_at>?",id,Timestamp.from(Instant.now()))==1;
    }
    public void failed(String id, String status, String error) {
        jdbc.update("UPDATE oauth_authorization_session SET status=?,error_message=? WHERE id=?", status, error.substring(0,Math.min(256,error.length())),id);
    }
    public void complete(String id, String json) {
        jdbc.update("UPDATE oauth_authorization_session SET status='COMPLETED',selected_json=?,error_message=NULL WHERE id=?",json,id);
    }
    public void lockTenant(String tenant) { jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE", String.class,tenant); }
    public boolean lockRefresh(Grant grant, String owner) {
        Instant now=Instant.now();
        return jdbc.update("UPDATE oauth_grant SET refresh_owner=?,refresh_locked_until=? WHERE id=? AND tenant_id=? AND version=? AND status='ACTIVE' AND (refresh_locked_until IS NULL OR refresh_locked_until<?)",
            owner,Timestamp.from(now.plusSeconds(180)),grant.id(),grant.tenantId(),grant.version(),Timestamp.from(now))==1;
    }
    public boolean saveRefresh(Grant grant, String owner, String access, String refresh, Instant accessExp, Instant refreshExp) {
        lockTenant(grant.tenantId());
        Instant refreshExpiry = refreshExp == null ? grant.refreshExpiresAt() : refreshExp;
        int changed=jdbc.update("UPDATE oauth_grant SET access_token_enc=?,refresh_token_enc=?,token_expires_at=?,refresh_expires_at=?,version=version+1,refresh_owner=NULL,refresh_locked_until=NULL WHERE id=? AND version=? AND refresh_owner=? AND status='ACTIVE'",
            access,refresh,Timestamp.from(accessExp),refreshExpiry==null?null:Timestamp.from(refreshExpiry),grant.id(),grant.version(),owner);
        if(changed==1) jdbc.update("UPDATE oauth_account SET access_token_enc=?,refresh_token_enc=?,token_expires_at=?,refresh_expires_at=? WHERE grant_id=? AND status='ACTIVE'",
            access,refresh,Timestamp.from(accessExp),refreshExpiry==null?null:Timestamp.from(refreshExpiry),grant.id());
        return changed==1;
    }
    public void releaseRefresh(String id, String owner) { jdbc.update("UPDATE oauth_grant SET refresh_owner=NULL,refresh_locked_until=NULL WHERE id=? AND refresh_owner=?",id,owner); }
    public void invalidate(String id, String tenant) {
        lockTenant(tenant);
        jdbc.update("UPDATE oauth_grant SET status='INVALID' WHERE id=? AND tenant_id=?",id,tenant);
        jdbc.update("UPDATE oauth_account SET status='UNUSABLE' WHERE grant_id=? AND tenant_id=? AND status='ACTIVE'",id,tenant);
    }
    public void accountStatus(String tenant,String account,String status) {
        jdbc.update("UPDATE oauth_account SET status=? WHERE id=? AND tenant_id=?",status,account,tenant);
    }
}
