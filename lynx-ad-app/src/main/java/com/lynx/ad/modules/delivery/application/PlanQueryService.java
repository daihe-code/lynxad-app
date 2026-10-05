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
import com.lynx.ad.modules.dataops.domain.port.SnapshotPair;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.catalog.domain.port.ProductRepository;
import com.lynx.ad.modules.catalog.domain.model.Product;
import com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ProductRepository productRepository;
    private final AdPlanCreativeBindingStore bindingStore;

    public PlanQueryService(
            AdPlanRepository planRepository,
            AdListPort adListPort,
            OauthAccountRepository oauthAccountRepository,
            QianchuanCredentialFacade credentialFacade,
            MetricSnapshotRepository snapshotRepository,
            ProductRepository productRepository,
            AdPlanCreativeBindingStore bindingStore) {
        this.planRepository = planRepository;
        this.adListPort = adListPort;
        this.oauthAccountRepository = oauthAccountRepository;
        this.credentialFacade = credentialFacade;
        this.snapshotRepository = snapshotRepository;
        this.productRepository = productRepository;
        this.bindingStore = bindingStore;
    }

    public PageResult<PlanListVo> list(TenantId tenantId, int pageNum, int pageSize) {
        List<AdPlan> local = planRepository.findByTenant(tenantId);
        var metadata = planRepository.listMetadataByTenant(tenantId);
        Set<String> localQc = new HashSet<>();
        List<PlanListVo> rows = new ArrayList<>();
        var boundProducts = bindingStore.productIdsByPlan(local.stream().map(p -> p.id().value()).toList());
        Map<String, List<Product>> productsByAweme = new HashMap<>();
        var latest = snapshotRepository.listLatestPairsByTenant(tenantId);
        for (AdPlan plan : local) {
            var meta = metadata.get(plan.id().value());
            plan.qcPlanId().ifPresent(id -> localQc.add(id.value()));
            SnapshotPair pair = latest.get(plan.id().value());
            MetricSnapshot snap = pair == null ? null : pair.current();
            List<String> productIds = boundProducts.getOrDefault(plan.id().value(),
                    plan.productIds().stream().map(p -> p.value()).toList());
            var catalog = productIds.isEmpty() ? List.<Product>of()
                    : productsByAweme.computeIfAbsent(plan.awemeId().value(),
                            key -> productRepository.findByAweme(tenantId, plan.awemeId()));
            var products = productIds.stream().map(id -> catalog.stream()
                    .filter(p -> p.qcProductId().value().equals(id)).findFirst()
                    .map(p -> new PlanListVo.ProductSummary(id, p.name(), p.imageUrl()))
                    .orElse(new PlanListVo.ProductSummary(id, id, null))).toList();
            rows.add(new PlanListVo(
                    plan.id().value(),
                    plan.name().text(),
                    plan.status().name(),
                    plan.source().name(),
                    plan.qcPlanId().map(q -> q.value()).orElse(null),
                    plan.source() == PlanSource.LINGTOU,
                    plan.bindings().isEmpty() ? null : "agg-cover",
                    "展示字段",
                    snap == null ? null : snap.cost().amount(),
                    snap == null ? null : snap.gmv().amount(),
                    snap == null || snap.roi() == null ? null : snap.roi().value(),
                    plan.setting().bidType().oceanValue(),
                    plan.setting().dailyBudget().amount(),
                    plan.setting().roi2Goal().map(r -> r.value()).orElse(null),
                    plan.setting().schedule() == null ? null : plan.setting().schedule().startDate(),
                    plan.setting().schedule() == null ? null : plan.setting().schedule().endDate(),
                    snap == null ? null : snap.convert(),
                    snap == null ? null : snap.ts(),
                    products,
                    comparison(pair), meta == null ? null : meta.createdAt(),
                    meta == null ? null : meta.deliveryPlatform()));
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
                                    null,
                                    null,
                                    null,
                                    row.bidType().oceanValue(), row.budget().amount(), null,
                                    null, null, null, null, List.of(), null, null, null));
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

    private static PlanListVo.MetricComparison comparison(SnapshotPair pair) {
        if (pair == null || pair.current() == null || pair.previous() == null) return null;
        MetricSnapshot current = pair.current(), previous = pair.previous();
        BigDecimal roiChange = current.roi() == null || previous.roi() == null ? null
                : current.roi().value().subtract(previous.roi().value());
        return new PlanListVo.MetricComparison(
                percentChange(current.cost().amount(), previous.cost().amount()),
                percentChange(BigDecimal.valueOf(current.convert()), BigDecimal.valueOf(previous.convert())),
                roiChange, previous.ts(), current.roi() == null || previous.roi() == null ? null
                        : percentChange(current.roi().value(), previous.roi().value()));
    }

    private static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() <= 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }
}
