package com.lynx.ad.modules.dataops.infrastructure.adapter;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.dataops.domain.port.ReportPullPort;
import com.lynx.ad.modules.dataops.domain.service.AmountConversion;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanRemoteStore;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Reuses account report sync instead of scanning the remote account for each plan. */
@Component
@Primary
@ConditionalOnProperty(prefix="lynx.ad.mock",name="qianchuan",havingValue="false")
public class LiveReportPullAdapter implements ReportPullPort {
    private final PlanRemoteStore store;
    public LiveReportPullAdapter(PlanRemoteStore store) { this.store=store; }
    @Override public RawOceanReport pull(AdPlan plan) {
        var remote=store.list(plan.tenantId()).get(plan.id().value());
        if(remote==null || !LocalDate.now(ZoneId.of("Asia/Shanghai")).equals(remote.reportDate()) || remote.reportTs()==null
                || remote.reportTs().isBefore(Instant.now().minusSeconds(600)) || remote.todayReport()==null)
            throw new BizException(1406,"当前计划统计未同步，保留历史快照");
        var report=remote.todayReport();
        var cost=QianchuanPlanMapping.number(report,"stat_cost");var gmv=QianchuanPlanMapping.number(report,"total_pay_order_gmv_include_coupon_for_roi2");
        var orders=QianchuanPlanMapping.number(report,"total_pay_order_count_for_roi2");var roi=QianchuanPlanMapping.number(report,"total_prepay_and_pay_order_roi2");
        var show=QianchuanPlanMapping.number(report,"show_cnt");var click=QianchuanPlanMapping.number(report,"click_cnt");
        if(cost==null || gmv==null || orders==null || roi==null || show==null || click==null)
            throw new BizException(1406,"当前计划统计不完整，不能生成盯盘快照");
        // This report API returns yuan; the legacy port uses units of 1/100000 yuan.
        BigDecimal units=BigDecimal.valueOf(AmountConversion.STAT_COST_DIVISOR);
        BigDecimal ctr=show.signum()==0?BigDecimal.ZERO:click.divide(show,6,java.math.RoundingMode.HALF_UP);
        return new RawOceanReport(show.longValueExact(),click.longValueExact(),cost.multiply(units).longValueExact(),orders.longValueExact(),gmv.multiply(units).longValueExact(),roi,ctr);
    }
}
