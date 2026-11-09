package com.lynx.ad.application.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.idempotency.IdempotencyService;
import com.lynx.ad.modules.billing.application.OrderCommandService;
import com.lynx.ad.modules.billing.application.SubscriptionSupport;
import com.lynx.ad.modules.billing.domain.model.*;
import com.lynx.ad.modules.billing.domain.port.*;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PendingOrderQueryTest {
    @Test
    void exposesOnlyOwnersPendingOrderWithServerCalculatedAmount() {
        var owner = new TenantId("order-owner");
        var other = new TenantId("other-tenant");
        var repository = mock(OrderRepository.class);
        var order = new BillingOrder("pending-order", owner, PlanCode.PROFESSIONAL, PlanCode.BASIC,
                OrderType.UPGRADE, OrderStatus.PENDING_PAY, 79900, 42000, "升级补差", Instant.now(), null);
        when(repository.findPendingPay(owner)).thenReturn(Optional.of(order));
        when(repository.findPendingPay(other)).thenReturn(Optional.empty());
        var service = new OrderCommandService(mock(PlanRepository.class), repository,
                mock(SubscriptionRepository.class), mock(SubscriptionSupport.class),
                mock(IdempotencyService.class), new ObjectMapper(), mock(WorkspaceFoundation.class));
        var result = service.pending(owner);
        assertEquals("pending-order", result.orderId());
        assertEquals(42000, result.payableFen());
        assertNull(service.pending(other));
        verify(repository).findPendingPay(owner);
        verify(repository).findPendingPay(other);
        verifyNoMoreInteractions(repository);
    }
}
