package com.lynx.ad.modules.dataops.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.dataops.domain.model.Ctr;
import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;
import com.lynx.ad.modules.dataops.domain.model.Roi;
import com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository;
import com.lynx.ad.modules.dataops.domain.port.SnapshotPair;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MpMetricSnapshotRepository implements MetricSnapshotRepository {

    private final MetricSnapshotMapper mapper;

    public MpMetricSnapshotRepository(MetricSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(MetricSnapshot snapshot) {
        MetricSnapshotPo po = new MetricSnapshotPo();
        po.setTenantId(snapshot.tenantId().value());
        po.setAdPlanId(snapshot.planId() == null ? null : snapshot.planId().value());
        po.setQcPlanId(snapshot.qcPlanId() == null ? null : snapshot.qcPlanId().value());
        po.setSnapshotTs(snapshot.ts());
        po.setImpression(snapshot.impression());
        po.setClick(snapshot.click());
        po.setCost(snapshot.cost() == null ? BigDecimal.ZERO : snapshot.cost().amount());
        po.setConvert(snapshot.convert());
        po.setGmv(snapshot.gmv() == null ? BigDecimal.ZERO : snapshot.gmv().amount());
        po.setRoi(snapshot.roi() == null ? BigDecimal.ZERO : snapshot.roi().value());
        po.setCtr(snapshot.ctr() == null ? BigDecimal.ZERO : snapshot.ctr().value());
        mapper.insert(po);
    }

    @Override
    public Optional<MetricSnapshot> latest(PlanId planId) {
        MetricSnapshotPo po = mapper.selectOne(new LambdaQueryWrapper<MetricSnapshotPo>()
                .eq(MetricSnapshotPo::getAdPlanId, planId.value())
                .orderByDesc(MetricSnapshotPo::getSnapshotTs)
                .orderByDesc(MetricSnapshotPo::getId)
                .last("limit 1"));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<SnapshotPair> latestPair(PlanId planId) {
        List<MetricSnapshot> list = mapper
                .selectList(new LambdaQueryWrapper<MetricSnapshotPo>()
                        .eq(MetricSnapshotPo::getAdPlanId, planId.value())
                        .orderByDesc(MetricSnapshotPo::getSnapshotTs)
                        .orderByDesc(MetricSnapshotPo::getId))
                .stream()
                .map(this::toDomain)
                .toList();
        if (list.isEmpty()) {
            return Optional.empty();
        }
        MetricSnapshot prev = list.size() > 1 ? list.get(1) : null;
        return Optional.of(new SnapshotPair(list.get(0), prev));
    }

    @Override
    public List<MetricSnapshot> listAll() {
        return TenantContext.tenantId().map(this::listLatestByTenant).orElse(List.of());
    }

    @Override
    public List<MetricSnapshot> listLatestByTenant(TenantId tenantId) {
        List<MetricSnapshotPo> rows = mapper.selectList(new LambdaQueryWrapper<MetricSnapshotPo>()
                .eq(MetricSnapshotPo::getTenantId, tenantId.value())
                .orderByDesc(MetricSnapshotPo::getSnapshotTs)
                .orderByDesc(MetricSnapshotPo::getId));
        Map<String, MetricSnapshot> latest = new LinkedHashMap<>();
        for (MetricSnapshotPo po : rows) {
            if (po.getAdPlanId() == null) {
                continue;
            }
            latest.putIfAbsent(po.getAdPlanId(), toDomain(po));
        }
        List<MetricSnapshot> out = new ArrayList<>(latest.values());
        out.sort(Comparator.comparing(MetricSnapshot::ts).reversed());
        return out;
    }

    private MetricSnapshot toDomain(MetricSnapshotPo po) {
        return MetricSnapshot.of(
                new TenantId(po.getTenantId()),
                po.getAdPlanId() == null ? null : new PlanId(po.getAdPlanId()),
                po.getQcPlanId() == null ? null : new QcPlanId(po.getQcPlanId()),
                po.getSnapshotTs(),
                po.getImpression() == null ? 0 : po.getImpression(),
                po.getClick() == null ? 0 : po.getClick(),
                Money.ofYuan(po.getCost() == null ? BigDecimal.ZERO : po.getCost()),
                po.getConvert() == null ? 0 : po.getConvert(),
                Money.ofYuan(po.getGmv() == null ? BigDecimal.ZERO : po.getGmv()),
                Roi.of(po.getRoi()),
                Ctr.of(po.getCtr()));
    }
}
