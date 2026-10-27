package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.IdempotencyKey;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.AudienceJson;
import com.lynx.ad.modules.delivery.domain.model.DeliverySetting;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import com.lynx.ad.modules.delivery.domain.model.PlanSource;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import com.lynx.ad.modules.delivery.domain.model.RoiGoal;
import com.lynx.ad.modules.delivery.domain.model.Schedule;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@TableName("ad_plan")
class AdPlanPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String oauthAccountId;
    private String awemeId;
    private String name;
    private String status;
    private String qcStatus;
    private String qcPlanId;
    private String smartBidType;
    private BigDecimal budget;
    private BigDecimal roi2Goal;
    private String scheduleType;
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String value) { scheduleType = value; }
    private LocalDate startDate;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private LocalDate endDate;
    private Integer publishVersion;
    private String idempotencyKey;
    private String rejectReason;
    private String source;
    private Instant createdAt;
    private String deliveryPlatform;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getOauthAccountId() { return oauthAccountId; }
    public void setOauthAccountId(String oauthAccountId) { this.oauthAccountId = oauthAccountId; }
    public String getAwemeId() { return awemeId; }
    public void setAwemeId(String awemeId) { this.awemeId = awemeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getQcStatus() { return qcStatus; }
    public void setQcStatus(String qcStatus) { this.qcStatus = qcStatus; }
    public String getQcPlanId() { return qcPlanId; }
    public void setQcPlanId(String qcPlanId) { this.qcPlanId = qcPlanId; }
    public String getSmartBidType() { return smartBidType; }
    public void setSmartBidType(String smartBidType) { this.smartBidType = smartBidType; }
    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }
    public BigDecimal getRoi2Goal() { return roi2Goal; }
    public void setRoi2Goal(BigDecimal roi2Goal) { this.roi2Goal = roi2Goal; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getPublishVersion() { return publishVersion; }
    public void setPublishVersion(Integer publishVersion) { this.publishVersion = publishVersion; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getDeliveryPlatform() { return deliveryPlatform; }
    public void setDeliveryPlatform(String deliveryPlatform) { this.deliveryPlatform = deliveryPlatform; }
}

@Mapper
interface AdPlanMapper extends BaseMapper<AdPlanPo> {}

@Repository
public class MpAdPlanRepository implements AdPlanRepository {

    @Override
    public boolean discardDraft(TenantId tenantId, PlanId id) {
        return mapper.delete(new LambdaQueryWrapper<AdPlanPo>()
                .eq(AdPlanPo::getId, id.value()).eq(AdPlanPo::getTenantId, tenantId.value())
                .eq(AdPlanPo::getStatus, PlanStatus.DRAFT.name()).isNull(AdPlanPo::getQcPlanId)) == 1;
    }

    private final AdPlanMapper mapper;
    private final AdPlanCreativeBindingStore bindingStore;
    private PlanReviewStore reviews;
    @org.springframework.beans.factory.annotation.Autowired public void reviewStore(PlanReviewStore value){reviews=value;}
    private PlanAuditNoticeStore auditNotices;
    @org.springframework.beans.factory.annotation.Autowired public void auditNoticeStore(PlanAuditNoticeStore value){auditNotices=value;}
    private PlanOriginStore origins;
    @org.springframework.beans.factory.annotation.Autowired public void originStore(PlanOriginStore value){origins=value;}

    public MpAdPlanRepository(AdPlanMapper mapper, AdPlanCreativeBindingStore bindingStore) {
        this.mapper = mapper;
        this.bindingStore = bindingStore;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void save(AdPlan plan) {
        AdPlanPo po = mapper.selectOne(new LambdaQueryWrapper<AdPlanPo>().eq(AdPlanPo::getId,plan.id().value()).last("FOR UPDATE"));
        boolean insert = po == null;
        String previousStatus=po==null?null:po.getStatus(),previousQc=po==null?null:po.getQcPlanId();
        boolean newReview=insert || !"PENDING_AUDIT".equals(po.getStatus()) || !java.util.Objects.equals(po.getQcPlanId(),plan.qcPlanId().map(QcPlanId::value).orElse(null));
        if (insert) {
            po = new AdPlanPo();
            po.setId(plan.id().value());
            po.setCreatedAt(Instant.now());
            // The current local creation flow is 商品全域投放.
            po.setDeliveryPlatform(plan.source() == PlanSource.LINGTOU ? "ALL" : null);
        }
        po.setTenantId(plan.tenantId().value());
        po.setOauthAccountId(plan.accountId() == null ? null : plan.accountId().value());
        po.setAwemeId(plan.awemeId().value());
        po.setName(plan.name().text());
        po.setStatus(plan.status().name());
        po.setQcStatus(plan.qcStatus());
        po.setQcPlanId(plan.qcPlanId().map(QcPlanId::value).orElse(null));
        po.setSmartBidType(plan.setting().bidType().oceanValue());
        po.setBudget(plan.setting().dailyBudget().amount());
        po.setRoi2Goal(plan.setting().roi2Goal().map(RoiGoal::value).orElse(null));
        po.setScheduleType(plan.setting().schedule() == null ? null : plan.setting().schedule().type().name());
        po.setStartDate(plan.setting().schedule() == null ? null : plan.setting().schedule().startDate());
        po.setEndDate(plan.setting().schedule() == null ? null : plan.setting().schedule().endDate());
        po.setPublishVersion(plan.publishVersion());
        po.setIdempotencyKey(plan.publishKey().map(IdempotencyKey::value).orElse(null));
        po.setRejectReason(plan.rejectReason());
        po.setSource(plan.source().name());
        if (insert) {
            mapper.insert(po);
        } else {
            // A stale sync/settings response must never restore a deleted physical plan.
            var update = new LambdaQueryWrapper<AdPlanPo>().eq(AdPlanPo::getId, po.getId())
                    .eq(AdPlanPo::getTenantId, po.getTenantId());
            if (plan.status() != PlanStatus.DELETED) update.ne(AdPlanPo::getStatus, PlanStatus.DELETED.name());
            if(mapper.update(po, update)==0)return;
        }
        if(auditNotices!=null)auditNotices.saved(plan,previousStatus,previousQc);
        if(reviews!=null)reviews.saved(plan,newReview);
    }

    @Override
    public Optional<AdPlan> findById(TenantId tenantId, PlanId id) {
        AdPlanPo po = mapper.selectById(id.value());
        if (po == null || !tenantId.value().equals(po.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(withOrigin(toDomain(po)));
    }

    @Override
    public Optional<AdPlan> findByIdForUpdate(TenantId tenantId, PlanId id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<AdPlanPo>()
                .eq(AdPlanPo::getId, id.value()).eq(AdPlanPo::getTenantId, tenantId.value()).last("FOR UPDATE")))
                .map(this::toDomain).map(this::withOrigin);
    }

    @Override
    public boolean existsUnfinishedName(TenantId tenantId, AwemeId awemeId, PlanName name, Optional<PlanId> exclude) {
        return mapper.selectList(new LambdaQueryWrapper<AdPlanPo>()
                        .eq(AdPlanPo::getTenantId, tenantId.value())
                        .eq(AdPlanPo::getAwemeId, awemeId.value())
                        .eq(AdPlanPo::getName, name.text())
                        .notIn(AdPlanPo::getStatus, "FINISHED", "DELETED"))
                .stream()
                .anyMatch(p -> exclude.isEmpty() || !exclude.get().value().equals(p.getId()));
    }

    @Override
    public Optional<AdPlan> findByQcPlanId(TenantId tenantId, QcPlanId qcPlanId) {
        AdPlanPo po = mapper.selectOne(new LambdaQueryWrapper<AdPlanPo>()
                .eq(AdPlanPo::getTenantId, tenantId.value())
                .eq(AdPlanPo::getQcPlanId, qcPlanId.value()));
        return Optional.ofNullable(po).map(this::toDomain).map(this::withOrigin);
    }

    @Override
    public List<AdPlan> findByTenant(TenantId tenantId) {
        return withOrigins(mapper.selectList(new LambdaQueryWrapper<AdPlanPo>().eq(AdPlanPo::getTenantId, tenantId.value())).stream()
                .map(this::toDomain)
                .toList());
    }

    @Override
    public Map<String, ListMetadata> listMetadataByTenant(TenantId tenantId) {
        return mapper.selectList(new LambdaQueryWrapper<AdPlanPo>()
                        .select(AdPlanPo::getId, AdPlanPo::getCreatedAt, AdPlanPo::getDeliveryPlatform)
                        .eq(AdPlanPo::getTenantId, tenantId.value()))
                .stream().collect(Collectors.toMap(AdPlanPo::getId,
                        row -> new ListMetadata(row.getCreatedAt(), row.getDeliveryPlatform())));
    }

    @Override
    public Optional<AdPlan> findByIdOnly(PlanId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain).map(this::withOrigin);
    }

    @Override
    public List<AdPlan> findPendingAudit() {
        return mapper.selectList(new LambdaQueryWrapper<AdPlanPo>().eq(AdPlanPo::getStatus, "PENDING_AUDIT")).stream()
                .map(this::toDomain)
                .toList();
    }

    private AdPlan withOrigin(AdPlan plan) { withOrigins(List.of(plan));return plan; }

    private List<AdPlan> withOrigins(List<AdPlan> values) {
        if(origins==null)return values;
        record Scope(TenantId tenant,AccountId account) {}
        values.stream().filter(p->p.source()==PlanSource.EXTERNAL && p.qcPlanId().isPresent())
            .collect(Collectors.groupingBy(p->new Scope(p.tenantId(),p.accountId())))
            .forEach((scope,rows)->{
                var published=origins.publishedIds(scope.tenant(),scope.account(),rows.stream().map(p->p.qcPlanId().orElseThrow().value()).toList());
                rows.stream().filter(p->published.contains(p.qcPlanId().orElseThrow().value())).forEach(AdPlan::restoreLingtouSource);
            });
        return values;
    }

    private AdPlan toDomain(AdPlanPo po) {
        DeliverySetting setting = new DeliverySetting(
                SmartBidType.fromOcean(po.getSmartBidType()),
                Money.ofYuan(po.getBudget() == null ? BigDecimal.ZERO : po.getBudget()),
                Optional.ofNullable(po.getRoi2Goal()).map(RoiGoal::new),
                new Schedule(po.getScheduleType()==null ? com.lynx.ad.modules.delivery.domain.model.ScheduleType.FROM_NOW : com.lynx.ad.modules.delivery.domain.model.ScheduleType.valueOf(po.getScheduleType()), po.getStartDate(), po.getEndDate()),
                AudienceJson.empty());
        return new AdPlan(
                new PlanId(po.getId()),
                new TenantId(po.getTenantId()),
                po.getOauthAccountId() == null ? new AccountId("unknown") : new AccountId(po.getOauthAccountId()),
                new AwemeId(po.getAwemeId()),
                po.getQcPlanId() != null ? PlanName.fromPlatform(po.getName()) : new PlanName(po.getName()),
                PlanStatus.valueOf(po.getStatus()),
                po.getSource() == null ? PlanSource.LINGTOU : PlanSource.valueOf(po.getSource()),
                setting,
                bindingStore.productIdsByPlan(List.of(po.getId())).getOrDefault(po.getId(), List.of()).stream()
                        .map(com.lynx.ad.common.domain.Ids.QcProductId::new).toList(),
                po.getQcPlanId() == null ? null : new QcPlanId(po.getQcPlanId()),
                po.getQcStatus(),
                po.getRejectReason(),
                po.getPublishVersion() == null ? 0 : po.getPublishVersion(),
                po.getIdempotencyKey() == null ? null : new IdempotencyKey(po.getIdempotencyKey()),
                List.of());
    }
}
