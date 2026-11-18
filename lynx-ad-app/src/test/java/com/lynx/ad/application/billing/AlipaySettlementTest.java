package com.lynx.ad.application.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.PaymentCommandService;
import com.lynx.ad.modules.billing.application.SubscriptionSupport;
import com.lynx.ad.modules.billing.application.WorkspacePointOrders;
import com.lynx.ad.modules.billing.domain.model.*;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import com.lynx.ad.modules.billing.domain.port.PaymentRepository;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import com.lynx.ad.modules.billing.infrastructure.adapter.AlipayPaymentAdapter;
import com.lynx.ad.modules.billing.infrastructure.adapter.AlipayPaymentAdapter.Status;
import com.lynx.ad.modules.billing.infrastructure.adapter.AlipayPaymentAdapter.Trade;
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
    private final PaymentCommandService service = new PaymentCommandService(orders, payments, subscriptions,
            mock(SubscriptionSupport.class), null, alipay, null, new LynxAdProperties(), null, null);
    private final BillingPayment payment = new BillingPayment(paymentId, tenant, orderId,
            PayChannel.ALIPAY, PaymentStatus.CREATED, 9900, null, null, Instant.now(), null);

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
        when(alipay.query(paymentId)).thenReturn(new Trade(paymentId, Status.NOT_FOUND, null, null));
        assertThrows(BizException.class, () -> service.close(tenant, orderId, true));
        assertEquals(OrderStatus.PENDING_PAY, order.status());

        when(alipay.expired(eq(payment), any())).thenReturn(true);
        assertEquals("CLOSED", service.check(tenant, orderId).status());
        assertEquals(PaymentStatus.FAILED, payment.status());
        verify(alipay, never()).close(paymentId);
        verifyNoInteractions(pointOrders);
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
        return order;
    }
}
