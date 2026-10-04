package com.lynx.ad.modules.audit.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.audit.api.dto.AuditPageVo;
import com.lynx.ad.modules.audit.api.dto.AuditRecordVo;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.audit.domain.port.AuditRecordRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

    private final AuditRecordRepository repository;

    public AuditQueryService(AuditRecordRepository repository) {
        this.repository = repository;
    }

    public AuditPageVo page(String actionType, String from, String to, String q, int pageNum, int pageSize) {
        TenantId tenantId = TenantContext.tenantId().orElseThrow(() -> new BizException(1602, "未开通公司，不能查看操作日志"));
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(1603, "分页或时间范围不合法");
        }
        Instant fromTs = parseTime(from);
        Instant toTs = parseTime(to);
        if (fromTs != null && toTs != null && fromTs.isAfter(toTs)) {
            throw new BizException(1603, "分页或时间范围不合法");
        }
        AuditActionType type = parseType(actionType);
        int offset = (pageNum - 1) * pageSize;
        AuditRecordRepository.Page page = repository.pageByTenant(tenantId, type, fromTs, toTs, q, offset, pageSize);
        List<AuditRecordVo> list = page.records().stream()
                .map(r -> new AuditRecordVo(
                        r.id(),
                        r.createdAt(),
                        r.actorName(),
                        r.actionType().name(),
                        r.objectLabel(),
                        r.ipMasked()))
                .toList();
        return new AuditPageVo(list, page.total(), pageNum, pageSize);
    }

    private static Instant parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            throw new BizException(1603, "分页或时间范围不合法");
        }
    }

    private static AuditActionType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AuditActionType.valueOf(raw);
        } catch (Exception ex) {
            throw new BizException(1603, "分页或时间范围不合法");
        }
    }
}
