package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.domain.model.PlanAction;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.domain.port.AdCreatePort;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
import com.lynx.ad.modules.delivery.domain.port.AdStatusPort;
import com.lynx.ad.modules.delivery.domain.port.AdUpdatePort;
import com.lynx.ad.modules.delivery.domain.port.BudgetSuggestion;
import com.lynx.ad.modules.delivery.domain.port.OceanAdCreateCommand;
import com.lynx.ad.modules.delivery.domain.port.OceanAdUpdateCommand;
import com.lynx.ad.modules.delivery.domain.port.OceanPlanRow;
import com.lynx.ad.modules.delivery.domain.port.RoiSuggestion;
import com.lynx.ad.modules.delivery.domain.port.SuggestBudgetPort;
import com.lynx.ad.modules.delivery.domain.port.SuggestRoiPort;
import com.lynx.ad.modules.delivery.domain.port.UpdateBidPort;
import com.lynx.ad.modules.delivery.domain.port.UpdateRoiPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "true", matchIfMissing = true)
public class MockDeliveryAdapters
        implements SuggestBudgetPort,
                SuggestRoiPort,
                AdCreatePort,
                AdUpdatePort,
                AdStatusPort,
                AdListPort,
                UpdateBidPort,
                UpdateRoiPort {

    private static final Logger log = LoggerFactory.getLogger(MockDeliveryAdapters.class);
    private final AtomicInteger creates = new AtomicInteger();
    private final AtomicInteger updates = new AtomicInteger();

    @Override
    public List<BudgetSuggestion> suggest(Credential c, AwemeId a, Money budget) {
        return List.of(new BudgetSuggestion(budget == null ? Money.ofYuan(300) : budget, "mock"));
    }

    @Override
    public Optional<RoiSuggestion> suggest(Credential c, AwemeId a) {
        return Optional.of(new RoiSuggestion(new BigDecimal("2.50"), "mock"));
    }

    @Override
    public QcPlanId create(Credential c, OceanAdCreateCommand cmd) {
        creates.incrementAndGet();
        return new QcPlanId("mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
    }

    @Override
    public void update(Credential c, OceanAdUpdateCommand cmd) {
        updates.incrementAndGet();
    }

    @Override
    public void updateStatus(Credential c, QcPlanId id, PlanAction action) {
        // mock no-op
    }

    @Override
    public List<OceanPlanRow> list(Credential c, SmartBidType bidType) {
        return List.of();
    }

    @Override
    public void updateBid(Credential c, QcPlanId id, Money bid) {
        log.info("UpdateBidPort unused in this round");
    }

    @Override
    public void updateRoi(Credential c, QcPlanId id, BigDecimal roi) {
        log.info("UpdateRoiPort unused in this round");
    }

    public int createCalls() {
        return creates.get();
    }

    public int updateCalls() {
        return updates.get();
    }
}
