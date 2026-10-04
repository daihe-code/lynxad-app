package com.lynx.ad.modules.audit.application;

import com.lynx.ad.common.security.RequestMeta;
import com.lynx.ad.modules.audit.domain.model.AuditRecord;
import com.lynx.ad.modules.audit.domain.port.AuditRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditRecordRepository repository;

    public AuditRecorder(AuditRecordRepository repository) {
        this.repository = repository;
    }

    public void record(SuccessAuditCommand cmd) {
        if (cmd == null || cmd.actionType() == null) {
            return;
        }
        try {
            String ip = cmd.ip() != null ? cmd.ip() : RequestMeta.ip().orElse(null);
            String ua = cmd.userAgent() != null ? cmd.userAgent() : RequestMeta.userAgent().orElse(null);
            AuditRecord record = AuditRecord.recordSuccess(
                    cmd.tenantId(),
                    cmd.userId(),
                    cmd.actorName(),
                    cmd.actionType(),
                    cmd.objectType(),
                    cmd.objectId(),
                    cmd.objectLabel(),
                    ip,
                    cmd.sessionId(),
                    ua,
                    cmd.extra());
            repository.append(record);
        } catch (Exception ex) {
            log.error("audit write failed type={}", cmd.actionType(), ex);
        }
    }
}
