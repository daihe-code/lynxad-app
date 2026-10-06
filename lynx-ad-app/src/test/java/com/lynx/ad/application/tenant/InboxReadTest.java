package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import com.lynx.ad.modules.tenant.domain.model.InboxMessage;
import com.lynx.ad.modules.tenant.domain.port.InboxMessageRepository;
import java.time.Instant;
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
}
