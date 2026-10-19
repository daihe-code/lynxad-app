package com.lynx.ad.modules.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.identity.domain.port.OauthAppBindingRepository;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@TableName("oauth_app_binding")
class OauthAppBindingPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String advertiserId;
    private String appCode;
    private String oauthAccountId;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAdvertiserId() { return advertiserId; }
    public void setAdvertiserId(String advertiserId) { this.advertiserId = advertiserId; }
    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getOauthAccountId() { return oauthAccountId; }
    public void setOauthAccountId(String oauthAccountId) { this.oauthAccountId = oauthAccountId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

@Mapper
interface OauthAppBindingMapper extends BaseMapper<OauthAppBindingPo> {}

@Repository
public class MpOauthAppBindingRepository implements OauthAppBindingRepository {

    static final String QIANCHUAN = "QIANCHUAN";

    private final OauthAppBindingMapper mapper;

    public MpOauthAppBindingRepository(OauthAppBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void upsertQianchuan(TenantId tenantId, String advertiserId, AccountId accountId) {
        OauthAppBindingPo existing = mapper.selectOne(new LambdaQueryWrapper<OauthAppBindingPo>()
                .eq(OauthAppBindingPo::getTenantId, tenantId.value())
                .eq(OauthAppBindingPo::getAdvertiserId, advertiserId)
                .eq(OauthAppBindingPo::getAppCode, QIANCHUAN));
        if (existing == null) {
            OauthAppBindingPo created = new OauthAppBindingPo();
            created.setId(UUID.randomUUID().toString());
            created.setTenantId(tenantId.value());
            created.setAdvertiserId(advertiserId);
            created.setAppCode(QIANCHUAN);
            created.setOauthAccountId(accountId.value());
            created.setCreatedAt(Instant.now());
            mapper.insert(created);
            return;
        }
        mapper.update(
                null,
                new LambdaUpdateWrapper<OauthAppBindingPo>()
                        .eq(OauthAppBindingPo::getId, existing.getId())
                        .set(OauthAppBindingPo::getOauthAccountId, accountId.value()));
    }

    @Override
    public void deleteByAccount(AccountId accountId) {
        mapper.delete(new LambdaQueryWrapper<OauthAppBindingPo>()
                .eq(OauthAppBindingPo::getOauthAccountId, accountId.value()));
    }
}
