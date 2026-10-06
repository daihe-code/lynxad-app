package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.agents.creative.CreativeAgent;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.creative.application.CreativeCommandService;
import com.lynx.ad.modules.creative.domain.model.*;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreativeSubmissionTest {
    @Test
    void savesTenantScopedPendingTaskBeforeDispatchingAsyncGeneration() {
        var repository = mock(CreativeTaskRepository.class);
        var agent = mock(CreativeAgent.class);
        var service = service(repository, agent);
        var tenant = new TenantId("tenant-progress");
        var plan = new PlanId("plan-progress");
        var products = List.of(new QcProductId("selected-product"));

        var id = service.submit(tenant, Optional.of(plan), products, CreativeMode.STANDARD);

        var task = ArgumentCaptor.forClass(CreativeTask.class);
        var order = inOrder(repository, agent);
        order.verify(repository).save(task.capture());
        order.verify(agent).run(id);
        verify(agent, never()).runNow(any());
        assertEquals(id, task.getValue().id());
        assertEquals(tenant, task.getValue().tenantId());
        assertEquals(Optional.of(plan), task.getValue().planId());
        assertEquals(products, task.getValue().productIds());
        assertEquals(TaskStatus.PENDING, task.getValue().status());
    }

    @Test
    void rejectedModeDoesNotPersistOrDispatchWork() {
        var repository = mock(CreativeTaskRepository.class);
        var agent = mock(CreativeAgent.class);
        assertThrows(DomainException.class, () -> service(repository, agent).submit(
                new TenantId("tenant-progress"), Optional.empty(), List.of(), CreativeMode.RESERVED));
        verifyNoInteractions(repository, agent);
    }

    private CreativeCommandService service(CreativeTaskRepository repository, CreativeAgent agent) {
        return new CreativeCommandService(repository, agent, mock(AuditRecorder.class),
                mock(StoragePort.class), new LynxAdProperties());
    }
}
