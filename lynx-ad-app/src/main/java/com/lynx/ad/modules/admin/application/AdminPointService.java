package com.lynx.ad.modules.admin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore;
import com.lynx.ad.modules.billing.application.PointLedgerService;
import com.lynx.ad.modules.billing.application.PointWalletService;
import com.lynx.ad.modules.billing.application.WorkspacePointOrders;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPointService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.9999");
    private final AdminAuthService auth;
    private final AdminAccountStore admins;
    private final JdbcTemplate jdbc;
    private final WorkspaceFoundation foundation;
    private final WorkspacePointOrders orders;
    private final PointWalletService points;
    private final PointLedgerService ledger;
    private final ObjectMapper json;

    public AdminPointService(AdminAuthService auth, AdminAccountStore admins, JdbcTemplate jdbc,
                             WorkspaceFoundation foundation, WorkspacePointOrders orders, PointWalletService points,
                             PointLedgerService ledger, ObjectMapper json) {
        this.auth = auth;
        this.admins = admins;
        this.jdbc = jdbc;
        this.foundation = foundation;
        this.orders = orders;
        this.points = points;
        this.ledger = ledger;
        this.json = json;
    }

    public enum Direction { ADD, DEDUCT }

    public record Change(UUID requestId, String tenantId, Direction direction, BigDecimal amount,
                         Long expectedVersion, String reason) {}

    @Transactional(readOnly = true)
    public PointLedgerService.Page history(String user, String tenant, String account, PointLedgerService.Kind kind, int page) {
        auth.require();
        validateTenant(tenant);
        requireMembership(user, tenant);
        return ledger.history(tenant, account, kind, page);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointWalletService.Wallet balance(String user, String tenant) {
        auth.require();
        prepareWallet(user, tenant);
        return points.balance(tenant);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointWalletService.Wallet adjust(String user, Change request) {
        var admin = auth.require();
        if (request == null || request.requestId() == null || request.direction() == null
                || request.expectedVersion() == null || request.expectedVersion() < 1) {
            throw new BizException(1904, "请重新打开积分调整后重试");
        }
        var value = request.amount();
        if (value == null || value.signum() <= 0 || value.scale() > 4 || value.compareTo(MAX_AMOUNT) > 0) {
            throw new BizException(1901, "请输入大于零的积分，最多四位小数");
        }
        String reason = request.reason() == null ? "" : request.reason().strip();
        if (reason.codePointCount(0, reason.length()) < 2 || reason.length() > 300) {
            throw new BizException(1904, "请填写2至300字的操作原因");
        }

        String tenant = request.tenantId();
        prepareWallet(user, tenant);
        boolean increase = request.direction() == Direction.ADD;
        BigDecimal delta = increase ? value : value.negate();
        String displayAmount = value.stripTrailingZeros().toPlainString();
        String fingerprint = fingerprint(List.of(admin.id(), user, request.direction(), displayAmount, request.expectedVersion(), reason));
        var result = points.adjustAvailable(tenant, request.requestId(), fingerprint, delta, request.expectedVersion());
        if (result.applied()) {
            var after = result.wallet().available();
            admins.audit(admin, "POINT_ADJUST", "SUCCESS", user, tenant,
                    (increase ? "增加 " : "扣减 ") + displayAmount + " 积分（"
                            + after.subtract(delta).stripTrailingZeros().toPlainString() + " → "
                            + after.stripTrailingZeros().toPlainString() + "） · " + reason);
        }
        return result.wallet();
    }

    private void prepareWallet(String user, String tenant) {
        validateTenant(tenant);
        if (jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? AND status='ACTIVE' FOR UPDATE",
                        String.class, tenant).isEmpty()) {
            throw new BizException(1904, "该坤境账户不可用");
        }
        requireMembership(user, tenant);
        foundation.ensure(tenant);
        orders.ensureCurrentGrant(tenant);
    }

    private void validateTenant(String tenant) {
        if (tenant == null || tenant.isBlank() || tenant.length() > 36) {
            throw new BizException(1904, "该坤境账户不可用");
        }
    }

    private void requireMembership(String user, String tenant) {
        var membership = jdbc.queryForList("""
                SELECT m.tenant_id FROM tenant_member m JOIN sys_user u ON u.id=m.user_id JOIN sys_tenant t ON t.id=m.tenant_id
                WHERE m.user_id=? AND m.tenant_id=? AND m.status<>'REMOVED' AND u.status<>'DELETED' AND t.status='ACTIVE'
                """, String.class, user, tenant);
        if (membership.isEmpty()) throw new BizException(1904, "该用户不属于所选坤境账户，请刷新用户列表");
    }

    private String fingerprint(List<?> fields) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(fields)));
        } catch (Exception ex) {
            throw new IllegalStateException("积分调整请求无法编码", ex);
        }
    }
}
