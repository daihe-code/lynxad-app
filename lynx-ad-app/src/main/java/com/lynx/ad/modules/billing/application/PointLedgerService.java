package com.lynx.ad.modules.billing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PointLedgerService {
    private static final int PAGE_SIZE = 20;
    private static final ZoneId LEDGER_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PointLedgerService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public enum Kind { RECHARGE, GRANT, CREDIT, RESERVE, SETTLE, RELEASE, REFUND, ADMIN_CREDIT, ADMIN_DEBIT, OTHER }

    public record Account(String userId, String name, String phoneSuffix, String role) {}

    public record Entry(String id, Kind kind, String amount, String availableChange, Account account,
                        String reference, Instant createdAt, String advertiserId, List<GenerationCharges.SettledItem> items) {}

    public record Page(List<Entry> records, List<Account> accounts, int page, boolean hasMore, String netConsumed) {}

    public Page history(String tenant, String account, Kind kind, int page) {
        return history(tenant, account, kind, page, null, null);
    }

    public Page history(String tenant, String account, Kind kind, int page, LocalDate from, LocalDate to) {
        if (account != null && (account.isBlank() || account.length() > 36)) {
            throw new BizException(1001, "积分归属账户无效");
        }
        if (from != null && to != null && from.isAfter(to)) throw new BizException(1001, "起始日期不能晚于结束日期");
        int current = Math.clamp(page, 1, 10000);
        var sql = new StringBuilder("""
                WITH ledger AS (
                    SELECT l.id, l.tenant_id, l.event_key, l.created_at, l.amount, r.amount AS reserved_amount,
                        r.task_key, COALESCE(l.actor_user_id, r.actor_user_id) AS actor_id,
                        COALESCE(l.binding_id, r.binding_id) AS binding_id,
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
                SELECT l.*, m.member_role, NULLIF(TRIM(p.nickname), '') AS nickname, RIGHT(u.phone, 4) AS phone_suffix,
                    a.advertiser_id, g.settlement_json
                FROM ledger l
                LEFT JOIN tenant_member m ON m.tenant_id=l.tenant_id AND m.user_id=l.actor_id
                LEFT JOIN sys_user u ON u.id=m.user_id AND u.status<>'DELETED'
                LEFT JOIN user_profile p ON p.user_id=u.id
                LEFT JOIN ad_account_binding b ON b.tenant_id=l.tenant_id AND b.id=l.binding_id
                LEFT JOIN real_ad_account a ON a.id=b.real_account_id
                LEFT JOIN workspace_generation_charge g ON g.tenant_id=l.tenant_id AND g.task_key=l.task_key
                    AND l.kind IN ('SETTLE','REFUND')
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
        dates(sql, args, from, to);
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
                    reference, row.getTimestamp("created_at").toInstant(), row.getString("advertiser_id"),
                    readItems(row.getString("settlement_json")));
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
        String net = account == null ? null : netConsumption(tenant, account, from, to).getOrDefault(account, "0");
        return new Page(records.stream().limit(PAGE_SIZE).toList(), accounts, current,
                current < 10000 && records.size() > PAGE_SIZE, net);
    }

    public Map<String, String> netConsumptionByAccount(String tenant) {
        return netConsumption(tenant, null, null, null);
    }

    private Map<String, String> netConsumption(String tenant, String account, LocalDate from, LocalDate to) {
        var sql = new StringBuilder("""
                SELECT COALESCE(l.actor_user_id, r.actor_user_id) AS actor_id,
                       SUM(CASE WHEN l.event_type='REFUND' THEN -l.amount ELSE l.amount END) AS net
                FROM point_ledger l
                LEFT JOIN point_reservation r ON r.tenant_id=l.tenant_id AND r.id=l.reservation_id
                WHERE l.tenant_id=? AND l.event_type IN ('SETTLE','REFUND')
                """);
        var args = new ArrayList<Object>();
        args.add(tenant);
        if (account != null) {
            sql.append(" AND COALESCE(l.actor_user_id, r.actor_user_id)=?");
            args.add(account);
        }
        dates(sql, args, from, to);
        sql.append(" GROUP BY COALESCE(l.actor_user_id, r.actor_user_id)");
        return jdbc.query(sql.toString(), rows -> {
            var totals = new LinkedHashMap<String, String>();
            while (rows.next()) {
                String actor = rows.getString("actor_id");
                if (actor != null) totals.put(actor, rows.getBigDecimal("net").toPlainString());
            }
            return totals;
        }, args.toArray());
    }

    private static void dates(StringBuilder sql, List<Object> args, LocalDate from, LocalDate to) {
        if (from != null) {
            sql.append(" AND l.created_at>=?");
            args.add(Timestamp.from(from.atStartOfDay(LEDGER_ZONE).toInstant()));
        }
        if (to != null) {
            sql.append(" AND l.created_at<?");
            args.add(Timestamp.from(to.plusDays(1).atStartOfDay(LEDGER_ZONE).toInstant()));
        }
    }

    private List<GenerationCharges.SettledItem> readItems(String value) {
        if (value == null) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<GenerationCharges.SettledItem>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid stored point settlement details", ex);
        }
    }
}
