package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.domain.port.UpdateBidPort;
import com.lynx.ad.modules.delivery.domain.port.UpdateRoiPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LiveSuggestNoBidTest {

    @Test
    void suggestDoesNotCallUpdateBidOrRoi() {
        UpdateBidPort bid = Mockito.mock(UpdateBidPort.class);
        UpdateRoiPort roi = Mockito.mock(UpdateRoiPort.class);
        Credential c = new Credential("tok", new AdvertiserId("adv"));
        Money budget = Money.ofYuan(300);
        assertEquals(300, budget.amount().intValue());
        verify(bid, never()).updateBid(c, new QcPlanId("x"), budget);
        verify(roi, never()).updateRoi(c, new QcPlanId("x"), new BigDecimal("2.50"));
        assertEquals("adv", c.advertiserId().value());
        assertEquals("aweme", new AwemeId("aweme").value());
    }
}
