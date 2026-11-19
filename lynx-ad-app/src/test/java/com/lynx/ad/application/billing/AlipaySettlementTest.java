package com.lynx.ad.application.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.billing.application.PaymentCommandService;
import com.lynx.ad.modules.billing.application.SubscriptionSupport;
import com.lynx.ad.modules.billing.application.WorkspacePointOrders;
import com.lynx.ad.modules.billing.application.WorkspacePricing;
import com.lynx.ad.modules.billing.domain.model.*;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import com.lynx.ad.modules.billing.domain.port.PaymentRepository;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import com.lynx.ad.modules.billing.infrastructure.adapter.AlipayPaymentAdapter;
import com.lynx.ad.modules.billing.infrastructure.adapter.AlipayPaymentAdapter.Status;
import com.lynx.ad.modules.billing.infrastructure.adapter.AlipayPaymentAdapter.Trade;
import com.lynx.ad.modules.tenant.application.WorkspaceAccess;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AlipaySettlementTest {
    private final TenantId tenant = new TenantId("workspace");
    private final String orderId = "01234567-89ab-cdef-0123-456789abcdef";
    private final String paymentId = "ali_0123456789abcdef0123456789abcdef";
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final WorkspacePointOrders pointOrders = mock(WorkspacePointOrders.class);
    private final AlipayPaymentAdapter alipay = mock(AlipayPaymentAdapter.class);
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);
    private final PaymentCommandService service = new PaymentCommandService(orders, payments, subscriptions,
            mock(SubscriptionSupport.class), null, alipay, null, new LynxAdProperties(), null, null);
    private final BillingPayment payment = new BillingPayment(paymentId, tenant, orderId,
            PayChannel.ALIPAY, PaymentStatus.CREATED, 9900, null, null, Instant.now(), null);

    @Test
    void adminGrantedSubscriptionAllowsAlipayRechargeUntilItExpires() {
        var order = prepare(OrderType.POINT_RECHARGE);
        when(orders.findById(tenant, orderId)).thenReturn(Optional.of(order));
        when(payments.findById(tenant, paymentId)).thenReturn(Optional.empty());
        var pricing = new WorkspacePricing();
        pricing.setEnabled(true);
        var subscriptionSupport = new SubscriptionSupport(subscriptions);
        var recharge = new WorkspacePointOrders(pricing, access, null, orders, null, null, subscriptionSupport);
        var properties = new LynxAdProperties();
        properties.getPayment().setMode("alipay");
        var checkout = new PaymentCommandService(orders, payments, subscriptions, subscriptionSupport, null,
                alipay, null, properties, null, null);
        ReflectionTestUtils.setField(checkout, "pointOrders", recharge);
        ReflectionTestUtils.setField(checkout, "workspaceAccess", access);
        Instant now = Instant.now();
        var adminPlan = new Subscription(tenant, PlanCode.BASIC, SubStatus.ACTIVE, now.minusSeconds(60),
                now.plusSeconds(3600), false, "ADMIN", 0, null, now);
        when(subscriptions.findByTenant(tenant)).thenReturn(Optional.of(adminPlan));
        String url = "https://openapi.alipay.com/gateway.do?test=recharge";
        when(alipay.checkoutUrl(any(), eq("坤境智营积分充值"), eq(true))).thenReturn(url);

        assertEquals(url, checkout.create(tenant, orderId, null).codeUrl());
        verify(payments).save(argThat(payment -> payment.channel() == PayChannel.ALIPAY && payment.amountFen() == 9900));

        adminPlan.expire(now);
        assertEquals(1904, assertThrows(BizException.class, () -> checkout.create(tenant, orderId, null)).code());
        verify(alipay).checkoutUrl(any(), eq("坤境智营积分充值"), eq(true));
    }

    @Test
    void validQuerySettlesRechargeOnceEvenWhenCallbackArrivesLater() {
        var order = prepare(OrderType.POINT_RECHARGE);
        when(alipay.notification(anyMap())).thenReturn(new Trade(paymentId, Status.PAID, "trade", 9899));
        assertThrows(BizException.class, () -> service.alipayNotify(Map.of()));
        assertEquals(OrderStatus.PENDING_PAY, order.status());
        verifyNoInteractions(pointOrders);

        var paid = new Trade(paymentId, Status.PAID, "trade", 9900);
        when(alipay.query(paymentId)).thenReturn(paid);
        when(alipay.notification(anyMap())).thenReturn(paid);
        assertEquals("PAID", service.check(tenant, orderId).status());
        service.alipayNotify(Map.of());
        service.alipayNotify(Map.of());
        verify(pointOrders).settle(order);
        verify(payments).save(payment);
        assertEquals("trade", payment.channelTradeNo());
    }

    @Test
    void cancelPreservesPaymentThatSettlesDuringTheCloseRequest() {
        prepare(OrderType.FIRST_SUBSCRIBE);
        when(alipay.query(paymentId)).thenReturn(new Trade(paymentId, Status.WAITING, null, null),
                new Trade(paymentId, Status.PAID, "trade", 9900));
        when(alipay.close(paymentId)).thenReturn(false);

        assertEquals("PAID", service.close(tenant, orderId, false).status());
        verify(subscriptions).save(argThat(subscription -> "ALIPAY".equals(subscription.payMethod())
                && !subscription.autoRenew() && subscription.paidFenThisPeriod() == 9900));
        verify(pointOrders).ensureCurrentGrant(tenant.value());
    }

    @Test
    void unacceptedCheckoutCanOnlyCloseAfterItsSignedExpiry() {
        var order = prepare(OrderType.POINT_RECHARGE);
        Instant expiry = Instant.parse("2026-11-19T09:39:55Z");
        when(alipay.checkoutExpiresAt(payment)).thenReturn(expiry);
        when(alipay.query(paymentId)).thenReturn(new Trade(paymentId, Status.NOT_FOUND, null, null));
        var failure = assertThrows(BizException.class, () -> service.close(tenant, orderId, true));
        assertTrue(failure.getMessage().contains("11月19日 17:39:55"));
        assertEquals(OrderStatus.PENDING_PAY, order.status());
        var pending = service.check(tenant, orderId);
        assertEquals("PENDING_PAY", pending.status());
        assertEquals(expiry, pending.paymentExpiresAt());

        when(alipay.expired(eq(payment), any())).thenReturn(true);
        var closed = service.check(tenant, orderId);
        assertEquals("CLOSED", closed.status());
        assertNull(closed.paymentExpiresAt());
        assertEquals(PaymentStatus.FAILED, payment.status());
        verify(alipay, never()).close(paymentId);
        verifyNoInteractions(pointOrders);
    }

    @Test
    void rechargeQueriesAndCancellationRequireWorkspaceOwner() {
        prepare(OrderType.POINT_RECHARGE);
        when(access.require(MemberRole.OWNER)).thenThrow(new BizException(403, "当前成员没有此操作权限"));
        assertEquals(403, assertThrows(BizException.class, () -> service.check(tenant, orderId)).code());
        assertEquals(403, assertThrows(BizException.class, () -> service.close(tenant, orderId, true)).code());
        doReturn(new WorkspaceAccess.Membership("other", "another", "owner", MemberRole.OWNER, 1))
                .when(access).require(MemberRole.OWNER);
        assertEquals(1604, assertThrows(BizException.class, () -> service.check(tenant, orderId)).code());
        verifyNoInteractions(alipay, pointOrders);
    }

    @Test
    void packageChangeClosesAlipayCheckoutAndRequiresALinkedPayment() {
        var order = prepare(OrderType.FIRST_SUBSCRIBE);
        when(payments.findById(tenant, paymentId)).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.closePackageCheckout(tenant, orderId));
        assertEquals(OrderStatus.PENDING_PAY, order.status());

        when(payments.findById(tenant, paymentId)).thenReturn(Optional.of(payment));
        when(alipay.query(paymentId)).thenReturn(new Trade(paymentId, Status.WAITING, null, null));
        when(alipay.close(paymentId)).thenReturn(true);
        assertEquals("CLOSED", service.closePackageCheckout(tenant, orderId).status());
        assertEquals(PaymentStatus.FAILED, payment.status());
        verifyNoInteractions(subscriptions, pointOrders);
    }

    private BillingOrder prepare(OrderType type) {
        var order = new BillingOrder(orderId, tenant, PlanCode.BASIC, null, type, OrderStatus.PENDING_PAY,
                9900, 9900, null, Instant.now(), null);
        when(orders.findByIdForUpdate(tenant, orderId)).thenReturn(Optional.of(order));
        when(payments.findById(tenant, paymentId)).thenReturn(Optional.of(payment));
        when(payments.findByIdOnly(paymentId)).thenReturn(Optional.of(payment));
        ReflectionTestUtils.setField(service, "pointOrders", pointOrders);
        ReflectionTestUtils.setField(service, "workspaceAccess", access);
        when(access.require(MemberRole.OWNER)).thenReturn(new WorkspaceAccess.Membership("member", tenant.value(), "owner", MemberRole.OWNER, 1));
        return order;
    }
}
