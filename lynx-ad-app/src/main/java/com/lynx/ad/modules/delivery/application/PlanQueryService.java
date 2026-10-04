package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.api.dto.PlanListVo;
import com.lynx.ad.modules.delivery.api.dto.PlanVo;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.PlanSource;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.port.OceanPlanRow;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PlanQueryService {

    private final AdPlanRepository planRepository;
    private final AdListPort adListPort;
    private final OauthAccountRepository oauthAccountRepository;
    private final QianchuanCredentialFacade credentialFacade;
    private final MetricSnapshotRepository snapshotRepository;

    public PlanQueryService(
            AdPlanRepository planRepository,
            AdListPort adListPort,
            OauthAccountRepository oauthAccountRepository,
            QianchuanCredentialFacade credentialFacade,
            MetricSnapshotRepository snapshotRepository) {
        this.planRepository = planRepository;
        this.adListPort = adListPort;
        this.oauthAccountRepository = oauthAccountRepository;
        this.credentialFacade = credentialFacade;
        this.snapshotRepository = snapshotRepository;
    }

    public PageResult<PlanListVo> list(TenantId tenantId, int pageNum, int pageSize) {
        List<AdPlan> local = planRepository.findByTenant(tenantId);
        Set<String> localQc = new HashSet<>();
        List<PlanListVo> rows = new ArrayList<>();
        var latest = snapshotRepository.listLatestByTenant(tenantId).stream()
                .filter(s -> s.planId() != null)
                .collect(java.util.stream.Collectors.toMap(s -> s.planId().value(), s -> s, (a, b) -> a));
        for (AdPlan plan : local) {
            plan.qcPlanId().ifPresent(id -> localQc.add(id.value()));
            MetricSnapshot snap = latest.get(plan.id().value());
            rows.add(new PlanListVo(
                    plan.id().value(),
                    plan.name().text(),
                    plan.status().name(),
                    plan.source().name(),
                    plan.qcPlanId().map(q -> q.value()).orElse(null),
                    plan.source() == PlanSource.LINGTOU,
                    plan.bindings().isEmpty() ? null : "agg-cover",
                    "展示字段",
                    snap == null ? BigDecimal.ZERO : snap.cost().amount(),
                    snap == null ? BigDecimal.ZERO : snap.gmv().amount(),
                    snap == null || snap.roi() == null ? BigDecimal.ZERO : snap.roi().value()));
        }
        oauthAccountRepository.findByTenant(tenantId).stream().findFirst().ifPresent(acc -> {
            try {
                var cred = credentialFacade.get(acc.id());
                for (SmartBidType type : SmartBidType.values()) {
                    for (OceanPlanRow row : adListPort.list(cred, type)) {
                        if (!localQc.contains(row.qcPlanId())) {
                            rows.add(new PlanListVo(
                                    row.qcPlanId(),
                                    row.name(),
                                    row.status(),
                                    PlanSource.EXTERNAL.name(),
                                    row.qcPlanId(),
                                    false,
                                    row.coverUrl(),
                                    row.guarantee(),
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO));
                        }
                    }
                }
            } catch (Exception ignored) {
                // list still works with local rows
            }
        });
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(rows.size(), from + pageSize);
        List<PlanListVo> slice = from >= rows.size() ? List.of() : rows.subList(from, to);
        return new PageResult<>(slice, rows.size(), pageNum, pageSize);
    }

    public PlanVo get(TenantId tenantId, PlanId id) {
        AdPlan plan = planRepository.findById(tenantId, id).orElseThrow(() -> new BizException(1405, "计划不存在"));
        PlanVo base = PlanCommandService.toVo(plan);
        return snapshotRepository
                .latest(id)
                .map(snap -> new PlanVo(
                        base.planId(),
                        base.name(),
                        base.status(),
                        base.source(),
                        base.qcPlanId(),
                        base.smartBidType(),
                        base.budget(),
                        base.awemeId(),
                        base.rejectReason(),
                        base.lingtou(),
                        base.guarantee(),
                        base.coverUrl(),
                        snap.cost().amount(),
                        snap.gmv().amount(),
                        snap.roi() == null ? BigDecimal.ZERO : snap.roi().value()))
                .orElse(base);
    }

    public record PageResult<T>(List<T> records, long total, int pageNum, int pageSize) {}
}
