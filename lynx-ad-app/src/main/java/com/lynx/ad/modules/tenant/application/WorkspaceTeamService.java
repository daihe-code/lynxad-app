package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.billing.application.EntitlementQueryService;
import com.lynx.ad.modules.tenant.domain.model.PasswordHasher;
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
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceTeamService {
    private static final SecureRandom RANDOM = new SecureRandom();
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

    public record Child(String id, String phone, String role, String status, Instant expiresAt, List<MemberRole> roles) {}
    public record Team(int used, int limit, List<Child> members, List<Child> invites) {}
    public record Invitation(String token, Instant expiresAt, String id) {}
    public record Activation(User user, String tenant, MemberRole role, boolean initialPasswordSet) {}
    private record Pending(String id, String tenant, String phone, List<MemberRole> roles, String status, Instant expiresAt) {}

    public Team list() {
        String tenant = access.require(MemberRole.OWNER).tenantId();
        return new Team(used(tenant), limits.childAccountLimit(new TenantId(tenant)),
                jdbc.query("SELECT m.membership_id,u.phone,m.member_role,m.status,m.child_roles FROM tenant_member m JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.member_role<>'OWNER' AND m.status<>'REMOVED' ORDER BY m.created_at",
                        (r,i) -> new Child(r.getString(1),r.getString(2),r.getString(3),r.getString(4),null,readRoles(r.getString(5),r.getString(3))),tenant),
                jdbc.query("SELECT id,phone,member_role,status,expires_at,child_roles FROM workspace_child_invite WHERE tenant_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP ORDER BY created_at DESC",
                        (r,i) -> new Child(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5).toInstant(),readRoles(r.getString(6),r.getString(3))),tenant));
    }

    @Transactional
    public Invitation invite(PhoneNumber phone, MemberRole role) {
        return inviteWithRoles(phone, role == null ? List.of() : List.of(role));
    }

    @Transactional
    public Invitation inviteWithRoles(PhoneNumber phone, List<MemberRole> roles) {
        var owner = lockOwner();
        var selected = WorkspaceChildRoles.require(roles);
        String primary = selected.getFirst().name(), stored = WorkspaceChildRoles.write(selected);
        String tenant = owner.tenantId();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member m JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND u.phone=?",Integer.class,tenant,phone.value()) > 0)
            throw new BizException(1911,"该手机号已是当前工作空间的成员");
        var existing = jdbc.query("SELECT id,status,expires_at FROM workspace_child_invite WHERE tenant_id=? AND phone=?",
                (r,i) -> new Child(r.getString(1),phone.value(),null,r.getString(2),r.getTimestamp(3).toInstant(),List.of()),tenant,phone.value()).stream().findFirst();
        boolean reserved = existing.filter(e -> "PENDING".equals(e.status()) && e.expiresAt().isAfter(Instant.now())).isPresent();
        if (!reserved) requireSeat(tenant);
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant until = Instant.now().plus(3, ChronoUnit.DAYS);
        String id = existing.map(Child::id).orElseGet(() -> UUID.randomUUID().toString());
        if (existing.isPresent())
            jdbc.update("UPDATE workspace_child_invite SET member_role=?,child_roles=?,token_hash=?,status='PENDING',expires_at=?,created_by=?,created_at=CURRENT_TIMESTAMP WHERE id=?",
                    primary,stored,hash(token),Timestamp.from(until),owner.userId(),id);
        else
            jdbc.update("INSERT INTO workspace_child_invite(id,tenant_id,phone,member_role,child_roles,token_hash,status,expires_at,created_by) VALUES(?,?,?,?,?,?,'PENDING',?,?)",
                    id,tenant,phone.value(),primary,stored,hash(token),Timestamp.from(until),owner.userId());
        event(owner, id, "INVITE");
        return new Invitation(token, until, id);
    }

    @Transactional
    public void updateInviteRole(String id, MemberRole role) {
        updateInviteRoles(id, role == null ? List.of() : List.of(role));
    }

    @Transactional
    public void updateInviteRoles(String id, List<MemberRole> roles) {
        var owner = lockOwner();
        var selected = WorkspaceChildRoles.require(roles);
        var previousRoles = jdbc.query("""
                SELECT member_role,child_roles FROM workspace_child_invite
                WHERE id=? AND tenant_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP FOR UPDATE
                """, (r,i) -> readRoles(r.getString(2),r.getString(1)), id, owner.tenantId()).stream().findFirst().orElseThrow(WorkspaceTeamService::invalid);
        if (selected.equals(previousRoles)) return;
        jdbc.update("UPDATE workspace_child_invite SET member_role=?,child_roles=? WHERE id=? AND tenant_id=?",
                selected.getFirst().name(), WorkspaceChildRoles.write(selected), id, owner.tenantId());
        event(owner,id,"UPDATE_INVITE_ROLE");
    }

    @Transactional
    public void revoke(String id) {
        var owner = lockOwner();
        if (jdbc.update("UPDATE workspace_child_invite SET status='REVOKED' WHERE id=? AND tenant_id=? AND status='PENDING'",id,owner.tenantId()) == 1)
            event(owner, id, "REVOKE");
    }

    @Transactional
    public void update(String id, MemberRole role, Boolean active) {
        updateRoles(id, role == null ? null : List.of(role), active);
    }

    @Transactional
    public void updateRoles(String id, List<MemberRole> roles, Boolean active) {
        var owner = lockOwner();
        if (roles == null && active == null) throw new BizException(1911,"请选择权限或账户状态");
        var old = jdbc.query("SELECT member_role,status,child_roles FROM tenant_member WHERE tenant_id=? AND membership_id=? AND member_role<>'OWNER' AND status<>'REMOVED' FOR UPDATE",
                (r,i) -> new Child(id,null,r.getString(1),r.getString(2),null,readRoles(r.getString(3),r.getString(1))),owner.tenantId(),id).stream().findFirst()
                .orElseThrow(() -> new BizException(404,"子账户不存在"));
        var selected = roles == null ? old.roles() : WorkspaceChildRoles.require(roles);
        String status = active == null ? old.status() : active ? "ACTIVE" : "DISABLED";
        if (status.equals(old.status()) && selected.equals(old.roles())) return;
        if ("ACTIVE".equals(status) && !"ACTIVE".equals(old.status())) {
            WorkspaceChildRoles.require(selected);
            requireSeat(owner.tenantId());
        }
        if (roles == null) {
            jdbc.update("UPDATE tenant_member SET status=?,permission_version=permission_version+1 WHERE tenant_id=? AND membership_id=?",
                    status,owner.tenantId(),id);
        } else {
            jdbc.update("UPDATE tenant_member SET member_role=?,child_roles=?,status=?,permission_version=permission_version+1 WHERE tenant_id=? AND membership_id=?",
                    selected.getFirst().name(),WorkspaceChildRoles.write(selected),status,owner.tenantId(),id);
        }
        event(owner,id,status.equals(old.status()) || "ACTIVE".equals(status) ? "UPDATE_MEMBER" : "DISABLE_MEMBER");
    }

    /** Called after successful verification of this phone's OTP, in the login transaction. */
    @Transactional
    public Activation activate(PhoneNumber phone, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        return activate(phone, "token_hash=?", new Object[]{hash(token)}, false, null, null);
    }

    public PhoneNumber activationPhone(String id) {
        String tenant = access.require(MemberRole.OWNER).tenantId();
        Pending invite = pending("tenant_id=? AND id=?", false, tenant, id);
        var phone = new PhoneNumber(invite.phone());
        validatePending(invite, phone);
        return phone;
    }

    @Transactional
    public boolean activateFromWorkspace(String id, PhoneNumber phone, String password, PasswordHasher hasher) {
        String tenant = access.require(MemberRole.OWNER).tenantId();
        return activate(phone, "tenant_id=? AND id=?", new Object[]{tenant, id}, true, password, hasher).initialPasswordSet();
    }

    private Activation activate(PhoneNumber phone, String where, Object[] args, boolean byOwner, String password, PasswordHasher hasher) {
        Pending initial = pending(where, false, args);
        validatePending(initial, phone);
        var existing = users.findByPhone(phone);
        User user = existing.orElseGet(() -> {
            User created = User.register(phone);
            if (byOwner) created.setPassword(password, hasher);
            users.save(created);
            return created;
        });
        var lockedUser = users.findByIdForUpdate(user.id()).filter(u -> u.phone().equals(phone))
            .orElseThrow(WorkspaceTeamService::invalid);
        lock(initial.tenant());
        var owner = byOwner ? access.require(MemberRole.OWNER) : null;
        Pending invite = pending(where, true, args);
        validatePending(invite, phone);
        if (used(invite.tenant()) > limits.childAccountLimit(new TenantId(invite.tenant()))) throw quota();
        var selected = WorkspaceChildRoles.require(invite.roles());
        MemberRole role = selected.getFirst();
        if (!lockedUser.isActive()
                || jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=? AND user_id=?",Integer.class,invite.tenant(),user.id().value()) > 0) throw invalid();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,membership_id,status,child_roles) VALUES(?,?,?,?,'ACTIVE',?)",
                invite.tenant(),user.id().value(),role.name(),id,WorkspaceChildRoles.write(selected));
        jdbc.update("UPDATE workspace_child_invite SET status='ACCEPTED' WHERE id=?",invite.id());
        event(byOwner ? owner : access.member(invite.tenant(),user.id().value()),id,"ACTIVATE");
        return new Activation(lockedUser,invite.tenant(),role,byOwner && existing.isEmpty());
    }

    private Pending pending(String where, boolean lock, Object... args) {
        String sql = "SELECT id,tenant_id,phone,member_role,status,expires_at,child_roles FROM workspace_child_invite WHERE " + where;
        return jdbc.query(lock ? sql + " FOR UPDATE" : sql,
                (r,i) -> new Pending(r.getString(1),r.getString(2),r.getString(3),readRoles(r.getString(7),r.getString(4)),r.getString(5),r.getTimestamp(6).toInstant()),args)
                .stream().findFirst().orElseThrow(WorkspaceTeamService::invalid);
    }
    private static void validatePending(Pending invite, PhoneNumber phone) {
        if (!"PENDING".equals(invite.status()) || !invite.expiresAt().isAfter(Instant.now()) || !invite.phone().equals(phone.value()))
            throw invalid();
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
    private static List<MemberRole> readRoles(String stored, String primary) {
        return WorkspaceChildRoles.read(stored, MemberRole.valueOf(primary));
    }
    private static BizException quota() { return new BizException(1910,"子账户额度已满"); }
    private static BizException invalid() { return new BizException(1911,"邀请无效、已使用或已过期"); }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
