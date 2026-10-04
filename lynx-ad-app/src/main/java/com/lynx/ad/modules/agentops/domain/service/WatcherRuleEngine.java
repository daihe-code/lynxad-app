package com.lynx.ad.modules.agentops.domain.service;

import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.agentops.domain.model.ActionType;
import com.lynx.ad.modules.agentops.domain.model.WatcherFinding;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WatcherRuleEngine {

    public List<WatcherFinding> evaluate(
            MetricSnapshot current,
            Optional<MetricSnapshot> previous,
            Optional<Money> dailyBudget,
            boolean qcAbnormalPause) {
        List<WatcherFinding> findings = new ArrayList<>();
        findings.add(roiDrop(current, previous));
        findings.add(budgetExhaust(current, dailyBudget));
        findings.add(new WatcherFinding(
                ActionType.QC_ABNORMAL_PAUSE, qcAbnormalPause, qcAbnormalPause ? "千川异常暂停" : "投放状态正常"));
        return findings;
    }

    private WatcherFinding roiDrop(MetricSnapshot current, Optional<MetricSnapshot> previous) {
        if (current == null || previous.isEmpty() || previous.get().roi() == null || current.roi() == null) {
            return new WatcherFinding(ActionType.ROI_DROP, false, "ROI 样本不足");
        }
        BigDecimal r0 = previous.get().roi().value();
        BigDecimal r1 = current.roi().value();
        if (r0.compareTo(BigDecimal.ZERO) <= 0) {
            return new WatcherFinding(ActionType.ROI_DROP, false, "上期 ROI 为 0");
        }
        BigDecimal drop = r0.subtract(r1).divide(r0, 6, RoundingMode.HALF_UP);
        boolean hit = drop.compareTo(new BigDecimal("0.30")) > 0;
        return new WatcherFinding(ActionType.ROI_DROP, hit, hit ? "ROI 环比下降超过 30%" : "ROI 波动在阈值内");
    }

    private WatcherFinding budgetExhaust(MetricSnapshot current, Optional<Money> dailyBudget) {
        if (current == null || dailyBudget.isEmpty() || dailyBudget.get().amount().signum() <= 0) {
            return new WatcherFinding(ActionType.BUDGET_EXHAUST, false, "预算样本不足");
        }
        BigDecimal ratio = current.cost().ratioOf(dailyBudget.get());
        boolean hit = ratio.compareTo(new BigDecimal("0.90")) > 0;
        return new WatcherFinding(ActionType.BUDGET_EXHAUST, hit, hit ? "当日消耗已超预算 90%" : "预算消耗正常");
    }
}
