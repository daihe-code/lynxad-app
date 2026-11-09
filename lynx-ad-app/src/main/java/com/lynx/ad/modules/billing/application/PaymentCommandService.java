package com.lynx.ad.modules.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.idempotency.IdempotencyService;
import com.lynx.ad.modules.billing.api.dto.PaymentView;
import com.lynx.ad.modules.billing.domain.model.BillingOrder;
import com.lynx.ad.modules.billing.domain.model.BillingPayment;
import com.lynx.ad.modules.billing.domain.model.OrderStatus;
import com.lynx.ad.modules.billing.domain.model.OrderType;
import com.lynx.ad.modules.billing.domain.model.PaymentStatus;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import com.lynx.ad.modules.billing.domain.port.PaymentPort;
import com.lynx.ad.modules.billing.domain.port.PaymentRepository;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import com.lynx.ad.modules.billing.domain.service.PeriodPolicy;
import com.lynx.ad.modules.billing.infrastructure.adapter.MockPaymentAdapter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCommandService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSupport subscriptionSupport;
    private final PaymentPort paymentPort;
    private final MockPaymentAdapter mockPaymentAdapter;
    private final LynxAdProperties properties;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired private WorkspacePointOrders pointOrders;
    @org.springframework.beans.factory.annotation.Autowired private com.lynx.ad.modules.tenant.application.WorkspaceFoundation foundation;
    @org.springframework.beans.factory.annotation.Autowired private com.lynx.ad.modules.tenant.application.WorkspaceAccess workspaceAccess;

    public PaymentCommandService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionSupport subscriptionSupport,
            PaymentPort paymentPort,
            MockPaymentAdapter mockPaymentAdapter,
            LynxAdProperties properties,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionSupport = subscriptionSupport;
        this.paymentPort = paymentPort;
        this.mockPaymentAdapter = mockPaymentAdapter;
        this.properties = properties;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentView create(TenantId tenantId, String orderId, String idempotencyKey) {
        if (foundation != null) foundation.ensure(tenantId.value());
        BillingOrder order = orderRepository
                .findById(tenantId, orderId)
                .orElseThrow(() -> new BizException(1604, "订单不存在"));
        if (order.type() == OrderType.POINT_RECHARGE)
            workspaceAccess.require(com.lynx.ad.common.security.MemberRole.OWNER);
        if (order.status() != OrderStatus.PENDING_PAY) {
            throw new BizException(1603, "订单状态不允许支付");
        }
        if (properties.getPayment().isLive() || order.type() == OrderType.POINT_RECHARGE) idempotencyKey = "order:"+orderId;
        else if(idempotencyKey != null) idempotencyKey=orderId+":"+idempotencyKey;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> replay =
                    idempotencyService.findReplay(tenantId, new IdempotencyKey("pay:" + idempotencyKey));
            if (replay.isPresent()) {
                try {
                    return objectMapper.readValue(replay.get(), PaymentView.class);
                } catch (Exception ex) {
                    throw new BizException(9999, "幂等回放失败");
                }
            }
        }
        String paymentId = properties.getPayment().isLive() ? order.id().replace("-","") : UUID.randomUUID().toString();
        PaymentPort.PaymentCreateResult created = paymentPort.create(new PaymentPort.PaymentCreateCommand(
                tenantId.value(),
                order.id(),
                paymentId,
                order.payableFen(),
                order.type() == OrderType.POINT_RECHARGE ? "灵投积分充值" : order.planCode().displayName(),
                properties.getPayment().getWechat().getNotifyUrl()));
        Instant now = Instant.now();
        BillingPayment payment = new BillingPayment(
                paymentId,
                tenantId,
                order.id(),
                created.channel(),
                PaymentStatus.CREATED,
                order.payableFen(),
                created.channelTradeNo(),
                created.mockNonce(),
                now,
                null);
        paymentRepository.save(payment);
        PaymentView view = PaymentView.from(payment, created.codeUrl(), created.mockNonce());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyService.saveReplay(
                        tenantId, new IdempotencyKey("pay:" + idempotencyKey), objectMapper.writeValueAsString(view));
            } catch (Exception ex) {
                throw new BizException(9999, "幂等写入失败");
            }
        }
        return view;
    }

    @Transactional
    public PaymentView mockComplete(TenantId tenantId, String paymentId, boolean success, String nonce, Boolean paid) {
        if (paid != null) {
            throw new BizException(1604, "禁止前端声明已支付");
        }
        if (!properties.getPayment().isMock()) {
            throw new BizException(1605, "当前不是演示支付模式");
        }
        if (foundation != null) foundation.ensure(tenantId.value());
        BillingPayment payment = paymentRepository
                .findById(tenantId, paymentId)
                .orElseThrow(() -> new BizException(1604, "支付单不存在"));
        BillingOrder order = orderRepository
                .findById(tenantId, payment.orderId())
                .orElseThrow(() -> new BizException(1604, "订单不存在"));
        String expected = mockPaymentAdapter.expectedNonce(payment.id(), payment.amountFen(), tenantId.value());
        if (nonce == null || !expected.equals(nonce) || !paymentPort.verifyMockComplete(paymentId, nonce)) {
            throw new BizException(1604, "支付确认无效");
        }
        if (payment.amountFen() != order.payableFen()) {
            throw new BizException(1609, "支付金额与订单应付不一致");
        }
        if (order.type() == OrderType.POINT_RECHARGE)
            workspaceAccess.require(com.lynx.ad.common.security.MemberRole.OWNER);
        if (order.status() == OrderStatus.PAID) return PaymentView.from(payment, null, null);
        Instant now = Instant.now();
        if (!success) {
            payment.fail(now);
            paymentRepository.save(payment);
            return PaymentView.from(payment, null, null);
        }
        settleSuccess(order, payment, now);
        return PaymentView.from(payment, null, null);
    }

    @Transactional
    public String wechatNotify(String rawBody, Map<String, String> headers) {
        PaymentPort.PaymentNotify notify = paymentPort.parseAndVerifyNotify(rawBody, headers);
        BillingPayment payment = paymentRepository
                .findByIdOnly(notify.paymentId())
                .orElseThrow(() -> new BizException(1604, "支付单不存在"));
        if (foundation != null) foundation.ensure(payment.tenantId().value());
        BillingOrder order = orderRepository
                .findById(payment.tenantId(), payment.orderId())
                .orElseThrow(() -> new BizException(1604, "订单不存在"));
        if (payment.amountFen() != order.payableFen() || (notify.success() && !java.util.Objects.equals(notify.amountFen(),order.payableFen()))) {
            throw new BizException(1609, "支付金额与订单应付不一致");
        }
        if (notify.success() && order.status() != OrderStatus.PAID) settleSuccess(order, payment, Instant.now(),notify.channelTradeNo());
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    private void settleSuccess(BillingOrder order, BillingPayment payment, Instant now) {
        settleSuccess(order,payment,now,payment.channelTradeNo());
    }

    private void settleSuccess(BillingOrder order, BillingPayment payment, Instant now, String tradeNo) {
        if (order.status() == OrderStatus.PAID) return;
        payment.succeed(tradeNo, now);
        order.markPaid(now);
        paymentRepository.save(payment);
        orderRepository.save(order);
        if (order.type() == OrderType.POINT_RECHARGE) {
            pointOrders.settle(order);
            return;
        }
        PeriodPolicy periods = new PeriodPolicy(ZoneId.of(properties.getPayment().getTimezone()));
        if (order.type() == OrderType.UPGRADE) {
            Subscription sub = subscriptionSupport
                    .effective(order.tenantId(), now)
                    .orElseThrow(() -> new BizException(1604, "订阅不存在"));
            sub.upgradeInPeriod(order.planCode(), order.payableFen(), now);
            subscriptionRepository.save(sub);
            if (pointOrders != null) pointOrders.ensureCurrentGrant(order.tenantId().value());
            return;
        }
        PeriodPolicy.Period period = periods.fromPaidAt(now);
        Optional<Subscription> existing = subscriptionRepository.findByTenant(order.tenantId());
        Subscription sub = existing.orElseGet(() -> new Subscription(
                order.tenantId(),
                order.planCode(),
                com.lynx.ad.modules.billing.domain.model.SubStatus.ACTIVE,
                period.start(),
                period.end(),
                true,
                "WECHAT",
                order.payableFen(),
                null,
                now));
        sub.activateNewPeriod(order.planCode(), period.start(), period.end(), order.payableFen(), now);
        subscriptionRepository.save(sub);
        if (pointOrders != null) pointOrders.ensureCurrentGrant(order.tenantId().value());
    }
}
