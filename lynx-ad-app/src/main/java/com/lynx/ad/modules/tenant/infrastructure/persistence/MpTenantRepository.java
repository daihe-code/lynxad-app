package com.lynx.ad.modules.tenant.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import com.lynx.ad.modules.tenant.domain.model.TenantMember;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MpTenantRepository implements TenantRepository {

    private final TenantMapper tenantMapper;
    private final TenantMemberMapper memberMapper;

    public MpTenantRepository(TenantMapper tenantMapper, TenantMemberMapper memberMapper) {
        this.tenantMapper = tenantMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    @Transactional
    public void save(Tenant tenant) {
        TenantPo po = tenantMapper.selectById(tenant.id().value());
        if (po == null) {
            po = new TenantPo();
            po.setId(tenant.id().value());
            po.setName(tenant.name());
            po.setStatus(tenant.status());
            po.setCreatedAt(Instant.now());
            tenantMapper.insert(po);
        } else {
            po.setName(tenant.name());
            po.setStatus(tenant.status());
            tenantMapper.updateById(po);
        }
        for (TenantMember m : tenant.members()) {
            TenantMemberPo existing = memberMapper.selectOne(new LambdaQueryWrapper<TenantMemberPo>()
                    .eq(TenantMemberPo::getTenantId, tenant.id().value())
                    .eq(TenantMemberPo::getUserId, m.userId().value()));
            if (existing == null) {
                TenantMemberPo mp = new TenantMemberPo();
                mp.setTenantId(tenant.id().value());
                mp.setUserId(m.userId().value());
                mp.setMemberRole(m.role().name());
                mp.setMembershipId(java.util.UUID.randomUUID().toString());
                mp.setStatus("ACTIVE");
                mp.setPermissionVersion(1L);
                mp.setCreatedAt(m.joinedAt());
                memberMapper.insert(mp);
            }
        }
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return Optional.ofNullable(tenantMapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<Tenant> findByUserId(UserId userId) {
        TenantMemberPo mp = memberMapper.selectOne(
                new LambdaQueryWrapper<TenantMemberPo>().eq(TenantMemberPo::getUserId, userId.value())
                        .eq(TenantMemberPo::getStatus,"ACTIVE").orderByAsc(TenantMemberPo::getCreatedAt,TenantMemberPo::getTenantId).last("limit 1"));
        if (mp == null) {
            return Optional.empty();
        }
        return findById(new TenantId(mp.getTenantId()));
    }

    @Override
    public List<Tenant> findAll() {
        return tenantMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    private Tenant toDomain(TenantPo po) {
        List<TenantMemberPo> members = memberMapper.selectList(
                new LambdaQueryWrapper<TenantMemberPo>().eq(TenantMemberPo::getTenantId, po.getId()).eq(TenantMemberPo::getStatus,"ACTIVE"));
        List<TenantMember> domainMembers = members.stream()
                .map(m -> new TenantMember(
                        new UserId(m.getUserId()), MemberRole.valueOf(m.getMemberRole()), m.getCreatedAt()))
                .toList();
        return new Tenant(new TenantId(po.getId()), po.getName(), po.getStatus(), domainMembers);
    }
}
