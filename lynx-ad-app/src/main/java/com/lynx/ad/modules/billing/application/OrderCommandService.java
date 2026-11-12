package com.lynx.ad.modules.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.idempotency.IdempotencyService;
import com.lynx.ad.modules.billing.api.dto.OrderView;
import com.lynx.ad.modules.billing.domain.model.BillingOrder;
import com.lynx.ad.modules.billing.domain.model.BillingPlan;
import com.lynx.ad.modules.billing.domain.model.OrderStatus;
import com.lynx.ad.modules.billing.domain.model.OrderType;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import com.lynx.ad.modules.billing.domain.port.PlanRepository;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import com.lynx.ad.modules.billing.domain.service.PricePolicy;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {

    private final PlanRepository planRepository;
    private final OrderRepository orderRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSupport subscriptionSupport;
    private final PricePolicy pricePolicy = new PricePolicy();
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final WorkspaceFoundation workspace;

    public OrderCommandService(
            PlanRepository planRepository,
            OrderRepository orderRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionSupport subscriptionSupport,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            WorkspaceFoundation workspace) {
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionSupport = subscriptionSupport;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.workspace = workspace;
    }

    @Transactional
    public OrderView create(TenantId tenantId, String planCodeRaw, String idempotencyKey) {
        workspace.ensure(tenantId.value());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> replay =
                    idempotencyService.findReplay(tenantId, new IdempotencyKey(idempotencyKey));
            if (replay.isPresent()) {
                try {
                    return objectMapper.readValue(replay.get(), OrderView.class);
                } catch (Exception ex) {
                    throw new BizException(9999, "幂等回放失败");
                }
            }
        }
        PlanCode planCode;
        try {
            planCode = PlanCode.parse(planCodeRaw);
        } catch (Exception ex) {
            throw new BizException(1601, "套餐不存在");
        }
        BillingPlan plan = planRepository.findByCode(planCode).orElseThrow(() -> new BizException(1601, "套餐不存在"));
        Instant now = Instant.now();
        Optional<Subscription> effective = subscriptionSupport.effective(tenantId, now);
        if (effective.isEmpty() || effective.get().paidFenThisPeriod() == 0) {
            orderRepository.findPendingPay(tenantId).ifPresent(o -> {
                throw new BizException(1602, "已有待支付订单");
            });
            boolean hadPaid = orderRepository.hasPaidPlan(tenantId, planCode);
            int payable = pricePolicy.firstMonthPayableFen(plan.listPriceFen(), hadPaid);
            String label = pricePolicy.firstMonthLabel(plan.listPriceFen(), payable);
            BillingOrder order = new BillingOrder(
                    UUID.randomUUID().toString(),
                    tenantId,
                    planCode,
                    null,
                    OrderType.FIRST_SUBSCRIBE,
                    OrderStatus.PENDING_PAY,
                    plan.listPriceFen(),
                    payable,
                    label,
                    now,
                    null);
            orderRepository.save(order);
            return remember(tenantId, idempotencyKey, OrderView.from(order));
        }
        Subscription sub = effective.get();
        if (planCode == sub.planCode()) {
            throw new BizException(1001, "已是当前套餐");
        }
        if (planCode.higherThan(sub.planCode())) {
            orderRepository.findPendingPay(tenantId).ifPresent(o -> {
                throw new BizException(1602, "已有待支付订单");
            });
            boolean hadPaidTarget = orderRepository.hasPaidPlan(tenantId, planCode);
            int targetFirst = pricePolicy.firstMonthPayableFen(plan.listPriceFen(), hadPaidTarget);
            int payable = pricePolicy.upgradePayableFen(targetFirst, sub.paidFenThisPeriod());
            String label = pricePolicy.firstMonthLabel(plan.listPriceFen(), targetFirst);
            BillingOrder order = new BillingOrder(
                    UUID.randomUUID().toString(),
                    tenantId,
                    planCode,
                    sub.planCode(),
                    OrderType.UPGRADE,
                    OrderStatus.PENDING_PAY,
                    plan.listPriceFen(),
                    payable,
                    label,
                    now,
                    null);
            orderRepository.save(order);
            return remember(tenantId, idempotencyKey, OrderView.from(order));
        }
        if (planCode.lowerThan(sub.planCode())) {
            sub.scheduleDowngrade(planCode, now);
            subscriptionRepository.save(sub);
            BillingOrder order = new BillingOrder(
                    UUID.randomUUID().toString(),
                    tenantId,
                    planCode,
                    sub.planCode(),
                    OrderType.DOWNGRADE,
                    OrderStatus.PAID,
                    plan.listPriceFen(),
                    0,
                    null,
                    now,
                    now);
            return remember(tenantId, idempotencyKey, OrderView.from(order));
        }
        throw new BizException(1607, "升级目标不高于当前档");
    }

    @Transactional
    public OrderView close(TenantId tenantId, String orderId) {
        workspace.ensure(tenantId.value());
        BillingOrder order = orderRepository
                .findById(tenantId, orderId)
                .orElseThrow(() -> new BizException(1604, "订单不存在"));
        if (order.type() == OrderType.POINT_RECHARGE) throw new BizException(1603,"积分充值单不支持通过套餐订单关闭");
        if (order.status() != OrderStatus.PENDING_PAY) {
            throw new BizException(1603, "订单状态不允许关闭");
        }
        order.close();
        orderRepository.save(order);
        return OrderView.from(order);
    }

    public OrderView pending(TenantId tenantId) {
        return orderRepository.findPendingPay(tenantId).map(OrderView::from).orElse(null);
    }

    private OrderView remember(TenantId tenantId, String idempotencyKey, OrderView view) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyService.saveReplay(
                        tenantId, new IdempotencyKey(idempotencyKey), objectMapper.writeValueAsString(view));
            } catch (Exception ex) {
                throw new BizException(9999, "幂等写入失败");
            }
        }
        return view;
    }
}
