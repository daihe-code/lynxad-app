package com.lynx.ad.modules.billing.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.domain.model.Trial;
import com.lynx.ad.modules.billing.domain.port.TrialRepository;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@TableName("billing_trial")
class TrialPo {
    @TableId(type = IdType.INPUT)
    private String tenantId;
    private Instant startedAt;
    private Instant expiresAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}

@Mapper
interface TrialMapper extends BaseMapper<TrialPo> {}

@Repository
public class MpTrialRepository implements TrialRepository {

    private final TrialMapper mapper;

    public MpTrialRepository(TrialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Trial trial) {
        TrialPo po = mapper.selectById(trial.tenantId().value());
        if (po == null) {
            po = new TrialPo();
            po.setTenantId(trial.tenantId().value());
            po.setStartedAt(trial.startedAt());
            po.setExpiresAt(trial.expiresAt());
            mapper.insert(po);
            return;
        }
        po.setStartedAt(trial.startedAt());
        po.setExpiresAt(trial.expiresAt());
        mapper.updateById(po);
    }

    @Override
    public Optional<Trial> findByTenant(TenantId id) {
        TrialPo po = mapper.selectById(id.value());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(new Trial(id, po.getStartedAt(), po.getExpiresAt()));
    }
}
