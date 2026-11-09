package com.lynx.ad.modules.billing.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.BillingOrder;
import com.lynx.ad.modules.billing.domain.model.OrderStatus;
import com.lynx.ad.modules.billing.domain.model.OrderType;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@TableName("billing_order")
class BillingOrderPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String planCode;
    private String fromPlanCode;
    private String type;
    private String status;
    private Integer listPriceFen;
    private Integer payableFen;
    private String discountLabel;
    private Instant createdAt;
    private Instant paidAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getFromPlanCode() {
        return fromPlanCode;
    }

    public void setFromPlanCode(String fromPlanCode) {
        this.fromPlanCode = fromPlanCode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getListPriceFen() {
        return listPriceFen;
    }

    public void setListPriceFen(Integer listPriceFen) {
        this.listPriceFen = listPriceFen;
    }

    public Integer getPayableFen() {
        return payableFen;
    }

    public void setPayableFen(Integer payableFen) {
        this.payableFen = payableFen;
    }

    public String getDiscountLabel() {
        return discountLabel;
    }

    public void setDiscountLabel(String discountLabel) {
        this.discountLabel = discountLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }
}

@Mapper
interface BillingOrderMapper extends BaseMapper<BillingOrderPo> {}

@Repository
public class MpOrderRepository implements OrderRepository {

    private final BillingOrderMapper mapper;

    public MpOrderRepository(BillingOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(BillingOrder order) {
        BillingOrderPo po = toPo(order);
        if (mapper.selectById(order.id()) == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<BillingOrder> findById(TenantId tenantId, String orderId) {
        BillingOrderPo po = mapper.selectById(orderId);
        if (po == null || !po.getTenantId().equals(tenantId.value())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public Optional<BillingOrder> findPendingPay(TenantId tenantId) {
        BillingOrderPo po = mapper.selectOne(new LambdaQueryWrapper<BillingOrderPo>()
                .eq(BillingOrderPo::getTenantId, tenantId.value())
                .ne(BillingOrderPo::getType, "POINT_RECHARGE")
                .eq(BillingOrderPo::getStatus, OrderStatus.PENDING_PAY.name())
                .last("limit 1"));
        return Optional.ofNullable(po).map(MpOrderRepository::toDomain);
    }

    @Override
    public boolean hasPaidPlan(TenantId tenantId, PlanCode planCode) {
        return mapper.selectCount(new LambdaQueryWrapper<BillingOrderPo>()
                        .eq(BillingOrderPo::getTenantId, tenantId.value())
                        .ne(BillingOrderPo::getType, "POINT_RECHARGE")
                        .eq(BillingOrderPo::getPlanCode, planCode.name())
                        .eq(BillingOrderPo::getStatus, OrderStatus.PAID.name()))
                > 0;
    }

    @Override
    public List<BillingOrder> findPaidWithAmount(TenantId tenantId) {
        return mapper.selectList(new LambdaQueryWrapper<BillingOrderPo>()
                        .eq(BillingOrderPo::getTenantId, tenantId.value())
                        .eq(BillingOrderPo::getStatus, OrderStatus.PAID.name())
                        .gt(BillingOrderPo::getPayableFen, 0)
                        .orderByDesc(BillingOrderPo::getPaidAt))
                .stream()
                .map(MpOrderRepository::toDomain)
                .toList();
    }

    private static BillingOrderPo toPo(BillingOrder order) {
        BillingOrderPo po = new BillingOrderPo();
        po.setId(order.id());
        po.setTenantId(order.tenantId().value());
        po.setPlanCode(order.planCode().name());
        po.setFromPlanCode(order.fromPlanCode() == null ? null : order.fromPlanCode().name());
        po.setType(order.type().name());
        po.setStatus(order.status().name());
        po.setListPriceFen(order.listPriceFen());
        po.setPayableFen(order.payableFen());
        po.setDiscountLabel(order.discountLabel());
        po.setCreatedAt(order.createdAt());
        po.setPaidAt(order.paidAt());
        return po;
    }

    private static BillingOrder toDomain(BillingOrderPo po) {
        return new BillingOrder(
                po.getId(),
                new TenantId(po.getTenantId()),
                PlanCode.valueOf(po.getPlanCode()),
                po.getFromPlanCode() == null ? null : PlanCode.valueOf(po.getFromPlanCode()),
                OrderType.valueOf(po.getType()),
                OrderStatus.valueOf(po.getStatus()),
                po.getListPriceFen(),
                po.getPayableFen(),
                po.getDiscountLabel(),
                po.getCreatedAt(),
                po.getPaidAt());
    }
}
