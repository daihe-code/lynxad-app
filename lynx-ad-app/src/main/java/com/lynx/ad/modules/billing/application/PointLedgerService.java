package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PointLedgerService {
    private static final int PAGE_SIZE = 20;
    private final JdbcTemplate jdbc;

    public PointLedgerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public enum Kind { RECHARGE, GRANT, CREDIT, RESERVE, SETTLE, RELEASE, REFUND, ADMIN_CREDIT, ADMIN_DEBIT, OTHER }

    public record Account(String userId, String name, String phoneSuffix, String role) {}

    public record Entry(String id, Kind kind, String amount, String availableChange, Account account,
                        String reference, Instant createdAt) {}

    public record Page(List<Entry> records, List<Account> accounts, int page, boolean hasMore) {}

    public Page history(String tenant, String account, Kind kind, int page) {
        if (account != null && (account.isBlank() || account.length() > 36)) {
            throw new BizException(1001, "积分归属账户无效");
        }
        int current = Math.clamp(page, 1, 10000);
        var sql = new StringBuilder("""
                WITH ledger AS (
                    SELECT l.id, l.tenant_id, l.event_key, l.created_at, l.amount, r.amount AS reserved_amount,
                        r.task_key, COALESCE(l.actor_user_id, r.actor_user_id) AS actor_id,
                        CASE
                            WHEN l.event_type = 'CREDIT' AND l.event_key LIKE 'recharge:%' THEN 'RECHARGE'
                            WHEN l.event_type = 'CREDIT' AND l.event_key LIKE 'subscription:%' THEN 'GRANT'
                            WHEN l.event_type IN ('CREDIT','RESERVE','SETTLE','RELEASE','REFUND','ADMIN_CREDIT','ADMIN_DEBIT')
                                THEN l.event_type
                            ELSE 'OTHER'
                        END AS kind
                    FROM point_ledger l
                    LEFT JOIN point_reservation r ON r.tenant_id=l.tenant_id AND r.id=l.reservation_id
                    WHERE l.tenant_id=?
                )
                SELECT l.*, m.member_role, NULLIF(TRIM(p.nickname), '') AS nickname, RIGHT(u.phone, 4) AS phone_suffix
                FROM ledger l
                LEFT JOIN tenant_member m ON m.tenant_id=l.tenant_id AND m.user_id=l.actor_id
                LEFT JOIN sys_user u ON u.id=m.user_id AND u.status<>'DELETED'
                LEFT JOIN user_profile p ON p.user_id=u.id
                WHERE 1=1
                """);
        var args = new ArrayList<Object>();
        args.add(tenant);
        if (account != null) {
            sql.append(" AND l.actor_id=?");
            args.add(account);
        }
        if (kind != null) {
            sql.append(" AND l.kind=?");
            args.add(kind.name());
        }
        sql.append(" ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?");
        args.add(PAGE_SIZE + 1);
        args.add((current - 1) * PAGE_SIZE);
        var records = jdbc.query(sql.toString(), (row, index) -> {
            var type = Kind.valueOf(row.getString("kind"));
            var amount = row.getBigDecimal("amount");
            var reserved = row.getBigDecimal("reserved_amount");
            BigDecimal available = switch (type) {
                case RECHARGE, GRANT, CREDIT, RELEASE, REFUND, ADMIN_CREDIT -> amount;
                case ADMIN_DEBIT, RESERVE -> amount.negate();
                case SETTLE -> reserved == null ? null : reserved.subtract(amount);
                case OTHER -> null;
            };
            String actor = row.getString("actor_id");
            String reference = type == Kind.RECHARGE ? row.getString("event_key").substring("recharge:".length()) : row.getString("task_key");
            return new Entry(row.getString("id"), type, amount.toPlainString(), available == null ? null : available.toPlainString(),
                    actor == null ? null : new Account(actor, row.getString("nickname"), row.getString("phone_suffix"), row.getString("member_role")),
                    reference, row.getTimestamp("created_at").toInstant());
        }, args.toArray());
        var accounts = jdbc.query("""
                SELECT m.user_id, m.member_role, NULLIF(TRIM(p.nickname), '') AS nickname, RIGHT(u.phone, 4) AS phone_suffix
                FROM tenant_member m
                LEFT JOIN sys_user u ON u.id=m.user_id AND u.status<>'DELETED'
                LEFT JOIN user_profile p ON p.user_id=u.id
                WHERE m.tenant_id=?
                ORDER BY CASE WHEN m.member_role='OWNER' THEN 0 ELSE 1 END, m.created_at, m.user_id
                """, (row, index) -> new Account(row.getString("user_id"), row.getString("nickname"),
                row.getString("phone_suffix"), row.getString("member_role")), tenant);
        return new Page(records.stream().limit(PAGE_SIZE).toList(), accounts, current, current < 10000 && records.size() > PAGE_SIZE);
    }
}
