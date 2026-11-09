package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.billing.application.EntitlementQueryService;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceTeamService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<MemberRole> CHILD_ROLES = Set.of(MemberRole.OPERATOR, MemberRole.FINANCE, MemberRole.VIEWER);
    private final JdbcTemplate jdbc;
    private final WorkspaceAccess access;
    private final EntitlementQueryService limits;
    private final UserRepository users;

    public WorkspaceTeamService(JdbcTemplate jdbc, WorkspaceAccess access, EntitlementQueryService limits, UserRepository users) {
        this.jdbc = jdbc;
        this.access = access;
        this.limits = limits;
        this.users = users;
    }

    public record Child(String id, String phone, String role, String status, Instant expiresAt) {}
    public record Team(int used, int limit, List<Child> members, List<Child> invites) {}
    public record Invitation(String token, Instant expiresAt) {}
    public record Activation(User user, String tenant, MemberRole role) {}
    private record Pending(String id, String tenant, String phone, String role, String status, Instant expiresAt) {}

    public Team list() {
        String tenant = access.require(MemberRole.OWNER).tenantId();
        return new Team(used(tenant), limits.childAccountLimit(new TenantId(tenant)),
                jdbc.query("SELECT m.membership_id,u.phone,m.member_role,m.status FROM tenant_member m JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.member_role<>'OWNER' AND m.status<>'REMOVED' ORDER BY m.created_at",
                        (r,i) -> new Child(r.getString(1),r.getString(2),r.getString(3),r.getString(4),null),tenant),
                jdbc.query("SELECT id,phone,member_role,status,expires_at FROM workspace_child_invite WHERE tenant_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP ORDER BY created_at DESC",
                        (r,i) -> new Child(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5).toInstant()),tenant));
    }

    @Transactional
    public Invitation invite(PhoneNumber phone, MemberRole role) {
        var owner = lockOwner();
        role = childRole(role);
        String tenant = owner.tenantId();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member m JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND u.phone=?",Integer.class,tenant,phone.value()) > 0)
            throw new BizException(1911,"该手机号已是当前工作空间的成员");
        var existing = jdbc.query("SELECT id,status,expires_at FROM workspace_child_invite WHERE tenant_id=? AND phone=?",
                (r,i) -> new Child(r.getString(1),phone.value(),null,r.getString(2),r.getTimestamp(3).toInstant()),tenant,phone.value()).stream().findFirst();
        boolean reserved = existing.filter(e -> "PENDING".equals(e.status()) && e.expiresAt().isAfter(Instant.now())).isPresent();
        if (!reserved) requireSeat(tenant);
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant until = Instant.now().plus(3, ChronoUnit.DAYS);
        String id = existing.map(Child::id).orElseGet(() -> UUID.randomUUID().toString());
        if (existing.isPresent())
            jdbc.update("UPDATE workspace_child_invite SET member_role=?,token_hash=?,status='PENDING',expires_at=?,created_by=?,created_at=CURRENT_TIMESTAMP WHERE id=?",
                    role.name(), hash(token),Timestamp.from(until),owner.userId(),id);
        else
            jdbc.update("INSERT INTO workspace_child_invite(id,tenant_id,phone,member_role,token_hash,status,expires_at,created_by) VALUES(?,?,?,?,?,'PENDING',?,?)",
                    id,tenant,phone.value(),role.name(),hash(token),Timestamp.from(until),owner.userId());
        event(owner, id, "INVITE");
        return new Invitation(token, until);
    }

    @Transactional
    public void revoke(String id) {
        var owner = lockOwner();
        if (jdbc.update("UPDATE workspace_child_invite SET status='REVOKED' WHERE id=? AND tenant_id=? AND status='PENDING'",id,owner.tenantId()) == 1)
            event(owner, id, "REVOKE");
    }

    @Transactional
    public void update(String id, MemberRole role, boolean active) {
        var owner = lockOwner();
        role = childRole(role);
        var old = jdbc.query("SELECT member_role,status FROM tenant_member WHERE tenant_id=? AND membership_id=? AND member_role<>'OWNER' AND status<>'REMOVED'",
                (r,i) -> new Child(id,null,r.getString(1),r.getString(2),null),owner.tenantId(),id).stream().findFirst()
                .orElseThrow(() -> new BizException(404,"子账户不存在"));
        String status = active ? "ACTIVE" : "DISABLED";
        if (status.equals(old.status()) && role.name().equals(old.role())) return;
        if (active && !"ACTIVE".equals(old.status())) requireSeat(owner.tenantId());
        jdbc.update("UPDATE tenant_member SET member_role=?,status=?,permission_version=permission_version+1 WHERE tenant_id=? AND membership_id=?",
                role.name(), status, owner.tenantId(), id);
        event(owner,id,active ? "UPDATE_MEMBER" : "DISABLE_MEMBER");
    }

    /** Called after successful verification of this phone's OTP, in the login transaction. */
    @Transactional
    public Activation activate(PhoneNumber phone, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String hash = hash(token);
        Pending initial = pending(hash);
        if (!initial.phone().equals(phone.value())) throw invalid();
        User user = users.findByPhone(phone).orElseGet(() -> {
            User created = User.register(phone);
            users.save(created);
            return created;
        });
        users.findByIdForUpdate(user.id()).orElseThrow(WorkspaceTeamService::invalid);
        lock(initial.tenant());
        Pending invite = pending(hash);
        if (!"PENDING".equals(invite.status()) || !invite.expiresAt().isAfter(Instant.now()) || !invite.phone().equals(phone.value()))
            throw invalid();
        if (used(invite.tenant()) > limits.childAccountLimit(new TenantId(invite.tenant()))) throw quota();
        MemberRole role = childRole(MemberRole.valueOf(invite.role()));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND status='ACTIVE'",Integer.class,user.id().value()) != 1
                || jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=? AND user_id=?",Integer.class,invite.tenant(),user.id().value()) > 0) throw invalid();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,membership_id,status) VALUES(?,?,?,?,'ACTIVE')",
                invite.tenant(),user.id().value(),role.name(),id);
        jdbc.update("UPDATE workspace_child_invite SET status='ACCEPTED' WHERE id=?",invite.id());
        event(access.member(invite.tenant(),user.id().value()),id,"ACTIVATE");
        return new Activation(user,invite.tenant(),role);
    }

    private Pending pending(String hash) {
        return jdbc.query("SELECT id,tenant_id,phone,member_role,status,expires_at FROM workspace_child_invite WHERE token_hash=?",
                (r,i) -> new Pending(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6).toInstant()),hash)
                .stream().findFirst().orElseThrow(WorkspaceTeamService::invalid);
    }
    private int used(String tenant) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=? AND member_role<>'OWNER' AND status='ACTIVE'",Integer.class,tenant)
                + jdbc.queryForObject("SELECT COUNT(*) FROM workspace_child_invite WHERE tenant_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP",Integer.class,tenant);
    }
    private void requireSeat(String tenant) {
        if (used(tenant) >= limits.childAccountLimit(new TenantId(tenant))) throw quota();
    }
    private WorkspaceAccess.Membership lockOwner() {
        var owner = access.require(MemberRole.OWNER);
        lock(owner.tenantId());
        return access.require(MemberRole.OWNER);
    }
    private void lock(String tenant) {
        if (jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? AND status='ACTIVE' FOR UPDATE",String.class,tenant).isEmpty()) throw invalid();
    }
    private void event(WorkspaceAccess.Membership m, String id, String type) {
        jdbc.update("INSERT INTO workspace_team_event(id,tenant_id,actor_user_id,target_id,event_type) VALUES(?,?,?,?,?)",
                UUID.randomUUID().toString(),m.tenantId(),m.userId(),id,type);
    }
    private static MemberRole childRole(MemberRole role) {
        if (role == null || !CHILD_ROLES.contains(role)) throw new BizException(1911,"请选择有效的子账户角色");
        return role;
    }
    private static BizException quota() { return new BizException(1910,"子账户额度已满"); }
    private static BizException invalid() { return new BizException(1911,"邀请无效、已使用或已过期"); }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
