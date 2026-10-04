package com.lynx.ad.modules.billing.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import com.lynx.ad.modules.billing.domain.model.SubStatus;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@TableName("billing_subscription")
class BillingSubscriptionPo {
    @TableId(type = IdType.INPUT)
    private String tenantId;
    private String planCode;
    private String status;
    private Instant periodStart;
    private Instant periodEnd;
    private Boolean autoRenew;
    private String payMethod;
    private Integer paidFenThisPeriod;
    private String nextPlanCode;
    private Instant updatedAt;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Boolean getAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(Boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public Integer getPaidFenThisPeriod() {
        return paidFenThisPeriod;
    }

    public void setPaidFenThisPeriod(Integer paidFenThisPeriod) {
        this.paidFenThisPeriod = paidFenThisPeriod;
    }

    public String getNextPlanCode() {
        return nextPlanCode;
    }

    public void setNextPlanCode(String nextPlanCode) {
        this.nextPlanCode = nextPlanCode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

@Mapper
interface BillingSubscriptionMapper extends BaseMapper<BillingSubscriptionPo> {}

@Repository
public class MpSubscriptionRepository implements SubscriptionRepository {

    private final BillingSubscriptionMapper mapper;

    public MpSubscriptionRepository(BillingSubscriptionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Subscription subscription) {
        BillingSubscriptionPo po = toPo(subscription);
        if (mapper.selectById(subscription.tenantId().value()) == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<Subscription> findByTenant(TenantId tenantId) {
        return Optional.ofNullable(mapper.selectById(tenantId.value())).map(this::toDomain);
    }

    private static BillingSubscriptionPo toPo(Subscription sub) {
        BillingSubscriptionPo po = new BillingSubscriptionPo();
        po.setTenantId(sub.tenantId().value());
        po.setPlanCode(sub.planCode().name());
        po.setStatus(sub.status().name());
        po.setPeriodStart(sub.periodStart());
        po.setPeriodEnd(sub.periodEnd());
        po.setAutoRenew(sub.autoRenew());
        po.setPayMethod(sub.payMethod());
        po.setPaidFenThisPeriod(sub.paidFenThisPeriod());
        po.setNextPlanCode(sub.nextPlanCode() == null ? null : sub.nextPlanCode().name());
        po.setUpdatedAt(sub.updatedAt());
        return po;
    }

    private Subscription toDomain(BillingSubscriptionPo po) {
        return new Subscription(
                new TenantId(po.getTenantId()),
                PlanCode.valueOf(po.getPlanCode()),
                SubStatus.valueOf(po.getStatus()),
                po.getPeriodStart(),
                po.getPeriodEnd(),
                Boolean.TRUE.equals(po.getAutoRenew()),
                po.getPayMethod(),
                po.getPaidFenThisPeriod() == null ? 0 : po.getPaidFenThisPeriod(),
                po.getNextPlanCode() == null ? null : PlanCode.valueOf(po.getNextPlanCode()),
                po.getUpdatedAt());
    }
}
