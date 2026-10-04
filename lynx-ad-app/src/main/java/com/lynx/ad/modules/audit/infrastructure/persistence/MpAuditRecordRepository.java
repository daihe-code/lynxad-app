package com.lynx.ad.modules.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.audit.domain.model.AuditRecord;
import com.lynx.ad.modules.audit.domain.port.AuditRecordRepository;
import com.lynx.ad.modules.audit.domain.service.TenantWideVisibilityPolicy;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MpAuditRecordRepository implements AuditRecordRepository {

    private final AuditRecordMapper mapper;

    public MpAuditRecordRepository(AuditRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(AuditRecord record) {
        AuditRecordPo po = new AuditRecordPo();
        po.setId(record.id());
        po.setTenantId(record.tenantId() == null ? null : record.tenantId().value());
        po.setUserId(record.userId() == null ? null : record.userId().value());
        po.setActorName(record.actorName());
        po.setActionType(record.actionType().name());
        po.setObjectType(record.objectType());
        po.setObjectId(record.objectId());
        po.setObjectLabel(record.objectLabel());
        po.setResult(record.result());
        po.setIp(record.ip());
        po.setSessionId(record.sessionId());
        po.setUserAgent(record.userAgent());
        po.setExtra(record.extra());
        po.setCreatedAt(record.createdAt());
        mapper.insert(po);
    }

    @Override
    public Page pageByTenant(
            TenantId tenantId,
            AuditActionType actionType,
            Instant from,
            Instant to,
            String q,
            int offset,
            int limit) {
        LambdaQueryWrapper<AuditRecordPo> countW = base(tenantId, actionType, from, to, q);
        Long total = mapper.selectCount(countW);
        LambdaQueryWrapper<AuditRecordPo> listW = base(tenantId, actionType, from, to, q);
        listW.orderByDesc(AuditRecordPo::getCreatedAt);
        listW.last("LIMIT " + limit + " OFFSET " + offset);
        List<AuditRecord> records = mapper.selectList(listW).stream().map(this::toDomain).filter(r ->
                TenantWideVisibilityPolicy.visibleToTenant(
                        r.tenantId() == null ? null : r.tenantId().value(), tenantId.value()))
                .toList();
        return new Page(records, total == null ? 0 : total);
    }

    private static LambdaQueryWrapper<AuditRecordPo> base(
            TenantId tenantId, AuditActionType actionType, Instant from, Instant to, String q) {
        LambdaQueryWrapper<AuditRecordPo> w = new LambdaQueryWrapper<>();
        w.eq(AuditRecordPo::getTenantId, tenantId.value());
        if (actionType != null) {
            w.eq(AuditRecordPo::getActionType, actionType.name());
        }
        if (from != null) {
            w.ge(AuditRecordPo::getCreatedAt, from);
        }
        if (to != null) {
            w.le(AuditRecordPo::getCreatedAt, to);
        }
        if (q != null && !q.isBlank()) {
            w.and(x -> x.like(AuditRecordPo::getActorName, q).or().like(AuditRecordPo::getObjectLabel, q));
        }
        return w;
    }

    private AuditRecord toDomain(AuditRecordPo po) {
        return AuditRecord.restore(
                po.getId(),
                po.getTenantId() == null ? null : new TenantId(po.getTenantId()),
                po.getUserId() == null ? null : new UserId(po.getUserId()),
                po.getActorName(),
                AuditActionType.valueOf(po.getActionType()),
                po.getObjectType(),
                po.getObjectId(),
                po.getObjectLabel(),
                po.getResult(),
                po.getIp(),
                po.getSessionId(),
                po.getUserAgent(),
                po.getExtra(),
                po.getCreatedAt());
    }
}
