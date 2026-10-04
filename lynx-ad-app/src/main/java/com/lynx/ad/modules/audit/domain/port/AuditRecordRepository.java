package com.lynx.ad.modules.audit.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.audit.domain.model.AuditRecord;
import java.time.Instant;
import java.util.List;

public interface AuditRecordRepository {

    void append(AuditRecord record);

    Page pageByTenant(
            TenantId tenantId,
            AuditActionType actionType,
            Instant from,
            Instant to,
            String q,
            int offset,
            int limit);

    record Page(List<AuditRecord> records, long total) {}
}
