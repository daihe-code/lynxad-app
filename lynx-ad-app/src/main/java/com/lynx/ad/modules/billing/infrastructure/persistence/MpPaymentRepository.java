package com.lynx.ad.modules.billing.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.BillingPayment;
import com.lynx.ad.modules.billing.domain.model.PayChannel;
import com.lynx.ad.modules.billing.domain.model.PaymentStatus;
import com.lynx.ad.modules.billing.domain.port.PaymentRepository;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@TableName("billing_payment")
class BillingPaymentPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String orderId;
    private String channel;
    private String status;
    private Integer amountFen;
    private String channelTradeNo;
    private String mockNonce;
    private Instant createdAt;
    private Instant completedAt;

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

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAmountFen() {
        return amountFen;
    }

    public void setAmountFen(Integer amountFen) {
        this.amountFen = amountFen;
    }

    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    public void setChannelTradeNo(String channelTradeNo) {
        this.channelTradeNo = channelTradeNo;
    }

    public String getMockNonce() {
        return mockNonce;
    }

    public void setMockNonce(String mockNonce) {
        this.mockNonce = mockNonce;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

@Mapper
interface BillingPaymentMapper extends BaseMapper<BillingPaymentPo> {}

@Repository
public class MpPaymentRepository implements PaymentRepository {

    private final BillingPaymentMapper mapper;

    public MpPaymentRepository(BillingPaymentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(BillingPayment payment) {
        BillingPaymentPo po = toPo(payment);
        if (mapper.selectById(payment.id()) == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<BillingPayment> findById(TenantId tenantId, String paymentId) {
        BillingPaymentPo po = mapper.selectById(paymentId);
        if (po == null || !po.getTenantId().equals(tenantId.value())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public Optional<BillingPayment> findByIdOnly(String paymentId) {
        return Optional.ofNullable(mapper.selectById(paymentId)).map(this::toDomain);
    }

    private static BillingPaymentPo toPo(BillingPayment payment) {
        BillingPaymentPo po = new BillingPaymentPo();
        po.setId(payment.id());
        po.setTenantId(payment.tenantId().value());
        po.setOrderId(payment.orderId());
        po.setChannel(payment.channel().name());
        po.setStatus(payment.status().name());
        po.setAmountFen(payment.amountFen());
        po.setChannelTradeNo(payment.channelTradeNo());
        po.setMockNonce(payment.mockNonce());
        po.setCreatedAt(payment.createdAt());
        po.setCompletedAt(payment.completedAt());
        return po;
    }

    private BillingPayment toDomain(BillingPaymentPo po) {
        return new BillingPayment(
                po.getId(),
                new TenantId(po.getTenantId()),
                po.getOrderId(),
                PayChannel.valueOf(po.getChannel()),
                PaymentStatus.valueOf(po.getStatus()),
                po.getAmountFen(),
                po.getChannelTradeNo(),
                po.getMockNonce(),
                po.getCreatedAt(),
                po.getCompletedAt());
    }
}
