package com.lynx.ad.modules.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.idempotency.IdempotencyService;
import com.lynx.ad.modules.billing.api.dto.OrderView;
import com.lynx.ad.modules.billing.api.dto.PaymentView;
import com.lynx.ad.modules.billing.domain.model.BillingOrder;
import com.lynx.ad.modules.billing.domain.model.BillingPayment;
import com.lynx.ad.modules.billing.domain.model.OrderStatus;
import com.lynx.ad.modules.billing.domain.model.OrderType;
import com.lynx.ad.modules.billing.domain.model.PayChannel;
import com.lynx.ad.modules.billing.domain.model.PaymentStatus;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import com.lynx.ad.modules.billing.domain.port.PaymentPort;
import com.lynx.ad.modules.billing.domain.port.PaymentRepository;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import com.lynx.ad.modules.billing.domain.service.PeriodPolicy;
import com.lynx.ad.modules.billing.infrastructure.adapter.AlipayPaymentAdapter;
import com.lynx.ad.modules.billing.infrastructure.adapter.MockPaymentAdapter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
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
    private final AlipayPaymentAdapter alipay;
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
            AlipayPaymentAdapter alipay,
            MockPaymentAdapter mockPaymentAdapter,
            LynxAdProperties properties,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionSupport = subscriptionSupport;
        this.paymentPort = paymentPort;
        this.alipay = alipay;
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
        if (order.type() == OrderType.POINT_RECHARGE) {
            workspaceAccess.require(com.lynx.ad.common.security.MemberRole.OWNER);
            pointOrders.requireRechargeSubscription(tenantId);
        }
        if (order.status() != OrderStatus.PENDING_PAY) {
            throw new BizException(1603, "订单状态不允许支付");
        }
        Optional<BillingPayment> existingAlipay = alipayPayment(order);
        if (existingAlipay.isPresent() || properties.getPayment().isAlipay()) {
            return prepareAlipay(order, existingAlipay.orElse(null));
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
                order.type() == OrderType.POINT_RECHARGE ? "坤境积分充值" : order.planCode().displayName(),
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

    private PaymentView prepareAlipay(BillingOrder order, BillingPayment payment) {
        if (payment == null) {
            if (paymentRepository.findById(order.tenantId(), order.id().replace("-", "")).isPresent()) {
                throw new BizException(1603, "请先核实原支付渠道的订单状态");
            }
            payment = new BillingPayment(alipayPaymentId(order.id()), order.tenantId(), order.id(),
                    PayChannel.ALIPAY, PaymentStatus.CREATED, order.payableFen(), null, null, Instant.now(), null);
            paymentRepository.save(payment);
        }
        boolean recharge = order.type() == OrderType.POINT_RECHARGE;
        String title = recharge ? "坤境智营积分充值" : "坤境智营 " + order.planCode().displayName();
        String url = alipay.expired(payment, Instant.now()) ? null : alipay.checkoutUrl(payment, title, recharge);
        return PaymentView.from(payment, url, null);
    }

    @Transactional
    public OrderView check(TenantId tenantId, String orderId) {
        lockWorkspace(tenantId);
        BillingOrder order = orderRepository.findByIdForUpdate(tenantId, orderId)
                .orElseThrow(() -> new BizException(1604, "订单不存在"));
        if (order.status() == OrderStatus.PENDING_PAY) {
            alipayPayment(order).ifPresent(payment -> refreshAlipay(order, payment, false));
        }
        return OrderView.from(order);
    }

    @Transactional
    public OrderView close(TenantId tenantId, String orderId, boolean recharge) {
        lockWorkspace(tenantId);
        BillingOrder order = orderRepository.findByIdForUpdate(tenantId, orderId)
                .orElseThrow(() -> new BizException(1604, "订单不存在"));
        if (recharge != (order.type() == OrderType.POINT_RECHARGE)) {
            throw new BizException(1604, "订单不存在");
        }
        if (order.status() != OrderStatus.PENDING_PAY) return OrderView.from(order);
        var payment = alipayPayment(order);
        if (payment.isPresent()) {
            refreshAlipay(order, payment.get(), true);
        } else {
            if (properties.getPayment().isAlipay()
                    && paymentRepository.findById(tenantId, order.id().replace("-", "")).isPresent()) {
                throw new BizException(1603, "请先核实原支付渠道的订单状态");
            }
            order.close();
            orderRepository.save(order);
        }
        return OrderView.from(order);
    }

    @Transactional
    public OrderView closePackageCheckout(TenantId tenantId, String orderId) {
        lockWorkspace(tenantId);
        var order = orderRepository.findByIdForUpdate(tenantId, orderId)
                .filter(o -> o.type() != OrderType.POINT_RECHARGE)
                .orElseThrow(() -> new BizException(1604, "套餐订单不存在"));
        if (order.status() == OrderStatus.PENDING_PAY) {
            var payment = alipayPayment(order)
                    .orElseThrow(() -> new BizException(1610, "支付宝支付单无法核对，请稍后重试"));
            refreshAlipay(order, payment, true);
        }
        return OrderView.from(order);
    }

    private void refreshAlipay(BillingOrder order, BillingPayment payment, boolean closeRequested) {
        var trade = alipay.query(payment.id());
        if (trade.status() == AlipayPaymentAdapter.Status.NOT_FOUND && alipay.expired(payment, Instant.now())) {
            closeAlipay(order, payment);
            return;
        }
        applyAlipayTrade(order, payment, trade);
        if (!closeRequested || order.status() != OrderStatus.PENDING_PAY) return;
        if (trade.status() == AlipayPaymentAdapter.Status.NOT_FOUND) {
            // A browser may still open an issued checkout URL before the signed expiry.
            throw new BizException(1603, "支付宝订单尚未受理。可打开收银台后取消订单，或等支付链接过期");
        }
        if (alipay.close(payment.id())) {
            closeAlipay(order, payment);
            return;
        }
        // The customer may have paid between the initial query and the close request.
        applyAlipayTrade(order, payment, alipay.query(payment.id()));
        if (order.status() == OrderStatus.PENDING_PAY) throw new BizException(1610, "暂时无法取消支付宝订单，请稍后查询");
    }

    @Transactional
    public void alipayNotify(Map<String, String> fields) {
        var trade = alipay.notification(fields);
        BillingPayment payment = paymentRepository.findByIdOnly(trade.paymentId())
                .filter(candidate -> candidate.channel() == PayChannel.ALIPAY)
                .orElseThrow(() -> new BizException(1604, "支付单不存在"));
        lockWorkspace(payment.tenantId());
        BillingOrder order = orderRepository.findByIdForUpdate(payment.tenantId(), payment.orderId())
                .orElseThrow(() -> new BizException(1604, "订单不存在"));
        // The locking read sees the committed order even if a concurrent query settled it first.
        applyAlipayTrade(order, payment, trade);
    }

    private void applyAlipayTrade(BillingOrder order, BillingPayment payment, AlipayPaymentAdapter.Trade trade) {
        if (!payment.id().equals(trade.paymentId()) || payment.amountFen() != order.payableFen()) {
            throw new BizException(1609, "支付单与订单不一致");
        }
        if (trade.status() == AlipayPaymentAdapter.Status.PAID) {
            if (!Objects.equals(trade.amountFen(), order.payableFen())) {
                throw new BizException(1609, "支付金额与订单应付不一致");
            }
            if (order.status() == OrderStatus.CLOSED) throw new BizException(1603, "订单已关闭，请核对支付宝账单");
            settleSuccess(order, payment, Instant.now(), trade.tradeNo());
        } else if (trade.status() == AlipayPaymentAdapter.Status.CLOSED) {
            closeAlipay(order, payment);
        }
    }

    private void closeAlipay(BillingOrder order, BillingPayment payment) {
        if (order.status() != OrderStatus.PENDING_PAY) return;
        payment.fail(Instant.now());
        order.close();
        paymentRepository.save(payment);
        orderRepository.save(order);
    }

    private Optional<BillingPayment> alipayPayment(BillingOrder order) {
        return paymentRepository.findById(order.tenantId(), alipayPaymentId(order.id()))
                .filter(payment -> payment.channel() == PayChannel.ALIPAY && payment.orderId().equals(order.id()));
    }

    private static String alipayPaymentId(String orderId) {
        return "ali_" + orderId.replace("-", "");
    }

    private void lockWorkspace(TenantId tenantId) {
        if (foundation != null) foundation.ensure(tenantId.value());
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
                .filter(candidate -> candidate.channel() == PayChannel.MOCK)
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
        if (order.type() == OrderType.POINT_RECHARGE) pointOrders.requireRechargeSubscription(tenantId);
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
                .filter(candidate -> candidate.channel() == PayChannel.WECHAT)
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
        if (order.status() != OrderStatus.PENDING_PAY) {
            throw new BizException(1603, "订单已关闭，请核对支付结果");
        }
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
            sub.recordPaymentMethod(payment.channel());
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
                payment.channel().name(),
                order.payableFen(),
                null,
                now));
        sub.activateNewPeriod(order.planCode(), period.start(), period.end(), order.payableFen(), now);
        sub.recordPaymentMethod(payment.channel());
        subscriptionRepository.save(sub);
        if (pointOrders != null) pointOrders.ensureCurrentGrant(order.tenantId().value());
    }
}
