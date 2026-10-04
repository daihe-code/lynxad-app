package com.lynx.ad.modules.insight.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.insight.api.InsightReportController.ReportRowVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InsightReportQueryService {

    private final MetricSnapshotRepository snapshotRepository;
    private final AdPlanRepository planRepository;
    private final OauthAccountRepository oauthAccountRepository;

    public InsightReportQueryService(
            MetricSnapshotRepository snapshotRepository,
            AdPlanRepository planRepository,
            OauthAccountRepository oauthAccountRepository) {
        this.snapshotRepository = snapshotRepository;
        this.planRepository = planRepository;
        this.oauthAccountRepository = oauthAccountRepository;
    }

    public List<ReportRowVo> query(TenantId tenantId, String dim, List<String> adIds) {
        String normalized = dim == null || dim.isBlank() ? "plan" : dim.toLowerCase();
        if (!normalized.equals("plan") && !normalized.equals("account")) {
            throw new BizException(400, "不支持的维度");
        }
        List<MetricSnapshot> snapshots = snapshotRepository.listLatestByTenant(tenantId);
        if (adIds != null && !adIds.isEmpty()) {
            Set<String> allow = adIds.stream().filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
            snapshots = snapshots.stream()
                    .filter(s -> s.planId() != null && allow.contains(s.planId().value()))
                    .toList();
        }
        Map<String, AdPlan> plans = planRepository.findByTenant(tenantId).stream()
                .collect(Collectors.toMap(p -> p.id().value(), p -> p, (a, b) -> a));
        if (normalized.equals("plan")) {
            List<ReportRowVo> rows = new ArrayList<>();
            for (MetricSnapshot snap : snapshots) {
                if (snap.planId() == null) {
                    continue;
                }
                AdPlan plan = plans.get(snap.planId().value());
                String name = plan == null ? snap.planId().value() : plan.name().text();
                rows.add(toRow(snap.planId().value(), name, snap));
            }
            return rows;
        }
        Map<String, List<MetricSnapshot>> grouped = new LinkedHashMap<>();
        Map<String, String> names = oauthAccountRepository.findByTenant(tenantId).stream()
                .collect(Collectors.toMap(a -> a.id().value(), OauthAccount::accountName, (a, b) -> a));
        for (MetricSnapshot snap : snapshots) {
            if (snap.planId() == null) {
                continue;
            }
            AdPlan plan = plans.get(snap.planId().value());
            if (plan == null || plan.accountId() == null) {
                continue;
            }
            String accountKey = plan.accountId().value();
            grouped.computeIfAbsent(accountKey, k -> new ArrayList<>()).add(snap);
        }
        List<ReportRowVo> rows = new ArrayList<>();
        for (Map.Entry<String, List<MetricSnapshot>> e : grouped.entrySet()) {
            rows.add(aggregate(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()), e.getValue()));
        }
        return rows;
    }

    private static ReportRowVo toRow(String id, String name, MetricSnapshot snap) {
        return new ReportRowVo(
                id,
                name,
                snap.cost().amount(),
                snap.gmv().amount(),
                snap.roi() == null ? BigDecimal.ZERO : snap.roi().value(),
                snap.impression(),
                snap.click(),
                snap.convert(),
                snap.ts());
    }

    private static ReportRowVo aggregate(String id, String name, List<MetricSnapshot> snaps) {
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal gmv = BigDecimal.ZERO;
        long impression = 0;
        long click = 0;
        long convert = 0;
        Instant ts = null;
        for (MetricSnapshot snap : snaps) {
            cost = cost.add(snap.cost().amount());
            gmv = gmv.add(snap.gmv().amount());
            impression += snap.impression();
            click += snap.click();
            convert += snap.convert();
            if (ts == null || snap.ts().isAfter(ts)) {
                ts = snap.ts();
            }
        }
        BigDecimal roi = cost.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gmv.divide(cost, 4, RoundingMode.HALF_UP);
        return new ReportRowVo(id, name, cost, gmv, roi, impression, click, convert, ts);
    }
}
