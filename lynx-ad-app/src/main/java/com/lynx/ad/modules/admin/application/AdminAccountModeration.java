package com.lynx.ad.modules.admin.application;

import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore;
import com.lynx.ad.modules.admin.infrastructure.AdminUserQuery.Page;
import com.lynx.ad.modules.tenant.application.PhoneAccess;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountModeration {
    public record BlacklistEntry(String phone, String reason, Instant updatedAt) {}
    private record Account(String id, String status) {}

    private final AdminAuthService auth;
    private final AdminAccountStore admins;
    private final JdbcTemplate jdbc;
    private final PhoneAccess phones;

    public AdminAccountModeration(AdminAuthService auth, AdminAccountStore admins, JdbcTemplate jdbc, PhoneAccess phones) {
        this.auth = auth;
        this.admins = admins;
        this.jdbc = jdbc;
        this.phones = phones;
    }

    public Page<BlacklistEntry> blacklist(String query, int page, int size) {
        auth.require();
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw new BizException(1904, "分页参数不正确");
        String search = Objects.toString(query, "").trim();
        if (!search.matches("[0-9]{0,11}")) throw new BizException(1904, "请输入手机号数字");
        String filter = "%" + search + "%";
        String from = " FROM platform_phone_access WHERE blocked=TRUE AND phone LIKE ?";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, filter);
        var entries = jdbc.query("SELECT phone,reason,updated_at" + from + " ORDER BY updated_at DESC,phone LIMIT ? OFFSET ?",
                (r, n) -> new BlacklistEntry(r.getString("phone"), r.getString("reason"), AdminAccountStore.instant(r, "updated_at")),
                filter, size, (page - 1) * size);
        return new Page<>(entries, total, page, size);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void block(String rawPhone, String expectedUser, String reason) {
        var admin = auth.require();
        String note = requireReason(reason);
        var phone = phone(rawPhone);
        boolean alreadyBlocked = phones.lock(phone);
        Account user = lockUser(phone);
        if (expectedUser != null && (user == null || !expectedUser.equals(user.id()) || "DELETED".equals(user.status()))) {
            throw new BizException(409, "账户信息已变更，请刷新后重试");
        }
        if (alreadyBlocked) return;
        jdbc.update("UPDATE platform_phone_access SET blocked=TRUE,reason=?,updated_at=CURRENT_TIMESTAMP WHERE phone=?",
                note, phone.value());
        if (user != null && "ACTIVE".equals(user.status())) {
            revokeAccess(user.id(), "BLOCKED");
        }
        admins.audit(admin, "PHONE_BLOCK", "SUCCESS", user == null ? null : user.id(), null, phone.value() + " · " + note);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void unblock(String rawPhone) {
        var admin = auth.require();
        var phone = phone(rawPhone);
        if (!phones.lock(phone)) return;
        Account user = lockUser(phone);
        jdbc.update("UPDATE platform_phone_access SET blocked=FALSE,reason=NULL,updated_at=CURRENT_TIMESTAMP WHERE phone=?",
                phone.value());
        if (user != null && "BLOCKED".equals(user.status())) {
            jdbc.update("UPDATE sys_user SET status='ACTIVE' WHERE id=?", user.id());
        }
        admins.audit(admin, "PHONE_UNBLOCK", "SUCCESS", user == null ? null : user.id(), null, phone.value());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(String userId, String reason) {
        var admin = auth.require();
        String note = requireReason(reason);
        var phone = jdbc.query("SELECT phone FROM sys_user WHERE id=?", (r, n) -> new PhoneNumber(r.getString(1)), userId)
                .stream().findFirst().orElseThrow(() -> new BizException(1904, "用户不存在"));
        phones.lock(phone);
        Account user = lockUser(phone);
        if (user == null || !userId.equals(user.id())) throw new BizException(409, "账户信息已变更，请刷新后重试");
        if ("DELETED".equals(user.status())) return;

        List<String> tenants = jdbc.queryForList("SELECT tenant_id FROM tenant_member WHERE user_id=? ORDER BY tenant_id", String.class, userId);
        for (String tenant : tenants) {
            jdbc.queryForObject("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE", String.class, tenant);
        }
        List<String> owned = jdbc.queryForList(
                "SELECT tenant_id FROM tenant_member WHERE user_id=? AND member_role='OWNER'", String.class, userId);
        if (exists("SELECT COUNT(*) FROM ad_account_binding WHERE bound_user_id=? AND status IN ('ACTIVE','REVIEW_REQUIRED')", userId)) {
            throw new BizException(1904, "请先处理该用户绑定的广告账户");
        }
        for (String tenant : owned) {
            requireClosable(tenant, userId);
            jdbc.update("UPDATE sys_tenant SET status='DELETED' WHERE id=?", tenant);
        }
        revokeAccess(userId, "DELETED");
        jdbc.update("UPDATE tenant_member SET status='REMOVED' WHERE user_id=?", userId);
        admins.audit(admin, "USER_DELETE", "SUCCESS", userId, owned.size() == 1 ? owned.getFirst() : null,
                phone.value() + " · " + note);
    }

    private void requireClosable(String tenant, String user) {
        if (exists("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=? AND user_id<>? AND status<>'REMOVED'", tenant, user)
                || exists("SELECT COUNT(*) FROM workspace_child_invite WHERE tenant_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP", tenant)) {
            throw new BizException(1904, "请先处理该工作空间的子账户和待接受邀请");
        }
        if (exists("SELECT COUNT(*) FROM ad_account_binding WHERE tenant_id=? AND status IN ('ACTIVE','REVIEW_REQUIRED')", tenant)
                || exists("SELECT COUNT(*) FROM ad_plan WHERE tenant_id=? AND status NOT IN ('DRAFT','FINISHED','DELETED','REJECTED')", tenant)) {
            throw new BizException(1904, "请先处理该工作空间的广告账户和未结束的计划");
        }
        if (exists("SELECT COUNT(*) FROM billing_order WHERE tenant_id=? AND status='PENDING_PAY'", tenant)
                || exists("SELECT COUNT(*) FROM billing_subscription WHERE tenant_id=? AND status IN ('ACTIVE','PENDING_DOWNGRADE') AND period_end>CURRENT_TIMESTAMP", tenant)
                || exists("SELECT COUNT(*) FROM point_wallet WHERE tenant_id=? AND (available>0 OR frozen>0)", tenant)) {
            throw new BizException(1904, "请先处理该工作空间的套餐、订单和积分余额");
        }
    }

    private boolean exists(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args) > 0;
    }

    private Account lockUser(PhoneNumber phone) {
        return jdbc.query("SELECT id,status FROM sys_user WHERE phone=? FOR UPDATE",
                (r, n) -> new Account(r.getString("id"), r.getString("status")), phone.value()).stream().findFirst().orElse(null);
    }

    private void revokeAccess(String user, String status) {
        jdbc.update("UPDATE sys_user SET status=?,session_version=session_version+1 WHERE id=?", status, user);
        jdbc.update("UPDATE tenant_member SET permission_version=permission_version+1 WHERE user_id=?", user);
    }

    private static PhoneNumber phone(String raw) {
        return new PhoneNumber(raw == null ? null : raw.trim());
    }

    private static String requireReason(String raw) {
        String reason = Objects.toString(raw, "").trim();
        if (reason.length() < 2 || reason.length() > 300) throw new BizException(1904, "请填写2至300字的操作原因");
        return reason;
    }
}
