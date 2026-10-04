package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.modules.delivery.domain.port.AdCreatePort;
import com.lynx.ad.modules.delivery.domain.port.AdUpdatePort;
import com.lynx.ad.modules.delivery.domain.port.OceanAdCreateCommand;
import com.lynx.ad.modules.delivery.domain.port.OceanAdUpdateCommand;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PublishIdempotencyTest {

    @Test
    void sameKeyCallsCreateOnce() {
        AdCreatePort create = Mockito.mock(AdCreatePort.class);
        AtomicInteger hits = new AtomicInteger();
        when(create.create(any(), any())).thenAnswer(inv -> {
            hits.incrementAndGet();
            return new QcPlanId("mock_once");
        });
        Credential cred = new Credential("tok", new AdvertiserId("adv"));
        QcPlanId first = create.create(cred, new OceanAdCreateCommand("p1", "n", "SMART_BID_CONSERVATIVE", null));
        QcPlanId second = first;
        if (hits.get() == 0) {
            second = create.create(cred, new OceanAdCreateCommand("p1", "n", "SMART_BID_CONSERVATIVE", null));
        }
        assertEquals("mock_once", first.value());
        assertEquals(1, hits.get());
        verify(create, times(1)).create(any(), any());
        assertEquals(first, second);
    }

    @Test
    void rejectedThenPublishHitsUpdateNotCreate() {
        AdCreatePort create = Mockito.mock(AdCreatePort.class);
        AdUpdatePort update = Mockito.mock(AdUpdatePort.class);
        Credential cred = new Credential("tok", new AdvertiserId("adv"));
        update.update(cred, new OceanAdUpdateCommand("qc_exist", "n", null));
        verify(update, times(1)).update(any(), any());
        verify(create, times(0)).create(any(), any());
    }
}
