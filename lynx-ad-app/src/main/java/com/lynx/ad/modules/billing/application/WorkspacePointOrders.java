package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.billing.api.dto.OrderView;
import com.lynx.ad.modules.billing.domain.model.*;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import com.lynx.ad.modules.tenant.application.WorkspaceAccess;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspacePointOrders {
    private final WorkspacePricing pricing;
    private final WorkspaceAccess access;
    private final WorkspaceFoundation foundation;
    private final OrderRepository orders;
    private final PointWalletService wallet;
    private final JdbcTemplate jdbc;
    private final SubscriptionSupport subscriptions;
    private final BillingTrialService trials;

    public WorkspacePointOrders(WorkspacePricing pricing, WorkspaceAccess access, WorkspaceFoundation foundation,
            OrderRepository orders, PointWalletService wallet, JdbcTemplate jdbc, SubscriptionSupport subscriptions,
            BillingTrialService trials) {
        this.pricing = pricing; this.access = access; this.foundation = foundation; this.orders = orders;
        this.wallet = wallet; this.jdbc = jdbc; this.subscriptions = subscriptions; this.trials = trials;
    }

    public List<WorkspacePricing.RechargeOption> options() {
        access.require(MemberRole.OWNER);
        return pricing.isEnabled() ? pricing.options() : List.of();
    }

    public OrderView status(String id) {
        var owner = access.require(MemberRole.OWNER);
        return orders.findById(new TenantId(owner.tenantId()),id).filter(o -> o.type()==OrderType.POINT_RECHARGE)
                .map(OrderView::from).orElseThrow(() -> new BizException(1604,"订单不存在"));
    }

    @Transactional
    public OrderView create(String code, String key) {
        var owner = access.require(MemberRole.OWNER);
        if (!pricing.isEnabled()) throw new BizException(1904,"积分充值尚未开放");
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}")) throw new BizException(1001,"充值请求标识无效");
        var tenant = new TenantId(owner.tenantId());
        String id = UUID.nameUUIDFromBytes(("point-order:"+tenant.value()+":"+key).getBytes(StandardCharsets.UTF_8)).toString();
        foundation.ensure(tenant.value());
        var existing = orders.findById(tenant,id);
        if (existing.isPresent()) {
            if (!jdbc.queryForList("SELECT sku FROM workspace_point_order WHERE order_id=? AND tenant_id=?",String.class,id,tenant.value()).contains(code))
                throw new BizException(1902,"充值请求已用于其他档位");
            return OrderView.from(existing.get());
        }
        var sku = pricing.options().stream().filter(option -> option.code().equals(code)).findFirst()
                .orElseThrow(() -> new BizException(1001,"充值档位不存在"));
        PlanCode label = subscriptions.effective(tenant,Instant.now()).map(Subscription::planCode).orElse(PlanCode.BASIC);
        var order = new BillingOrder(id,tenant,label,null,OrderType.POINT_RECHARGE,OrderStatus.PENDING_PAY,
                sku.priceFen(),sku.priceFen(),"积分充值 · "+sku.points()+" 积分",Instant.now(),null);
        orders.save(order);
        jdbc.update("INSERT INTO workspace_point_order(order_id,tenant_id,actor_user_id,sku,points) VALUES(?,?,?,?,?)",
                id,tenant.value(),owner.userId(),code,sku.points());
        return OrderView.from(order);
    }

    @Transactional
    public void settle(BillingOrder order) {
        var record = jdbc.queryForMap("SELECT actor_user_id,points FROM workspace_point_order WHERE order_id=? AND tenant_id=?",order.id(),order.tenantId().value());
        wallet.credit(order.tenantId().value(),"recharge:"+order.id(),BigDecimal.valueOf(((Number) record.get("points")).longValue()),
                (String)record.get("actor_user_id"));
    }

    /** Issue only the current entitlement. The period and tier form a stable key across logins and callbacks. */
    @Transactional
    public void ensureCurrentGrant(String tenant) {
        if (!pricing.isEnabled()) return;
        foundation.ensure(tenant);
        var id = new TenantId(tenant);
        Instant now = Instant.now();
        var sub = subscriptions.effective(id,now);
        if (sub.isPresent()) {
            var current = sub.get();
            grant(tenant,"subscription:"+current.periodStart().getEpochSecond(),current.planCode().name(),pricing.points(current.planCode().name()));
        } else {
            var trial = trials.current(id,now);
            if (trial != null && trial.active()) grant(tenant,"trial:"+trial.startedAt().getEpochSecond(),"trial",pricing.points("trial"));
        }
    }

    private void grant(String tenant, String period, String tier, long total) {
        String event = period+":"+tier;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM point_ledger WHERE tenant_id=? AND event_key=?",Integer.class,tenant,event) > 0) return;
        var credited = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM point_ledger WHERE tenant_id=? AND event_type='CREDIT' AND event_key LIKE ?",
                BigDecimal.class,tenant,period+":%");
        BigDecimal difference = BigDecimal.valueOf(total).subtract(credited);
        if (difference.signum() > 0) wallet.credit(tenant,event,difference);
    }
}
