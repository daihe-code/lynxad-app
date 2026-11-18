package com.lynx.ad.modules.admin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.admin.infrastructure.*;
import com.lynx.ad.modules.billing.application.PaymentCommandService;
import com.lynx.ad.modules.billing.application.WorkspacePointOrders;
import com.lynx.ad.modules.billing.domain.model.OrderStatus;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import com.lynx.ad.modules.billing.domain.model.Trial;
import com.lynx.ad.modules.billing.domain.model.TrialWindow;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.lynx.ad.modules.admin.infrastructure.AdminAccountStore.*;

@Service
public class AdminPackageService {
    public record ExtendRequest(String requestId,String type,String planCode,int days,Instant expectedEnd,String reason) {}
    public enum PackageMode { TRIAL, SUBSCRIPTION }
    public record ChangeRequest(String requestId, String tenantId, String planCode, Integer days,
                                Instant expectedUpdatedAt, String reason, PackageMode mode, Instant expectedTrialEnd) {
        public ChangeRequest(String requestId, String tenantId, String planCode, Integer days,
                             Instant expectedUpdatedAt, String reason) {
            this(requestId, tenantId, planCode, days, expectedUpdatedAt, reason, null, null);
        }
    }
    private record SubscriptionSnapshot(String code,String nextPlan,Instant end) {}
    private record CurrentPackage(String code, String status, Instant end, Instant updatedAt, int paidFen) {
        boolean effectiveAt(Instant now) {
            return ("ACTIVE".equals(status) || "PENDING_DOWNGRADE".equals(status)) && end.isAfter(now);
        }
    }
    private record Receipt(AdminUserQuery.Adjustment adjustment,String admin,String hash) {}
    private final AdminAuthService auth;private final AdminAccountStore admins;private final JdbcTemplate jdbc;private final ObjectMapper json;
    private final WorkspacePointOrders points;
    private final PaymentCommandService payments;
    public AdminPackageService(AdminAuthService auth,AdminAccountStore admins,JdbcTemplate jdbc,ObjectMapper json,
                               WorkspacePointOrders points, PaymentCommandService payments) {
        this.auth=auth;this.admins=admins;this.jdbc=jdbc;this.json=json;this.points=points;this.payments=payments;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AdminUserQuery.Adjustment extend(String user,ExtendRequest request) {
        var admin=auth.require();validate(request);
        var tenant=jdbc.query("SELECT tenant_id FROM tenant_member WHERE user_id=?",(r,n)->r.getString(1),user);
        if(tenant.size()!=1)throw new BizException(1904,"用户尚未创建坤境账户或账户归属不明确，暂不可延期");
        String id=tenant.getFirst();String hash=hash(user,request);
        // Serialize this account's manual grants; repeat submissions are re-read under the lock.
        jdbc.queryForObject("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE",String.class,id);
        var currentMembership=jdbc.query("SELECT tenant_id FROM tenant_member WHERE user_id=? AND status<>'REMOVED' FOR UPDATE",(r,n)->r.getString(1),user);
        if(currentMembership.size()!=1||!id.equals(currentMembership.getFirst()))throw new BizException(409,"用户的坤境账户归属已更新，请刷新后重新操作");
        var existing = receipt(request.requestId(), admin.id(), hash);
        if (existing != null) return existing;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM billing_order WHERE tenant_id=? AND status='PENDING_PAY'",Integer.class,id)>0)throw new BizException(1904,"用户有未完成的套餐支付订单，请处理订单后再延期");
        Instant now=Instant.now().truncatedTo(ChronoUnit.MILLIS),before,after;String code;
        if("TRIAL".equals(request.type())) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM billing_subscription WHERE tenant_id=? AND status IN ('ACTIVE','PENDING_DOWNGRADE') AND period_end>?",Integer.class,id,ts(now))>0)throw new BizException(1904,"当前已有生效套餐，请选择套餐延期");
            var rows=jdbc.query("SELECT expires_at FROM billing_trial WHERE tenant_id=? FOR UPDATE",(r,n)->instant(r,"expires_at"),id);before=rows.isEmpty()?null:rows.getFirst();checkExpected(before,request.expectedEnd());after=end(before,now,request.days());code="PROFESSIONAL";
            if(before==null)jdbc.update("INSERT INTO billing_trial(tenant_id,started_at,expires_at) VALUES(?,?,?)",id,ts(now),ts(after));
            else if(jdbc.update("UPDATE billing_trial SET expires_at=? WHERE tenant_id=? AND expires_at=?",ts(after),id,ts(before))!=1)throw conflict();
        } else {
            var rows=jdbc.query("SELECT * FROM billing_subscription WHERE tenant_id=? FOR UPDATE",(r,n)->new SubscriptionSnapshot(r.getString("plan_code"),r.getString("next_plan_code"),instant(r,"period_end")),id);var sub=rows.isEmpty()?null:rows.getFirst();
            before=sub==null?null:sub.end();checkExpected(before,request.expectedEnd());
            code=request.planCode();if(sub!=null&&!code.equals(sub.code()))throw new BizException(1904,"延期保持当前套餐类型，请重新加载后操作");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM billing_plan WHERE code=?",Integer.class,code)!=1)throw new BizException(1904,"套餐不存在");after=end(before,now,request.days());
            if(sub==null)jdbc.update("INSERT INTO billing_subscription(tenant_id,plan_code,status,period_start,period_end,auto_renew,pay_method,paid_fen_this_period,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",id,code,"ACTIVE",ts(now),ts(after),false,"ADMIN",0,ts(now));
            else {String status=sub.nextPlan()!=null?"PENDING_DOWNGRADE":"ACTIVE";if(jdbc.update("UPDATE billing_subscription SET period_end=?,status=?,updated_at=? WHERE tenant_id=? AND period_end=? AND plan_code=?",ts(after),status,ts(now),id,ts(before),code)!=1)throw conflict();}
        }
        admins.audit(admin,"PACKAGE_EXTEND","SUCCESS",user,id,request.type()+" 延长 "+request.days()+" 天；"+request.reason().trim());
        return recordAdjustment(admin.id(), hash, new AdminUserQuery.Adjustment(request.requestId(),admin.username(),user,id,request.type(),code,request.days(),before,after,request.reason().trim(),now));
    }

