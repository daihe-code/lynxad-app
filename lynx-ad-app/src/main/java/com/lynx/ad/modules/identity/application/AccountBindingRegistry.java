package com.lynx.ad.modules.identity.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.billing.application.EntitlementQueryService;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business responsibility is independent of OAuth token validity and authorization path. */
@Service
public class AccountBindingRegistry {
    private final JdbcTemplate jdbc;
    private final EntitlementQueryService entitlements;
    private final WorkspaceFoundation foundation;
    private final String database;
    public AccountBindingRegistry(JdbcTemplate jdbc,EntitlementQueryService entitlements,WorkspaceFoundation foundation) {
        this.jdbc=jdbc;this.entitlements=entitlements;this.foundation=foundation;
        try(var c=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) { database=c.getMetaData().getDatabaseProductName(); }
        catch(java.sql.SQLException ex) { throw new IllegalStateException(ex); }
    }
    public record Binding(String id,String tenantId,String realAccountId,String boundUserId,String clientSpaceId,String status,long version) {}
    public record ExecutionScope(String tenantId,String bindingId,long bindingVersion,String actorUserId,String membershipId,long permissionVersion) {}
    public Optional<Binding> find(String tenant,String account) {
        return jdbc.query("SELECT * FROM ad_account_binding WHERE tenant_id=? AND id=?",(r,i)->new Binding(r.getString("id"),r.getString("tenant_id"),r.getString("real_account_id"),r.getString("bound_user_id"),r.getString("client_space_id"),r.getString("status"),r.getLong("binding_version")),tenant,account).stream().findFirst();
    }
    private String realId(String platform,String advertiser) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest((platform+":advertiser:"+advertiser).getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @Transactional
    public String synchronize(OauthAccount account) {
        String tenant=account.tenantId().value(),id=account.id().value();
        foundation.ensure(tenant); // Serializes tenant quota and initialization in this transaction.
        String real=realId(account.platform().name(),account.advertiserId().value());
        String insert="INSERT INTO real_ad_account(id,platform,id_namespace,advertiser_id) VALUES(?,?,'advertiser',?)";
        if(database.contains("PostgreSQL")) insert+=" ON CONFLICT DO NOTHING";
        else if(database.contains("MySQL")) insert=insert.replace("INSERT INTO","INSERT IGNORE INTO");
        else insert="MERGE INTO real_ad_account(id,platform,id_namespace,advertiser_id) KEY(id) VALUES(?,?,'advertiser',?)";
        jdbc.update(insert,real,account.platform().name(),account.advertiserId().value());
        jdbc.queryForList("SELECT id FROM real_ad_account WHERE id=? FOR UPDATE",String.class,real);
        var existing=find(tenant,id);
        if(existing.isPresent()&&!existing.get().realAccountId().equals(real)) throw new BizException(1206,"广告账户标识不能变更");
        if(existing.isPresent()&&"REVIEW_REQUIRED".equals(existing.get().status())) throw new BizException(1206,"账户绑定需要核验，请联系管理员");
        String responsible=existing.map(Binding::boundUserId).orElseGet(()->account.authorizedByUserId().orElseGet(()-> {
            var owners=jdbc.queryForList("SELECT user_id FROM tenant_member WHERE tenant_id=? AND member_role='OWNER' AND status='ACTIVE'",String.class,tenant);
            return owners.size()==1?owners.getFirst():null;
        }));
        boolean release=List.of("UNBOUND","DELETED").contains(account.status());
        if(!release) {
            if(responsible==null) throw new BizException(1206,"请先核验广告账户责任人");
            requireMember(tenant,responsible);
            if(TenantContext.tenantId().isPresent()) {
                if(!tenant.equals(TenantContext.requireTenantId().value())||!responsible.equals(TenantContext.userId().value()))
                    throw new BizException(403,"仅账户绑定责任人可操作");
            }
            if(account.authorizedByUserId().isPresent()&&!responsible.equals(account.authorizedByUserId().get()))
                throw new BizException(403,"重新授权不能变更账户责任人");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE real_account_id=? AND tenant_id<>?",Integer.class,real,tenant)>0)
                throw new BizException(1206,"该广告账户存在其他工作空间的绑定记录，请先完成账户移交核验");
            var claim=jdbc.queryForList("SELECT binding_id FROM ad_account_claim WHERE real_account_id=?",String.class,real);
            if(!claim.isEmpty()&&!claim.getFirst().equals(id)) throw new BizException(1206,"该广告账户已有有效绑定，请先完成账户移交");
            boolean additional=existing.isEmpty()||!"ACTIVE".equals(existing.get().status());
            int count=jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE tenant_id=? AND status IN ('ACTIVE','REVIEW_REQUIRED')",Integer.class,tenant);
            if(additional&&count>=entitlements.adAccountLimit(account.tenantId())) throw new BizException(1206,"当前工作空间的广告账户容量不足");
        } else if(existing.isPresent()&&TenantContext.tenantId().isPresent()&&!Objects.equals(responsible,TenantContext.userId().value())) {
            throw new BizException(403,"仅账户绑定责任人可操作");
        }
        String status=release?"RELEASED":"ACTIVE";
        if(existing.isEmpty()) jdbc.update("INSERT INTO ad_account_binding(id,tenant_id,real_account_id,bound_user_id,client_space_id,status) VALUES(?,?,?,?,?,?)",id,tenant,real,responsible,tenant,status);
        else if(!status.equals(existing.get().status())) jdbc.update("UPDATE ad_account_binding SET status=?,binding_version=binding_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",status,id,tenant);
        if(release) jdbc.update("DELETE FROM ad_account_claim WHERE real_account_id=? AND binding_id=? AND tenant_id=?",real,id,tenant);
        else jdbc.update("INSERT INTO ad_account_claim(real_account_id,binding_id,tenant_id,bound_user_id) SELECT ?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM ad_account_claim WHERE real_account_id=?)",real,id,tenant,responsible,real);
        if(account.grantId()!=null) jdbc.update("INSERT INTO account_grant_path(binding_id,grant_id) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM account_grant_path WHERE binding_id=? AND grant_id=?)",id,account.grantId(),id,account.grantId());
        return responsible;
    }
    private ExecutionScope requireMember(String tenant,String user) {
        return jdbc.query("SELECT m.membership_id,m.permission_version,m.member_role FROM tenant_member m JOIN sys_tenant t ON t.id=m.tenant_id JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.user_id=? AND m.status='ACTIVE' AND t.status='ACTIVE' AND u.status='ACTIVE'",(r,i)-> {
            if(!List.of("OWNER","MEMBER","OPERATOR").contains(r.getString("member_role"))) throw new BizException(403,"当前成员没有账户执行权限");
            return new ExecutionScope(tenant,null,0,user,r.getString("membership_id"),r.getLong("permission_version"));
        },tenant,user).stream().findFirst().orElseThrow(()->new BizException(403,"工作空间成员身份已失效"));
    }
    public ExecutionScope executionScope(String tenant,String account,String actor) {
        Binding b=find(tenant,account).filter(v->"ACTIVE".equals(v.status())).orElseThrow(()->new BizException(403,"账户绑定不可用"));
        if(actor==null)actor=b.boundUserId(); // Scheduled tasks execute under the binding responsibility, never a tenant owner bypass.
        if(!Objects.equals(actor,b.boundUserId())) throw new BizException(403,"仅账户绑定责任人可操作");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_claim WHERE real_account_id=? AND binding_id=? AND tenant_id=? AND bound_user_id=?",Integer.class,b.realAccountId(),account,tenant,actor)!=1) throw new BizException(403,"账户绑定已变化");
        var member=requireMember(tenant,actor);
        return new ExecutionScope(tenant,account,b.version(),actor,member.membershipId(),member.permissionVersion());
    }
    public void validate(ExecutionScope scope) {
        var current=executionScope(scope.tenantId(),scope.bindingId(),scope.actorUserId());
        if(!current.equals(scope)) throw new BizException(403,"任务授权已变化，请重新确认");
    }
    public void validatePaid(ExecutionScope scope) {
        validate(scope);
        if(entitlements.adAccountLimit(new com.lynx.ad.common.domain.Ids.TenantId(scope.tenantId()))<=0)
            throw new BizException(1904,"工作空间服务已到期，请先续订");
    }
    @Transactional
    public void release(String tenant,String account) {
        foundation.ensure(tenant);
        Binding b=find(tenant,account).orElseThrow(()->new BizException(1201,"账户绑定不存在"));
        jdbc.queryForList("SELECT id FROM real_ad_account WHERE id=? FOR UPDATE",String.class,b.realAccountId());
        jdbc.update("DELETE FROM ad_account_claim WHERE real_account_id=? AND binding_id=? AND tenant_id=?",b.realAccountId(),account,tenant);
        jdbc.update("UPDATE ad_account_binding SET status='RELEASED',binding_version=binding_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status<>'RELEASED'",account,tenant);
    }
}
