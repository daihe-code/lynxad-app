package com.lynx.ad.modules.billing.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.persistence.JsonbStringTypeHandler;
import com.lynx.ad.modules.billing.domain.model.BillingPlan;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import com.lynx.ad.modules.billing.domain.port.PlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@TableName(value = "billing_plan", autoResultMap = true)
class BillingPlanPo {
    @TableId
    private String code;
    private String name;
    private Integer listPriceFen;
    private Integer adAccountLimit;
    private Integer sortNo;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String benefitsJson;

    private String suitableFor;
    private Instant createdAt;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getListPriceFen() {
        return listPriceFen;
    }

    public void setListPriceFen(Integer listPriceFen) {
        this.listPriceFen = listPriceFen;
    }

    public Integer getAdAccountLimit() {
        return adAccountLimit;
    }

    public void setAdAccountLimit(Integer adAccountLimit) {
        this.adAccountLimit = adAccountLimit;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getBenefitsJson() {
        return benefitsJson;
    }

    public void setBenefitsJson(String benefitsJson) {
        this.benefitsJson = benefitsJson;
    }

    public String getSuitableFor() {
        return suitableFor;
    }

    public void setSuitableFor(String suitableFor) {
        this.suitableFor = suitableFor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

@Mapper
interface BillingPlanMapper extends BaseMapper<BillingPlanPo> {}

@Repository
public class MpPlanRepository implements PlanRepository {

    private final BillingPlanMapper mapper;
    private final ObjectMapper objectMapper;

    public MpPlanRepository(BillingPlanMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BillingPlan> findAll() {
        return mapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<BillingPlan> findByCode(PlanCode code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(code.name())).map(this::toDomain);
    }

    private BillingPlan toDomain(BillingPlanPo po) {
        List<String> benefits = List.of();
        try {
            if (po.getBenefitsJson() != null && !po.getBenefitsJson().isBlank()) {
                benefits = objectMapper.readValue(po.getBenefitsJson(), new TypeReference<>() {});
            }
        } catch (Exception ignored) {
            benefits = List.of();
        }
        return new BillingPlan(
                PlanCode.valueOf(po.getCode()),
                po.getName(),
                po.getListPriceFen(),
                po.getAdAccountLimit(),
                po.getSortNo(),
                benefits,
                po.getSuitableFor());
    }
}