    // A checkout may settle while it is being closed. Commit that payment even when the edit needs a fresh snapshot.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED,
            noRollbackFor = AdminPackageService.PackagePaymentSettled.class)
    public AdminUserQuery.Adjustment change(String user, ChangeRequest request) {
        var admin = auth.require();
        validateChange(request);
        String tenant = request.tenantId();
        boolean toTrial = request.mode() == PackageMode.TRIAL;
        String code = toTrial ? PlanCode.PROFESSIONAL.name() : request.planCode();
        String reason = request.reason().trim();
        var fields = new ArrayList<>(List.of("PLAN_CHANGE", user, tenant, code, Objects.toString(request.days(), ""),
                Objects.toString(request.expectedUpdatedAt(), ""), reason));
        if (request.mode() != null) {
            fields.add(request.mode().name());
            fields.add(Objects.toString(request.expectedTrialEnd(), ""));
        }
        String hash = hash(fields);

        if (jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? AND status='ACTIVE' FOR UPDATE",
                String.class, tenant).isEmpty()) throw new BizException(1904, "该坤境账户不可用");
        var membership = jdbc.queryForList("""
                SELECT m.tenant_id FROM tenant_member m JOIN sys_user u ON u.id=m.user_id
                WHERE m.user_id=? AND m.tenant_id=? AND m.status<>'REMOVED' AND u.status<>'DELETED'
                """, String.class, user, tenant);
        if (membership.isEmpty()) throw new BizException(409, "用户的坤境账户归属已更新，请刷新后重试");

        var existing = receipt(request.requestId(), admin.id(), hash);
        if (existing != null) return existing;

        var current = jdbc.query("""
                SELECT plan_code,status,period_end,updated_at,paid_fen_this_period FROM billing_subscription
                WHERE tenant_id=? FOR UPDATE
                """, (r, n) -> new CurrentPackage(r.getString("plan_code"), r.getString("status"),
                instant(r, "period_end"), instant(r, "updated_at"), r.getInt("paid_fen_this_period")), tenant)
                .stream().findFirst().orElse(null);
        Instant trialEnd = jdbc.query("SELECT expires_at FROM billing_trial WHERE tenant_id=? FOR UPDATE",
                (r, n) -> instant(r, "expires_at"), tenant).stream().findFirst().orElse(null);
        if (!Objects.equals(request.expectedUpdatedAt(), current == null ? null : current.updatedAt()))
            throw new BizException(409, "套餐已变更，请重新打开后操作");
        if (request.mode() != null) checkExpected(trialEnd, request.expectedTrialEnd());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM billing_plan WHERE code=?", Integer.class, code) != 1)
            throw new BizException(1904, "套餐不存在");

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        boolean effective = current != null && current.effectiveAt(now);
        boolean onTrial = !effective && trialEnd != null && trialEnd.isAfter(now);
        if (toTrial) {
            if (onTrial) throw new BizException(1904, "当前已是试用中");
            if (effective && current.paidFen() > 0) throw new BizException(1904, "已付款套餐到期后才可设为试用");
        } else {
            if (effective && code.equals(current.code())) throw new BizException(1904, "已是当前套餐");
            if (effective != (request.days() == null)) throw new BizException(409, "套餐有效期已变化，请重新打开后操作");
        }

        closeUnpaidPackageOrders(tenant);
        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (current != null && current.effectiveAt(now) != effective) {
            throw new BizException(409, "套餐有效期已变化，请重新打开后操作");
        }
        onTrial = !effective && trialEnd != null && trialEnd.isAfter(now);
        Instant before = onTrial || current == null ? trialEnd : current.end();
        Instant updatedAt = current != null && !now.isAfter(current.updatedAt()) ? current.updatedAt().plusMillis(1) : now;
        String adjustment = toTrial ? "TRIAL_START" : onTrial && request.mode() != null ? "TRIAL_CONVERT" : "PLAN_CHANGE";
        int days;
        Instant after;
        if (toTrial) {
            var window = TrialWindow.threeDays();
            var trial = Trial.start(new TenantId(tenant), now, window);
            days = window.days();
            after = trial.expiresAt();
            if (effective) {
                jdbc.update("""
                        UPDATE billing_subscription SET status='EXPIRED',period_end=?,auto_renew=FALSE,
                        next_plan_code=NULL,updated_at=? WHERE tenant_id=?
                        """, ts(now), ts(updatedAt), tenant);
            }
            if (trialEnd == null) {
                jdbc.update("INSERT INTO billing_trial(tenant_id,started_at,expires_at) VALUES(?,?,?)",
                        tenant, ts(trial.startedAt()), ts(after));
            } else {
                jdbc.update("UPDATE billing_trial SET started_at=?,expires_at=? WHERE tenant_id=?",
                        ts(trial.startedAt()), ts(after), tenant);
            }
        } else {
            days = effective ? 0 : request.days();
            after = effective ? current.end() : end(null, now, days);
            activatePackage(tenant, code, current, effective, now, after, updatedAt);
            if (trialEnd != null && trialEnd.isAfter(now)) {
                jdbc.update("UPDATE billing_trial SET expires_at=? WHERE tenant_id=?", ts(now), tenant);
            }
            points.ensureCurrentGrant(tenant);
        }
        String previous = onTrial ? "试用中" : current == null ? "未开通" : current.code();
        admins.audit(admin, "PACKAGE_CHANGE", "SUCCESS", user, tenant,
                previous + " → " + (toTrial ? "试用中" : "正式用户 " + code) + "；" + reason);
        return recordAdjustment(admin.id(), hash, new AdminUserQuery.Adjustment(request.requestId(), admin.username(),
                user, tenant, adjustment, code, days, before, after, reason, now));
    }

    private void activatePackage(String tenant, String code, CurrentPackage current, boolean effective,
                                 Instant now, Instant after, Instant updatedAt) {
        if (effective) {
            // Keep the period key and the amount actually paid, including any credit for a later upgrade.
            jdbc.update("""
                    UPDATE billing_subscription SET plan_code=?,status='ACTIVE',auto_renew=FALSE,
                    pay_method='ADMIN',next_plan_code=NULL,updated_at=? WHERE tenant_id=?
                    """, code, ts(updatedAt), tenant);
        } else if (current == null) {
            jdbc.update("""
                    INSERT INTO billing_subscription(tenant_id,plan_code,status,period_start,period_end,auto_renew,pay_method,paid_fen_this_period,updated_at)
                    VALUES(?,?,'ACTIVE',?,?,FALSE,'ADMIN',0,?)
                    """, tenant, code, ts(now), ts(after), ts(now));
        } else {
            jdbc.update("""
                    UPDATE billing_subscription SET plan_code=?,status='ACTIVE',period_start=?,period_end=?,
                    auto_renew=FALSE,pay_method='ADMIN',paid_fen_this_period=0,next_plan_code=NULL,updated_at=? WHERE tenant_id=?
                    """, code, ts(now), ts(after), ts(updatedAt), tenant);
        }
    }

    private void closeUnpaidPackageOrders(String tenant) {
        // Checkout creation and settlement lock the same workspace before reading orders.
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM billing_order o JOIN billing_payment p ON p.order_id=o.id AND p.tenant_id=o.tenant_id
                WHERE o.tenant_id=? AND o.type<>'POINT_RECHARGE' AND o.status='PENDING_PAY' AND p.status<>'FAILED'
                  AND (p.channel NOT IN ('ALIPAY','MOCK') OR p.status<>'CREATED')
                """, Integer.class, tenant) > 0)
            throw new BizException(1904, "该套餐有待核对的支付结果，请核对原支付渠道后再修改");
        var checkoutOrders = jdbc.queryForList("""
                SELECT DISTINCT o.id FROM billing_order o JOIN billing_payment p ON p.order_id=o.id AND p.tenant_id=o.tenant_id
                WHERE o.tenant_id=? AND o.type<>'POINT_RECHARGE' AND o.status='PENDING_PAY'
                  AND p.channel='ALIPAY' AND p.status='CREATED' ORDER BY o.id
                """, String.class, tenant);
        for (String orderId : checkoutOrders) {
            var order = payments.closePackageCheckout(new TenantId(tenant), orderId);
            if (OrderStatus.PAID.name().equals(order.status())) throw new PackagePaymentSettled();
            if (!OrderStatus.CLOSED.name().equals(order.status())) {
                throw new BizException(1904, "支付宝订单尚未关闭，请稍后核对后重试");
            }
        }
        jdbc.update("""
                UPDATE billing_payment SET status='FAILED',completed_at=?
                WHERE tenant_id=? AND channel='MOCK' AND status='CREATED' AND order_id IN (
                    SELECT id FROM billing_order WHERE tenant_id=? AND type<>'POINT_RECHARGE' AND status='PENDING_PAY'
                )
                """, ts(Instant.now()), tenant, tenant);
        jdbc.update("UPDATE billing_order SET status='CLOSED' WHERE tenant_id=? AND type<>'POINT_RECHARGE' AND status='PENDING_PAY'", tenant);
    }

    static final class PackagePaymentSettled extends BizException {
        PackagePaymentSettled() {
            super(409, "套餐订单已付款并生效，请重新打开套餐后修改");
        }
    }

    private AdminUserQuery.Adjustment receipt(String requestId, String adminId, String hash) {
        var existing = jdbc.query("SELECT * FROM platform_package_adjustment WHERE request_id=? FOR UPDATE",
                (r, n) -> new Receipt(AdminUserQuery.ADJUSTMENT.mapRow(r,n), r.getString("admin_id"), r.getString("request_hash")),
                requestId).stream().findFirst().orElse(null);
        if (existing == null) return null;
        if (!adminId.equals(existing.admin()) || !hash.equals(existing.hash()))
            throw new BizException(409, "该请求已用于其他操作，请重新确认");
        return existing.adjustment();
    }

    private AdminUserQuery.Adjustment recordAdjustment(String adminId, String hash, AdminUserQuery.Adjustment row) {
        jdbc.update("""
                INSERT INTO platform_package_adjustment(request_id,request_hash,admin_id,actor_name,user_id,tenant_id,
                grant_type,plan_code,days,before_end,after_end,reason,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, row.requestId(), hash, adminId, row.actorName(), row.userId(), row.tenantId(), row.type(),
                row.planCode(), row.days(), ts(row.beforeEnd()), ts(row.afterEnd()), row.reason(), ts(row.createdAt()));
        return row;
    }

    private static void validateChange(ChangeRequest request) {
        if (request == null || request.requestId() == null || !request.requestId().matches("[a-fA-F0-9-]{36}")
                || request.tenantId() == null || request.tenantId().isBlank() || request.tenantId().length() > 36
                || request.reason() == null || request.reason().trim().length() < 2 || request.reason().length() > 300
                || (request.days() != null && (request.days() < 1 || request.days() > 3650))
                || (request.mode() == PackageMode.TRIAL && (request.planCode() != null || request.days() != null)))
            throw new BizException(1904, "请填写有效的套餐、有效期和2至300字操作原因");
        try {
            UUID.fromString(request.requestId());
            if (request.mode() != PackageMode.TRIAL) PlanCode.valueOf(request.planCode());
        } catch (RuntimeException invalid) {
            throw new BizException(1904, "请求标识或套餐类型不正确");
        }
    }
    private static Instant end(Instant before,Instant now,int days) { return (before!=null&&before.isAfter(now)?before:now).plusSeconds(days*86400L); }
    private static void checkExpected(Instant actual,Instant expected) { if(!Objects.equals(actual,expected))throw conflict(); }
    private static BizException conflict() { return new BizException(409,"套餐到期时间已被更新，请刷新用户资料后重新确认"); }
    private static void validate(ExtendRequest r) {
        if(r==null||r.requestId()==null||!r.requestId().matches("[a-fA-F0-9-]{36}")||!List.of("TRIAL","SUBSCRIPTION").contains(Objects.toString(r.type(),""))||r.days()<1||r.days()>3650||r.reason()==null||r.reason().trim().length()<2||r.reason().length()>300)throw new BizException(1904,"请填写有效请求、1至3650天的延长时长和2至300字操作原因");
        try {UUID.fromString(r.requestId());if("SUBSCRIPTION".equals(r.type()))PlanCode.valueOf(r.planCode());}catch(RuntimeException invalid){throw new BizException(1904,"请求标识或套餐类型不正确");}
    }
    private String hash(String user,ExtendRequest r) {
        return hash(List.of(user,r.type(),Objects.toString(r.planCode(),""),r.days(),Objects.toString(r.expectedEnd(),""),r.reason().trim()));
    }
    private String hash(List<?> fields) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(fields)));
        } catch (Exception error) { throw new IllegalStateException("套餐调整请求无法编码", error); }
    }
}
