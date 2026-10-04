package com.lynx.ad.modules.dataops.domain.port;

import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import java.math.BigDecimal;

public interface ReportPullPort {

    record RawOceanReport(
            long impression,
            long click,
            long costOcean,
            long convert,
            long gmvOcean,
            BigDecimal roi,
            BigDecimal ctr) {}

    RawOceanReport pull(AdPlan plan);
}
