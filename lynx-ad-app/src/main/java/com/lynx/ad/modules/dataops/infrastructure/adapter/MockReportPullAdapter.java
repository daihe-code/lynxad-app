package com.lynx.ad.modules.dataops.infrastructure.adapter;

import com.lynx.ad.modules.dataops.domain.port.ReportPullPort;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "true", matchIfMissing = true)
public class MockReportPullAdapter implements ReportPullPort {

    @Override
    public RawOceanReport pull(AdPlan plan) {
        return new RawOceanReport(100, 10, 100_000, 1, 200_000, new BigDecimal("2.00"), new BigDecimal("0.1"));
    }
}
