package com.lynx.ad.modules.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.identity.domain.model.OauthAuthCodeAudit;
import com.lynx.ad.modules.identity.domain.port.OauthAuthCodeAuditRepository;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@TableName("oauth_auth_code_audit")
class OauthAuthCodeAuditPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String userId;
    private String authCodeHash;
    private String advertiserId;
    private String status;
    private Instant createdAt;
    private Instant usedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAuthCodeHash() { return authCodeHash; }
    public void setAuthCodeHash(String authCodeHash) { this.authCodeHash = authCodeHash; }
    public String getAdvertiserId() { return advertiserId; }
    public void setAdvertiserId(String advertiserId) { this.advertiserId = advertiserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
}

@Mapper
interface OauthAuthCodeAuditMapper extends BaseMapper<OauthAuthCodeAuditPo> {}

@Repository
public class MpOauthAuthCodeAuditRepository implements OauthAuthCodeAuditRepository {

    private final OauthAuthCodeAuditMapper mapper;

    public MpOauthAuthCodeAuditRepository(OauthAuthCodeAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OauthAuthCodeAudit audit) {
        OauthAuthCodeAuditPo po = mapper.selectById(audit.id());
        boolean insert = po == null;
        if (insert) {
            po = new OauthAuthCodeAuditPo();
            po.setId(audit.id());
        }
        po.setTenantId(audit.tenantId().value());
        po.setUserId(audit.userId());
        po.setAuthCodeHash(audit.authCodeHash());
        po.setAdvertiserId(audit.advertiserId());
        po.setStatus(audit.status());
        po.setCreatedAt(audit.createdAt());
        po.setUsedAt(audit.usedAt());
        if (insert) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<OauthAuthCodeAudit> findByTenantAndHash(TenantId tenantId, String authCodeHash) {
        OauthAuthCodeAuditPo po = mapper.selectOne(new LambdaQueryWrapper<OauthAuthCodeAuditPo>()
                .eq(OauthAuthCodeAuditPo::getTenantId, tenantId.value())
                .eq(OauthAuthCodeAuditPo::getAuthCodeHash, authCodeHash)
                .last("limit 1"));
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(new OauthAuthCodeAudit(
                po.getId(),
                new TenantId(po.getTenantId()),
                po.getUserId(),
                po.getAuthCodeHash(),
                po.getAdvertiserId(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUsedAt()));
    }
}
