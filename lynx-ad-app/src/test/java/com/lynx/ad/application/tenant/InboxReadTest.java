package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.domain.model.InboxMessage;
import com.lynx.ad.modules.tenant.domain.port.InboxMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InboxReadTest {
    final InboxMessageRepository repository = mock(InboxMessageRepository.class);
    final InboxCommandService service = new InboxCommandService(repository, mock(AuditRecorder.class));
    final TenantId tenant = new TenantId("notification-owner");

    @Test void readingUsesTenantOwnershipAndIsIdempotent() {
        var message = InboxMessage.post(tenant, "素材已生成", "可查看素材", null);
        when(repository.findById(tenant, message.id())).thenReturn(Optional.of(message));
        service.markRead(tenant, message.id(), new UserId("reader"));
        Instant readAt = message.readAt();
        assertFalse(message.unread());
        service.markRead(tenant, message.id(), new UserId("reader"));
        assertEquals(readAt, message.readAt());
        assertThrows(BizException.class, () -> service.markRead(new TenantId("other"), message.id(), new UserId("reader")));
    }

    @Test void readAllPreservesTheObservedBoundaryAndClampsFutureDates() {
        Instant boundary = Instant.parse("2026-10-01T00:00:00Z");
        service.markAllRead(tenant, boundary);
        verify(repository).markAllRead(tenant, boundary);
        Instant start = Instant.now();
        service.markAllRead(tenant, start.plusSeconds(3600));
        var value = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(2)).markAllRead(eq(tenant), value.capture());
        assertFalse(value.getValue().isBefore(start));
        assertFalse(value.getValue().isAfter(Instant.now()));
    }
    @Test void inboxReadAttemptsOnlyTheCurrentTenantsDueRecovery() {
        var delivery=mock(com.lynx.ad.modules.tenant.application.InboxDeliveryJob.class);service.deliveryJob(delivery);
        service.list(tenant);verify(delivery).recover(tenant);verify(repository).listByTenant(tenant);
    }

    @Test void inboxHidesHistoricalCollectionNoticesAndKeepsActionableMessages() {
        var error = InboxMessage.post(tenant, "盯盘采集异常",
                "RPCError{PSM:[ad.site.lego] ErrType:[RPC_FAILED] reason=request timeout}",
                new BizRef("OPTIMIZATION_ERROR", "plan"));
        var recovered = InboxMessage.post(tenant, "盯盘采集已恢复", "已重新获取最新平台数据。",
                new BizRef("OPTIMIZATION_RECOVERED", "plan"));
        var authorization = InboxMessage.post(tenant, "自动调控授权已恢复", "后续使用最新平台状态重新判断。",
                new BizRef("OPTIMIZATION_RECOVERED", "plan"));
        var advice = InboxMessage.post(tenant, "新的盯盘建议", "持续无曝光，可调整对应素材。",
                new BizRef("OPTIMIZATION_ADVICE", "plan"));
        when(repository.listByTenant(tenant)).thenReturn(List.of(error, recovered, authorization, advice));

        var messages = service.list(tenant);
        assertEquals(List.of("自动调控授权已恢复", "新的盯盘建议"),
                messages.stream().map(message -> message.title()).toList());
    }
}
